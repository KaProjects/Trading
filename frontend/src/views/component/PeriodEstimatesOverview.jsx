import {
    Box,
    ButtonBase,
    Grid,
} from "@mui/material";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import React from "react";
import {ReactComponent as EstimatesIcon} from "../../assets/icons/estimates.svg";
import {formatDecimals, formatMillionsRounded, formatMultiple, formatPercent} from "../../service/FormattingService";
import {WIDGET_FIRST_COLUMN_WIDTH} from "./widgetLayout";

const windows = [
    {key: "ttm", label: "ttm"},
    {key: "current", label: "current"},
    {key: "next1", label: "next 1"},
    {key: "next2", label: "next 2"},
    {key: "next3", label: "next 3"},
];

export const formatEstimateAmount = value => formatDecimals(value, 0, 2) || "-";
export const formatRevenueEstimateAmount = value =>
    (value === null || value === undefined || value === "" ? "" : formatMillionsRounded(Number(value))) || "-";

const EstimateSummaryItem = ({window = {}, label, format, multiple, expanded = false, first = false}) => {
    const change = formatPercent(window.change, true, 1);
    // The label is dropped on a narrow screen, the multiple is not: it is a number, not a caption.
    const indicator = multiple ? formatMultiple(multiple(window.value)) : "";
    const textAlign = "center";

    return (
        <Box sx={{
            marginLeft: first ? 0 : "10px",
            minWidth: first ? WIDGET_FIRST_COLUMN_WIDTH : undefined,
            flexShrink: 0,
        }}>
            <Box sx={{color: "text.secondary", fontSize: 9, minHeight: "13px", marginBottom: "-3px", textAlign}}>
                {change ? `(${change})` : "\u00a0"}
            </Box>
            <Box sx={{fontWeight: 600, fontSize: 13, textAlign}}>
                {format(window.value)}
            </Box>
            <Box sx={{
                display: expanded ? "block" : {xs: "none", sm: "block"},
                color: "text.secondary",
                mx: 0.5,
                marginTop: "-2px",
                fontSize: 11,
                textAlign,
            }}>
                {label}
            </Box>
            {indicator &&
                <Box sx={{color: "text.secondary", fontSize: 10, textAlign}}>{indicator}</Box>
            }
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

export const PeriodEstimatesOverview = ({
    overview,
    onOpen,
    sx,
    title = "Earnings estimates",
    format = formatEstimateAmount,
    multiple,
    expanded = false,
}) => {
    if (!overview) return null;

    return (
        <Box sx={{
            ...sx,
            maxWidth: "550px",
            borderLeft: "3px solid",
            borderColor: "warning.main",
            bgcolor: "rgba(237, 108, 2, 0.04)",
            borderRadius: "0 4px 4px 0",
        }}>
            <ButtonBase
                component={onOpen ? "button" : "div"}
                aria-label={onOpen ? "Open estimates" : undefined}
                onClick={onOpen}
                disableRipple={!onOpen}
                sx={{
                    width: "100%",
                    padding: "5px 7px",
                    textAlign: "left",
                    alignItems: "flex-start",
                    gap: "7px",
                    cursor: onOpen ? "pointer" : "default",
                }}
            >
                <Box sx={{display: "flex", color: "warning.main", marginTop: "1px", flexShrink: 0}}>
                    <EstimatesIcon width="17" height="17"/>
                </Box>
                <Box sx={{flex: 1, minWidth: 0}}>
                    <Box sx={{display: expanded ? "block" : {xs: "none", sm: "block"}, marginBottom: "2px", color: "text.secondary", fontSize: 11}}>
                        <Box component="span" sx={{fontWeight: 600, color: "text.primary"}}>{title}</Box>
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
                                    format={format}
                                    multiple={multiple}
                                    expanded={expanded}
                                    first={index === 0}
                                />
                            ))}
                            <EstimateYearOverYearChange value={overview.yearOverYearChange}/>
                        </Grid>
                    </Box>
                </Box>
                {onOpen &&
                    <ChevronRightIcon sx={{fontSize: 17, color: "text.secondary", marginTop: "1px", flexShrink: 0}}/>
                }
            </ButtonBase>
        </Box>
    );
};
