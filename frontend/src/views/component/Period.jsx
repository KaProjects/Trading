import {BorderedSection} from "./BorderedSection";
import React from "react";
import {Badge, Box, Button, Stack, Typography} from "@mui/material";
import Tooltip from "@mui/material/Tooltip";
import {formatDate, formatDecimals, formatError, formatMillions, formatPercent, formatPeriodName} from "../../service/FormattingService";
import axios from "axios";
import {backend} from "../../properties";
import {ContentEditor} from "./ContentEditor";
import {ReactComponent as FinancialsPlusIcon} from "../../assets/icons/financials-plus.svg";
import {ReactComponent as EstimatesPlusIcon} from "../../assets/icons/estimates-plus.svg";
import EditNoteIcon from "@mui/icons-material/EditNote";
import TrackChangesIcon from "@mui/icons-material/TrackChanges";
import NewspaperOutlinedIcon from "@mui/icons-material/NewspaperOutlined";
import {PeriodTargetSummary} from "./PeriodTargetSummary";

export const Period = ({period, currency, setAlert, openDialog, openEditDialog, openEstimateDialog, openTargetDialog, openNewsSentimentDialog, targetCandidateCount, targetCandidateFailed}) => {

    function formatEndingMonth(endingMonth) {
        if (endingMonth === null || endingMonth === undefined) return "";
        return endingMonth.substring(5, 7) + "/" + endingMonth.substring(2, 4);
    }

    function formatPrice(price, currency) {
        if (price === null || price === undefined) return "";
        return price + currency;
    }

    function formatEstimateValue(value) {
        if (value === null || value === undefined || value === "") return "-"
        return formatDecimals(value, 0, 2) || "-"
    }

    function formatPastEstimates(estimate) {
        const past = [estimate.past4, estimate.past3, estimate.past2, estimate.past1]
            .map(formatEstimateValue)
            .join(" | ")
        return past + " => "
    }

    function formatCurrentAndFutureEstimates(estimate) {
        const future = [estimate.next1, estimate.next2, estimate.next3]
            .map(formatEstimateValue)
            .join(" | ")
        return formatEstimateValue(estimate.current) + " | " + future
    }

    function formatEstimateChanges(estimate) {
        const formatChange = (value) => formatPercent(value, true, 1) || "-";
        return [estimate.currentChange, estimate.next1Change, estimate.next2Change, estimate.next3Change]
            .map(formatChange)
            .join(" | ");
    }

    function formatEstimateDate(datetime) {
        return formatDate(datetime?.substring(0, 10)) || "-";
    }

    function formatEstimatePastTotal(value) {
        return formatDecimals(value, 0, 2) || "-";
    }

    function updateResearch(id, content) {
        return axios.put(backend + "/period", {id: id, research: JSON.stringify(content)})
            .then((response) => {})
            .catch((error) => {
                const formatted = formatError(error)
                setAlert(formatted)
                return formatted
            })
    }

    return (
        <BorderedSection
            title={formatPeriodName(period.name) + " - ending: " + formatEndingMonth(period.endingMonth) + " - report: " + formatDate(period.reportDate)}
            style={{color: 'text.primary'}}
        >
            <ContentEditor
                content={period.research}
                update={(value) => updateResearch(period.id, value)}
                style={{margin: "5px 5px 10px 5px"}}
            />

            {period.financial &&
                <>
                    <Typography sx={{color: 'text.secondary', fontSize: 14}}>
                        {"Revenue: " + formatMillions(period.financial.revenue.value)
                            + " | Gross P.: " + formatMillions(period.financial.grossProfit.value)
                            + " | Op. Inc.: " + formatMillions(period.financial.operatingIncome.value)
                            + " | Net Income: " + formatMillions(period.financial.netIncome.value)}
                    </Typography>
                    <Typography sx={{color: 'text.secondary', fontSize: 14}}>
                        {"Shares: " + formatMillions(period.shares)
                            + " | CapEx: " + formatMillions(period.financial.capex?.value)
                            + " | FCF: " + formatMillions(period.financial.freeCashFlow?.value)
                            + " | Dividend: " + formatMillions(period.financial.dividend)
                            + " | Adj. Eps: " + formatDecimals(period.financial.adjustedEps, 0, 2)}
                    </Typography>
                    <Typography sx={{color: 'text.secondary', fontSize: 14}}>
                        {"H: " + formatPrice(period.priceHigh, currency)
                            + " | L: " + formatPrice(period.priceLow, currency)}
                    </Typography>
                </>
            }
            {period.estimate &&
                <>
                    <Typography data-testid="period-estimates" sx={{color: 'text.secondary', fontSize: 14}}>
                        {"Estimates: "}
                        <Box component="span" sx={{display: {xs: "none", sm: "inline"}}}>
                            {formatPastEstimates(period.estimate)}
                        </Box>
                        {formatCurrentAndFutureEstimates(period.estimate)}
                    </Typography>
                    <Box sx={{
                        color: 'text.secondary',
                        display: "grid",
                        gridTemplateColumns: {xs: "88px max-content", sm: "88px 94px max-content"},
                        columnGap: "8px",
                        fontSize: 11,
                        marginTop: "-2px",
                    }}>
                        <Box>({formatEstimateDate(period.estimate.datetime)})</Box>
                        <Box sx={{display: {xs: "none", sm: "flex"}, justifyContent: "center"}}>({formatEstimatePastTotal(period.estimate.pastTotal)})</Box>
                        <Box sx={{marginLeft: {xs: 0, sm: "20px"}}}>({formatEstimateChanges(period.estimate)})</Box>
                    </Box>
                </>
            }
            <PeriodTargetSummary stats={period.targetStats} currency={currency}/>
            <Stack direction="column" justifyContent="flex-start" alignItems="center" spacing={1}
                   sx={{
                       position: "absolute", top: "6px", right: "8px", zIndex: 1, opacity: 0, pointerEvents: "none",
                       maxHeight: "calc(100% - 12px)", overflowY: "auto", overflowX: "hidden",
                       paddingRight: "8px", marginRight: "-8px",
                       transition: "opacity 120ms ease-in-out",
                       ".mainContainer:hover &": {opacity: 1, pointerEvents: "auto",},
                       "& .MuiButton-root": {minWidth: 0, padding: "2px", lineHeight: 0,},
                       "& svg": {width: "20px", height: "20px", display: "block",},
                   }}
            >
                {!period.financial &&
                    <Tooltip title="Add Financials" placement="left">
                        <Button onClick={openDialog}>
                            <FinancialsPlusIcon/>
                        </Button>
                    </Tooltip>
                }
                <Tooltip title="Add Estimates" placement="left">
                    <Button onClick={() => openEstimateDialog?.(period)}>
                        <EstimatesPlusIcon/>
                    </Button>
                </Tooltip>
                <Tooltip
                    title={targetCandidateFailed
                        ? "Manage Targets (availability could not be checked)"
                        : targetCandidateCount > 0
                        ? `Manage Targets (${targetCandidateCount} available to import)`
                        : "Manage Targets"}
                    placement="left"
                >
                    <Button aria-label="Manage Targets" onClick={() => openTargetDialog?.(period)}>
                        <Badge
                            badgeContent={targetCandidateFailed ? "!" : targetCandidateCount}
                            color={targetCandidateFailed ? "error" : "success"}
                            sx={{
                                "& .MuiBadge-badge": {
                                    minWidth: "12px",
                                    height: "12px",
                                    padding: 0,
                                    fontSize: "0.55rem",
                                    lineHeight: 1,
                                },
                            }}
                        >
                            <TrackChangesIcon/>
                        </Badge>
                    </Button>
                </Tooltip>
                <Tooltip title="View News Sentiment" placement="left">
                    <Button aria-label="View News Sentiment" onClick={() => openNewsSentimentDialog?.(period)}>
                        <NewspaperOutlinedIcon sx={{color: "info.main"}}/>
                    </Button>
                </Tooltip>
                {period.financial &&
                    <Tooltip title="Edit Period" placement="left">
                        <Button onClick={() => openEditDialog(period)}>
                            <EditNoteIcon/>
                        </Button>
                    </Tooltip>
                }
            </Stack>
        </BorderedSection>
    )
}
