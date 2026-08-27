import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Alert,
    AlertTitle,
    Box,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Typography,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import axios from "axios";
import React, {useEffect, useState} from "react";
import {backend} from "../properties";
import {formatDate, formatError, formatPeriodName} from "../service/FormattingService";
import {SentimentBreakdown} from "../views/component/SentimentBreakdown";

export const NewsSentimentDialog = ({open, handleClose, company, period}) => {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const periodId = period?.id;

    useEffect(() => {
        if (!open || !periodId) return undefined;

        let active = true;
        setData(null);
        setError(null);
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
                News Sentiment for {company?.ticker} {period ? formatPeriodName(period.name) : ""}
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
                        No news sentiment records are available for this period.
                    </Typography>
                }
                {records.map(record => (
                    <Accordion key={record.id} disableGutters elevation={0} sx={{borderBottom: "1px solid", borderColor: "divider"}}>
                        <AccordionSummary
                            expandIcon={<ExpandMoreIcon/>}
                            sx={{"& .MuiAccordionSummary-content": {marginBottom: "5px"}}}
                        >
                            <Box sx={{width: "100%", paddingRight: 1}}>
                                <Box sx={{display: "flex", justifyContent: "space-between", gap: 1, marginBottom: "3px"}}>
                                    <Typography sx={{fontSize: 13, fontWeight: 600}}>{formatDate(record.date)}</Typography>
                                    <Typography sx={{fontSize: 11, color: "text.secondary"}}>
                                        {record.total} {record.total === 1 ? "article" : "articles"}
                                    </Typography>
                                </Box>
                                <SentimentBreakdown stats={record.stats} total={record.total}/>
                            </Box>
                        </AccordionSummary>
                        <AccordionDetails sx={{paddingTop: "5px"}}>
                            {record.keyTakeaways.length > 0
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
                                    {record.keyTakeaways.map(takeaway => <li key={takeaway}>{takeaway}</li>)}
                                </Box>
                                : <Typography sx={{color: "text.disabled", fontSize: 12}}>No key takeaways.</Typography>
                            }
                        </AccordionDetails>
                    </Accordion>
                ))}
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose}>Close</Button>
            </DialogActions>
        </Dialog>
    );
};
