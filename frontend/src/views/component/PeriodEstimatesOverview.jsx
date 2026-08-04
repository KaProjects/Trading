import {
    Box,
    Button,
    Grid,
    Paper,
} from "@mui/material";
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
        <Box sx={{marginLeft: first ? {xs: 0, sm: "10px"} : "10px", flexShrink: 0}}>
            <Box sx={{fontSize: 9, minHeight: "13px", marginBottom: "-3px", textAlign: "center"}}>
                {change ? `(${change})` : "\u00a0"}
            </Box>
            <Box sx={{fontWeight: "bold", fontSize: 13, textAlign: "center"}}>
                {formatDecimals(window.value, 0, 2) || "-"}
            </Box>
            <Box sx={{color: "lightgrey", fontWeight: "bold", mx: 0.5, marginTop: "-2px", fontSize: 12, textAlign: "center"}}>
                {label}
            </Box>
        </Box>
    );
};

export const PeriodEstimatesOverview = ({overview, onOpen, sx}) => (
    <Paper elevation={0} sx={{...sx, maxWidth: "100%", overflowX: "auto", overflowY: "hidden"}}>
        <Grid
            container
            wrap="nowrap"
            direction="row"
            justifyContent="flex-start"
            alignItems="stretch"
            sx={{width: "max-content", minWidth: "100%", columnGap: "3px"}}
        >
            {overview &&
                <>
                    {windows.map((window, index) => (
                        <EstimateSummaryItem
                            key={window.key}
                            window={overview[window.key]}
                            label={window.label}
                            first={index === 0}
                        />
                    ))}
                    <Button
                        aria-label="Open estimates"
                        onClick={onOpen}
                        sx={{minWidth: 0, height: "25px", marginLeft: "3px", padding: "2px", color: "primary.main", transform: "translateY(5px)", flexShrink: 0}}
                    >
                        <EstimatesIcon width="20" height="20"/>
                    </Button>
                </>
            }
        </Grid>
    </Paper>
);
