import {
    Box,
    ButtonBase,
    CircularProgress,
    Collapse,
} from "@mui/material";
import ExpandLessIcon from "@mui/icons-material/ExpandLess";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import NewspaperOutlinedIcon from "@mui/icons-material/NewspaperOutlined";
import React, {useEffect, useState} from "react";
import {formatDate} from "../../service/FormattingService";
import {useData} from "../../service/BackendService";
import {SentimentBreakdown} from "./SentimentBreakdown";

export const LatestNewsSentiment = ({companyId, sx}) => {
    const [expanded, setExpanded] = useState(false);
    const {data, loaded, error} = useData(`/news-sentiment/company/${companyId}/latest`);
    const record = data?.record;
    const canExpand = (record?.keyTakeaways?.length ?? 0) > 0;

    useEffect(() => setExpanded(false), [companyId, record?.id]);

    if (!loaded) {
        if (error) return null;
        return (
            <Box
                data-testid="latest-news-sentiment-loading"
                sx={{...sx, display: "inline-flex", alignItems: "center", gap: "6px", color: "text.disabled", fontSize: 11}}
            >
                <CircularProgress size={12}/>
                Loading news sentiment
            </Box>
        );
    }

    if (!record) return null;

    return (
        <Box
            data-testid="latest-news-sentiment"
            sx={{
                ...sx,
                maxWidth: "520px",
                borderLeft: "3px solid",
                borderColor: "info.main",
                bgcolor: "rgba(25, 118, 210, 0.04)",
                borderRadius: "0 4px 4px 0",
            }}
        >
            <ButtonBase
                aria-expanded={canExpand ? expanded : undefined}
                aria-label={canExpand ? "Toggle latest news sentiment takeaways" : "Latest news sentiment"}
                disabled={!canExpand}
                onClick={() => setExpanded(value => !value)}
                sx={{width: "100%", padding: "5px 7px", textAlign: "left", alignItems: "flex-start", gap: "7px"}}
            >
                <NewspaperOutlinedIcon sx={{fontSize: 17, color: "info.main", marginTop: "1px", flexShrink: 0}}/>
                <Box sx={{flex: 1, minWidth: 0}}>
                    <Box sx={{display: "flex", alignItems: "center", gap: "6px", color: "text.secondary", fontSize: 11}}>
                        <Box component="span" sx={{fontWeight: 600, color: "text.primary"}}>Latest news</Box>
                        <Box component="span">{formatDate(record.date)}</Box>
                        <Box component="span">{record.total} {record.total === 1 ? "article" : "articles"}</Box>
                    </Box>
                    <SentimentBreakdown stats={record.stats} total={record.total}/>
                </Box>
                {canExpand && (expanded
                    ? <ExpandLessIcon sx={{fontSize: 17, color: "text.secondary"}}/>
                    : <ExpandMoreIcon sx={{fontSize: 17, color: "text.secondary"}}/>)
                }
            </ButtonBase>
            <Collapse in={expanded} unmountOnExit>
                <Box
                    component="ul"
                    sx={{
                        margin: "0 10px 7px 34px",
                        paddingLeft: "14px",
                        paddingTop: "5px",
                        display: "grid",
                        rowGap: "3px",
                        color: "text.secondary",
                        fontSize: 12,
                        lineHeight: 1.4,
                    }}
                >
                    {record.keyTakeaways.map(takeaway => <li key={takeaway}>{takeaway}</li>)}
                </Box>
            </Collapse>
        </Box>
    );
};
