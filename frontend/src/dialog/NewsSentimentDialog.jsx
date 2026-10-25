import {
    Alert,
    AlertTitle,
    Box,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    IconButton,
    Typography,
} from "@mui/material";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import axios from "axios";
import React, {useEffect, useState} from "react";
import {backend} from "../properties";
import {formatDate, formatError, formatPeriodName} from "../service/FormattingService";
import {SentimentBreakdown} from "../views/component/SentimentBreakdown";

function Points({title, color, points, analysed, missingLabel, testId}) {
    return (
        <Box data-testid={testId}>
            <Typography sx={{fontSize: 12, fontWeight: 600, color, marginTop: "10px"}}>{title}</Typography>
            {(points?.length ?? 0) === 0
                ? <Typography sx={{color: "text.disabled", fontSize: 12}}>
                    {analysed ? missingLabel : "Not part of this weekly analysis."}
                </Typography>
                : points.map(entry => (
                    <Box key={entry.point} sx={{marginTop: "4px"}}>
                        <Typography sx={{fontSize: 13}}>{entry.point}</Typography>
                        <Typography sx={{color: "text.secondary", fontSize: 12, lineHeight: 1.4}}>
                            {entry.reasoning}
                        </Typography>
                    </Box>
                ))
            }
        </Box>
    );
}

export const NewsSentimentDialog = ({open, handleClose, company, period}) => {
    const [data, setData] = useState(null);
    const [index, setIndex] = useState(0);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const periodId = period?.id;

    useEffect(() => {
        if (!open || !periodId) return undefined;

        let active = true;
        setData(null);
        setError(null);
        setIndex(0);
        setLoading(true);
        axios.get(`${backend}/news-sentiment/period/${periodId}`)
            .then(response => {
                if (!active) return;
                setData(response.data);
            })
            .catch(requestError => {
                if (!active) return;
                setError(formatError(requestError));
            })
            .finally(() => {
                if (active) setLoading(false);
            });

        return () => {
            active = false;
        };
    }, [open, periodId]);

    const records = data?.records ?? [];
    const warnings = data?.warnings ?? [];

    return (
        <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
            <DialogTitle>
                Analysis of {company?.ticker} {period ? formatPeriodName(period.name) : ""}
            </DialogTitle>
            <DialogContent sx={{minHeight: "120px"}}>
                {loading &&
                    <Box sx={{display: "flex", justifyContent: "center", padding: "24px"}}>
                        <CircularProgress aria-label="Loading news sentiment"/>
                    </Box>
                }
                {error &&
                    <Alert severity="error" variant="filled">
                        <AlertTitle>{error.title}</AlertTitle>
                        {error.message}
                    </Alert>
                }
                {warnings.length > 0 &&
                    <Alert severity="warning" sx={{marginBottom: 1.5}}>
                        <AlertTitle>Some expected news sentiment data could not be loaded</AlertTitle>
                        {warnings.map(warning => <Box key={warning}>{warning}</Box>)}
                    </Alert>
                }
                {!loading && !error && records.length === 0 &&
                    <Typography sx={{color: "text.secondary", fontSize: 13}}>
                        No analysis is available for this period.
                    </Typography>
                }
                {records.length > 0 &&
                    <Box sx={{display: "grid"}}>
                        {records.map((entry, entryIndex) => (
                            <Box
                                key={entry.id}
                                data-testid={entryIndex === index ? "sentiment-analysis" : undefined}
                                aria-hidden={entryIndex !== index}
                                sx={{
                                    gridArea: "1 / 1",
                                    visibility: entryIndex === index ? "visible" : "hidden",
                                }}
                            >
                                <Box sx={{display: "flex", justifyContent: "space-between", gap: 1, marginBottom: "3px"}}>
                                    <Typography sx={{fontSize: 13, fontWeight: 600}}>{formatDate(entry.date)}</Typography>
                                    <Typography sx={{fontSize: 11, color: "text.secondary"}}>
                                        {entry.total} {entry.total === 1 ? "article" : "articles"}
                                    </Typography>
                                </Box>
                                <SentimentBreakdown stats={entry.stats} total={entry.total}/>

                                <Typography sx={{fontSize: 12, fontWeight: 600, marginTop: "12px"}}>News</Typography>
                                {entry.keyTakeaways.length > 0
                                    ? <Box
                                        component="ul"
                                        sx={{
                                            margin: 0,
                                            paddingLeft: "20px",
                                            display: "grid",
                                            rowGap: "4px",
                                            color: "text.secondary",
                                            fontSize: 13,
                                            lineHeight: 1.4,
                                        }}
                                    >
                                        {entry.keyTakeaways.map(takeaway => <li key={takeaway}>{takeaway}</li>)}
                                    </Box>
                                    : <Typography sx={{color: "text.disabled", fontSize: 12}}>
                                        No news was analysed this week.
                                    </Typography>
                                }

                                <Divider sx={{marginTop: "12px"}}/>
                                <Points
                                    title="Bull case"
                                    color="success.main"
                                    points={entry.bull}
                                    analysed={entry.bullBearAnalysed}
                                    missingLabel="The analysis found no bull points."
                                    testId={entryIndex === index ? "sentiment-bull-case" : undefined}
                                />
                                <Points
                                    title="Bear case"
                                    color="error.main"
                                    points={entry.bear}
                                    analysed={entry.bullBearAnalysed}
                                    missingLabel="The analysis found no bear points."
                                    testId={entryIndex === index ? "sentiment-bear-case" : undefined}
                                />
                            </Box>
                        ))}
                    </Box>
                }
            </DialogContent>
            <DialogActions sx={{justifyContent: "space-between"}}>
                {records.length > 1
                    ? <Box sx={{display: "flex", alignItems: "center", gap: "4px"}}>
                        <IconButton
                            aria-label="Newer analysis"
                            size="small"
                            disabled={index === 0}
                            onClick={() => setIndex(value => value - 1)}
                        >
                            <ChevronLeftIcon fontSize="small"/>
                        </IconButton>
                        <Typography data-testid="sentiment-position" sx={{fontSize: 12, color: "text.secondary"}}>
                            {index + 1} / {records.length}
                        </Typography>
                        <IconButton
                            aria-label="Older analysis"
                            size="small"
                            disabled={index >= records.length - 1}
                            onClick={() => setIndex(value => value + 1)}
                        >
                            <ChevronRightIcon fontSize="small"/>
                        </IconButton>
                    </Box>
                    : <Box/>
                }
                <Button onClick={handleClose}>Close</Button>
            </DialogActions>
        </Dialog>
    );
};
