import {
    Box,
    ButtonBase,
    CircularProgress,
} from "@mui/material";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import NewspaperOutlinedIcon from "@mui/icons-material/NewspaperOutlined";
import React from "react";
import {formatDate} from "../../service/FormattingService";
import {useData} from "../../service/BackendService";
import {SentimentBreakdown} from "./SentimentBreakdown";

export const LatestNewsSentiment = ({companyId, onOpen, sx}) => {
    const {data, loaded, error} = useData(`/news-sentiment/company/${companyId}/latest`);
    const record = data?.record;

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
                maxWidth: "550px",
                borderLeft: "3px solid",
                borderColor: "info.main",
                bgcolor: "rgba(25, 118, 210, 0.04)",
                borderRadius: "0 4px 4px 0",
            }}
        >
            <ButtonBase
                aria-label="Latest news sentiment"
                onClick={onOpen}
                sx={{width: "100%", padding: "5px 7px", textAlign: "left", alignItems: "flex-start", gap: "7px"}}
            >
                <NewspaperOutlinedIcon sx={{fontSize: 17, color: "info.main", marginTop: "1px", flexShrink: 0}}/>
                <Box sx={{flex: 1, minWidth: 0}}>
                    <Box sx={{display: "flex", alignItems: "center", gap: "6px", color: "text.secondary", fontSize: 11}}>
                        <Box component="span" sx={{fontWeight: 600, color: "text.primary"}}>Analysis</Box>
                        <Box component="span">{formatDate(record.date)}</Box>
                        <Box component="span">{record.total} {record.total === 1 ? "article" : "articles"}</Box>
                    </Box>
                    <SentimentBreakdown stats={record.stats} total={record.total}/>
                </Box>
                <ChevronRightIcon sx={{fontSize: 17, color: "text.secondary", flexShrink: 0}}/>
            </ButtonBase>
        </Box>
    );
};
