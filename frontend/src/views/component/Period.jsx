import {BorderedSection} from "./BorderedSection";
import React, {useCallback, useEffect, useRef, useState} from "react";
import {PeriodFinancialSummary} from "./PeriodFinancialSummary";
import {Badge, Box, Button, Stack, Typography} from "@mui/material";
import Tooltip from "@mui/material/Tooltip";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import {formatDate, formatDecimals, formatError, formatMillions, formatMillionsRounded, formatPercent, formatPeriodName, formatTargetStats} from "../../service/FormattingService";
import {nextPeriod} from "../../service/PeriodService";
import axios from "axios";
import {backend} from "../../properties";
import {ContentEditor} from "./ContentEditor";
import {ContentEditorDialog} from "../../dialog/ContentEditorDialog";
import {ReactComponent as FinancialsPlusIcon} from "../../assets/icons/financials-plus.svg";
import {ReactComponent as EpsEstimatesPlusIcon} from "../../assets/icons/estimates-plus-eps.svg";
import {ReactComponent as RevenueEstimatesPlusIcon} from "../../assets/icons/estimates-plus-revenue.svg";
import EditNoteIcon from "@mui/icons-material/EditNote";
import NewspaperOutlinedIcon from "@mui/icons-material/NewspaperOutlined";
import TrackChangesIcon from "@mui/icons-material/TrackChanges";
import {PeriodTargetSummary} from "./PeriodTargetSummary";

export function hasUnexpectedEndingMonth(period, previousPeriod) {
    const following = nextPeriod(previousPeriod)
    if (!following?.endingMonth) return false

    const name = formatPeriodName(period?.name)
        || (typeof period?.name === "string" ? period.name : "")
    if (name !== following.name) return false

    return typeof period?.endingMonth === "string" && period.endingMonth !== following.endingMonth
}

export const Period = ({period, ticker, previousPeriod, isLatest = true, periodPosition, periodCount, currency, setAlert, openDialog, openEditDialog, openEstimateDialog, openRevenueEstimateDialog, openTargetDialog, openNewsSentimentDialog, targetCandidateCount, targetCandidateFailed, stretch}) => {

    const [contentEditSignal, setContentEditSignal] = useState(0)
    const [openContentEditor, setOpenContentEditor] = useState(false)
    const contentScrollRef = useRef(null)
    const [moreContentBelow, setMoreContentBelow] = useState(false)

    /** Whether the content runs past the bottom of its box, so the reader knows to scroll. */
    const updateScrollHint = useCallback(() => {
        const box = contentScrollRef.current
        if (!box) return
        setMoreContentBelow(box.scrollTop + box.clientHeight < box.scrollHeight - 1)
    }, [])

    const [research, setResearch] = useState(period.research)
    const [researchVersion, setResearchVersion] = useState(0)

    useEffect(() => {
        const box = contentScrollRef.current
        if (!stretch || !box) {
            setMoreContentBelow(false)
            return
        }

        updateScrollHint()
        // The content arrives after the first render and the box is resized by the keyboard and
        // by swiping between periods, so the measurement cannot be taken only once. Where there
        // is no ResizeObserver - jsdom, for one - the scroll handler alone keeps it honest.
        if (typeof ResizeObserver === "undefined") return

        const observer = new ResizeObserver(updateScrollHint)
        observer.observe(box)
        if (box.firstElementChild) observer.observe(box.firstElementChild)
        return () => observer.disconnect()
    }, [stretch, research, researchVersion, updateScrollHint])

    const reportLabel = period.financial ? "reported: " : "report: "
    const reportValue = period.financial
        ? formatDate(period.reportDate)
        : (period.expectedReportDate ? formatDate(period.expectedReportDate) : "?")
    const reportPending = !period.financial && (period.reportedInFirebase || !isLatest)
    const reportDateUnknown = reportValue === "?"
    const endingMonthMismatch = hasUnexpectedEndingMonth(period, previousPeriod)

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

    function formatRevenueEstimateValue(value) {
        if (value === null || value === undefined || value === "") return "-"
        return formatMillionsRounded(Number(value)) || "-"
    }

    function formatPastEstimates(estimate, format = formatEstimateValue) {
        const past = [estimate.past4, estimate.past3, estimate.past2, estimate.past1]
            .map(format)
            .join(" | ")
        return past + " => "
    }

    function formatCurrentAndFutureEstimates(estimate, format = formatEstimateValue) {
        const future = [estimate.next1, estimate.next2, estimate.next3]
            .map(format)
            .join(" | ")
        return format(estimate.current) + " | " + future
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

    function formatRevenueEstimatePastTotal(value) {
        return formatRevenueEstimateValue(value);
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

    async function saveResearch(content) {
        const error = await updateResearch(period.id, content)
        if (error) return error

        setResearch(JSON.stringify(content))
        setResearchVersion(version => version + 1)
        return null
    }

    const estimatesShown = !stretch && Boolean(period.estimate)
    const revenueEstimatesShown = !stretch && Boolean(period.revenueEstimate)
    const targetSummaryShown = !(stretch && period.financial)
        && Boolean(formatTargetStats(period.targetStats))
    const targetsShown = targetSummaryShown
        && targetCandidateCount === 0
        && !targetCandidateFailed
    // Whatever of the estimates and the targets a period has sits in one block under the research.
    const footerShown = estimatesShown || revenueEstimatesShown || targetSummaryShown

    return (
        <BorderedSection
            title={
                <>
                    {formatPeriodName(period.name) + " - "}
                    {endingMonthMismatch
                        ? <Box component="span" sx={{color: "error.dark"}}>{"ending: "}</Box>
                        : "ending: "}
                    {endingMonthMismatch
                        ? <Box
                            component="span"
                            data-testid="period-ending-month-warning"
                            sx={{color: "error.dark", textDecoration: "underline"}}
                        >
                            {formatEndingMonth(period.endingMonth)}
                        </Box>
                        : formatEndingMonth(period.endingMonth)}
                    {" - "}
                    {reportPending
                        ? <Box component="span" sx={{color: "error.dark"}}>{reportLabel}</Box>
                        : reportLabel}
                    {reportPending
                        ? (!reportDateUnknown &&
                            <Box component="span" sx={{color: "error.dark", textDecoration: "underline"}}>
                                {reportValue}
                            </Box>)
                        : reportValue}
                    {reportPending &&
                        <Box component="span" sx={{fontWeight: 900, color: "error.dark"}}>
                            {reportDateUnknown ? "!" : " !"}
                        </Box>
                    }
                </>
            }
            titleEnd={periodPosition && periodCount ? `${periodPosition} / ${periodCount}` : undefined}
            style={{color: 'text.primary'}}
            stretch={stretch}
            highlightTitle={stretch}
        >
            {period.financial &&
                <PeriodFinancialSummary
                    financial={period.financial}
                    shares={period.shares}
                    priceHigh={period.priceHigh}
                    priceLow={period.priceLow}
                    currency={currency}
                    compact={stretch}
                />
            }

            <Box sx={{
                // A list's default 40px indent pushes the research well clear of the card edge.
                // Enough is kept for the bullet itself.
                "& ul, & ol": {paddingLeft: "18px"},
                ...(stretch
                    ? {position: "relative", flex: "1 1 auto", minHeight: 0, display: "flex"}
                    : {}),
            }}>
            <Box
                ref={stretch ? contentScrollRef : undefined}
                data-period-scroll={stretch ? "true" : undefined}
                onScroll={stretch ? updateScrollHint : undefined}
                onClick={stretch ? () => setOpenContentEditor(true) : undefined}
                sx={stretch
                    ? {
                        flex: "1 1 auto",
                        minHeight: 0,
                        overflow: "auto",
                        cursor: "pointer",
                        // The editor has to fill this box, otherwise its centred placeholder
                        // centres itself in a one-line-tall element at the top.
                        display: "flex",
                        flexDirection: "column",
                    }
                    : undefined}
            >
                <ContentEditor
                    key={researchVersion}
                    content={research}
                    update={(value) => updateResearch(period.id, value)}
                    style={stretch
                        ? {margin: "5px 5px 10px 0", flex: "1 1 auto", minHeight: 0}
                        : {margin: "5px 5px 10px 0"}}
                    locked={stretch}
                    centerPlaceholder={stretch}
                    editTrigger={stretch ? undefined : contentEditSignal}
                />
            </Box>
            {stretch && moreContentBelow &&
                <KeyboardArrowDownIcon
                    data-testid="period-content-more"
                    sx={{
                        position: "absolute",
                        right: "2px",
                        bottom: "2px",
                        fontSize: 18,
                        color: "text.disabled",
                        pointerEvents: "none",
                    }}
                />
            }
            </Box>
            {footerShown &&
                <Box
                    data-testid="period-footer"
                    sx={{
                        // The rule the reported financials draw under themselves, drawn again here:
                        // it closes the research off from the figures that follow it.
                        marginTop: "4px",
                        paddingTop: "4px",
                        borderTop: "1px solid",
                        borderColor: "divider",
                    }}
                >
                {!stretch && period.estimate &&
                    <Box
                        data-testid="period-estimates-view"
                        aria-label="Add EPS Estimates"
                        role="button"
                        tabIndex={0}
                        onClick={() => openEstimateDialog?.(period)}
                        sx={{cursor: "pointer"}}
                    >
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
                    </Box>
                }
                {!stretch && period.revenueEstimate &&
                    <Box
                        data-testid="period-revenue-estimates-view"
                        aria-label="Add Revenue Estimates"
                        role="button"
                        tabIndex={0}
                        onClick={() => openRevenueEstimateDialog?.(period)}
                        sx={{cursor: "pointer"}}
                    >
                        <Typography data-testid="period-revenue-estimates" sx={{color: 'text.secondary', fontSize: 14}}>
                            {"Revenues: "}
                            <Box component="span" sx={{display: {xs: "none", sm: "inline"}}}>
                                {formatPastEstimates(period.revenueEstimate, formatRevenueEstimateValue)}
                            </Box>
                            {formatCurrentAndFutureEstimates(period.revenueEstimate, formatRevenueEstimateValue)}
                        </Typography>
                        <Box sx={{
                            color: 'text.secondary',
                            display: "grid",
                            gridTemplateColumns: {xs: "88px max-content", sm: "88px 94px max-content"},
                            columnGap: "8px",
                            fontSize: 11,
                            marginTop: "-2px",
                        }}>
                            <Box>({formatEstimateDate(period.revenueEstimate.datetime)})</Box>
                            <Box sx={{display: {xs: "none", sm: "flex"}, justifyContent: "center"}}>({formatRevenueEstimatePastTotal(period.revenueEstimate.pastTotal)})</Box>
                            <Box sx={{marginLeft: {xs: 0, sm: "20px"}}}>({formatEstimateChanges(period.revenueEstimate)})</Box>
                        </Box>
                    </Box>
                }
                {targetSummaryShown &&
                    <PeriodTargetSummary
                        stats={period.targetStats}
                        currency={currency}
                        onOpen={() => openTargetDialog?.(period)}
                    />
                }
                </Box>
            }
            <ContentEditorDialog
                open={openContentEditor}
                content={research}
                title={[ticker, formatPeriodName(period.name)].filter(Boolean).join(" ")}
                handleClose={() => setOpenContentEditor(false)}
                save={saveResearch}
            />
            <Stack direction="column" justifyContent="flex-start" alignItems="center" spacing={1}
                   sx={{
                       position: "absolute", top: "6px", right: "8px", zIndex: 1,
                       opacity: {xs: 1, sm: 0}, pointerEvents: {xs: "auto", sm: "none"},
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
                {!estimatesShown &&
                    <Tooltip title="Add EPS Estimates" placement="left">
                        <Button aria-label="Add EPS Estimates" onClick={() => openEstimateDialog?.(period)}>
                            <EpsEstimatesPlusIcon/>
                        </Button>
                    </Tooltip>
                }
                {!revenueEstimatesShown &&
                    <Tooltip title="Add Revenue Estimates" placement="left">
                        <Button aria-label="Add Revenue Estimates" onClick={() => openRevenueEstimateDialog?.(period)}>
                            <RevenueEstimatesPlusIcon/>
                        </Button>
                    </Tooltip>
                }
                {!targetsShown &&
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
                }
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
