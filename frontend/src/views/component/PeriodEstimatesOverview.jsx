import {
    Box,
    ButtonBase,
    Grid,
} from "@mui/material";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import React from "react";
import {ReactComponent as EstimatesIcon} from "../../assets/icons/estimates.svg";
import {formatDecimals, formatPercent} from "../../service/FormattingService";

const windows = [
    {key: "ttm", label: "ttm"},
    {key: "current", label: "current"},
    {key: "next1", label: "next 1"},
    {key: "next2", label: "next 2"},
    {key: "next3", label: "next 3"},
];

const EstimateSummaryItem = ({window = {}, label, first = false}) => {
    const change = formatPercent(window.change, true, 1);
    return (
        <Box sx={{marginLeft: first ? 0 : "10px", flexShrink: 0}}>
            <Box sx={{color: "text.secondary", fontSize: 9, minHeight: "13px", marginBottom: "-3px", textAlign: "center"}}>
                {change ? `(${change})` : "\u00a0"}
            </Box>
            <Box sx={{fontWeight: 600, fontSize: 13, textAlign: "center"}}>
                {formatDecimals(window.value, 0, 2) || "-"}
            </Box>
            <Box sx={{color: "text.secondary", mx: 0.5, marginTop: "-2px", fontSize: 11, textAlign: "center"}}>
                {label}
            </Box>
        </Box>
    );
};

const EstimateYearOverYearChange = ({value}) => {
    const change = formatPercent(value, true, 1);
    return (
        <Box sx={{width: "44px", flexShrink: 0}}>
            <Box sx={{color: "text.secondary", fontSize: 9, minHeight: "13px", marginBottom: "-3px", textAlign: "center", whiteSpace: "nowrap"}}>
                {change ? `(${change})` : "\u00a0"}
            </Box>
        </Box>
    );
};

export const PeriodEstimatesOverview = ({overview, onOpen, sx}) => {
    if (!overview) return null;

    return (
        <Box sx={{
            ...sx,
            maxWidth: "520px",
            borderLeft: "3px solid",
            borderColor: "warning.main",
            bgcolor: "rgba(237, 108, 2, 0.04)",
            borderRadius: "0 4px 4px 0",
        }}>
            <ButtonBase
                aria-label="Open estimates"
                onClick={onOpen}
                sx={{width: "100%", padding: "5px 7px", textAlign: "left", alignItems: "flex-start", gap: "7px"}}
            >
                <Box sx={{display: "flex", color: "warning.main", marginTop: "1px", flexShrink: 0}}>
                    <EstimatesIcon width="17" height="17"/>
                </Box>
                <Box sx={{flex: 1, minWidth: 0}}>
                    <Box sx={{marginBottom: "2px", color: "text.secondary", fontSize: 11}}>
                        <Box component="span" sx={{fontWeight: 600, color: "text.primary"}}>Earnings estimates</Box>
                    </Box>
                    <Box sx={{maxWidth: "100%", overflowX: {xs: "auto", sm: "visible"}, overflowY: "hidden"}}>
                        <Grid
                            container
                            wrap="nowrap"
                            direction="row"
                            justifyContent="flex-start"
                            alignItems="stretch"
                            sx={{width: "max-content", minWidth: "100%", columnGap: "3px"}}
                        >
                            {windows.map((window, index) => (
                                <EstimateSummaryItem
                                    key={window.key}
                                    window={overview[window.key]}
                                    label={window.label}
                                    first={index === 0}
                                />
                            ))}
                            <EstimateYearOverYearChange value={overview.yearOverYearChange}/>
                        </Grid>
                    </Box>
                </Box>
                <ChevronRightIcon sx={{fontSize: 17, color: "text.secondary", marginTop: "1px", flexShrink: 0}}/>
            </ButtonBase>
        </Box>
    );
};
