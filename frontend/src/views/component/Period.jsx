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
import {ContentDatePicker, PeriodContentList, defaultEntryDate} from "./PeriodContentList";
import {PeriodContentImages} from "./PeriodContentImages";
import {ContentEditorDialog} from "../../dialog/ContentEditorDialog";
import {ReactComponent as FinancialsPlusIcon} from "../../assets/icons/financials-plus.svg";
import {ReactComponent as EpsEstimatesPlusIcon} from "../../assets/icons/estimates-plus-eps.svg";
import {ReactComponent as RevenueEstimatesPlusIcon} from "../../assets/icons/estimates-plus-revenue.svg";
import EditNoteIcon from "@mui/icons-material/EditNote";
import PostAddIcon from "@mui/icons-material/PostAdd";
import NewspaperOutlinedIcon from "@mui/icons-material/NewspaperOutlined";
import TrackChangesIcon from "@mui/icons-material/TrackChanges";
import {PeriodTargetSummary} from "./PeriodTargetSummary";
import {NUMERIC_FONT} from "./widgetLayout";
import {tightPlus} from "./tightPlus";

/** No pictures added, none crossed out. */
const EMPTY_STAGED = {adds: [], removes: []}

export function hasUnexpectedEndingMonth(period, previousPeriod) {
    const following = nextPeriod(previousPeriod)
    if (!following?.endingMonth) return false

    const name = formatPeriodName(period?.name)
        || (typeof period?.name === "string" ? period.name : "")
    if (name !== following.name) return false

    return typeof period?.endingMonth === "string" && period.endingMonth !== following.endingMonth
}

export const Period = ({period, ticker, previousPeriod, isLatest = true, periodPosition, periodCount, currency, setAlert, openDialog, openEditDialog, openEstimateDialog, openRevenueEstimateDialog, openTargetDialog, openNewsSentimentDialog, targetCandidateCount, targetCandidateFailed, stretch}) => {

    const [openContentEditor, setOpenContentEditor] = useState(false)
    const contentScrollRef = useRef(null)
    const [moreContentBelow, setMoreContentBelow] = useState(false)

    /** Whether the content runs past the bottom of its box, so the reader knows to scroll. */
    const updateScrollHint = useCallback(() => {
        const box = contentScrollRef.current
        if (!box) return
        setMoreContentBelow(box.scrollTop + box.clientHeight < box.scrollHeight - 1)
    }, [])

    const [contents, setContents] = useState(period.contents ?? [])
    // Pictures added or crossed out while an entry is being written. They wait here until the
    // entry is saved, and are dropped with the text when the writing is cancelled.
    const [staged, setStaged] = useState({})
    const [editedEntry, setEditedEntry] = useState(null)
    const [addingContent, setAddingContent] = useState(false)
    const [newContentDate, setNewContentDate] = useState("")

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
    }, [stretch, contents, updateScrollHint])

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

    /**
     * Figures in the text colour, everything that is not a figure in the label's grey: the bars
     * between the windows carry no information, and at the same weight as the numbers they read
     * like part of them.
     */
    function estimateFigures(values, format, separatorStyle = {color: "text.secondary"}) {
        return values.map((value, index) => (
            <React.Fragment key={index}>
                {index > 0 && <Box component="span" sx={separatorStyle}>{" | "}</Box>}
                {format(value)}
            </React.Fragment>
        ))
    }

    function formatPastEstimates(estimate, format = formatEstimateValue) {
        return estimateFigures([estimate.past4, estimate.past3, estimate.past2, estimate.past1], format)
    }

    function formatCurrentAndFutureEstimates(estimate, format = formatEstimateValue) {
        return estimateFigures(
            [estimate.current, estimate.next1, estimate.next2, estimate.next3],
            format)
    }

    function formatEstimateChanges(estimate) {
        const formatChange = (value) => tightPlus(formatPercent(value, true, 1) || "-");
        // The whole row is already the one quiet grey, so the bars take its colour rather than
        // the darker one they are given between the figures above.
        return estimateFigures(
            [estimate.currentChange, estimate.next1Change, estimate.next2Change, estimate.next3Change],
            formatChange,
            {})
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

    /**
     * Research is saved against its date, which is what identifies it: writing a date the period
     * already carries fills that entry in rather than adding another.
     */
    function saveContent(date, content) {
        return axios.put(`${backend}/period/content/${period.id}`,
                {date: date, content: JSON.stringify(content)})
            .then(response => {
                setContents(previous => {
                    // The version makes the editor start again on what was saved - writing in
                    // the dialog would otherwise leave the page showing the text it opened with.
                    const written = response.data
                    const before = previous.find(entry => entry.date === written.date)
                    const saved = {...written, version: (before?.version ?? 0) + 1}
                    // An entry emptied out stays on the page for the rest of the visit: having
                    // just cleared it, one is as likely to write it again as to leave it. The
                    // server leaves it out of the next load, so it is gone on the next visit.
                    const others = previous.filter(entry => entry.date !== saved.date)
                    return [...others, saved]
                        .sort((first, second) => first.date.localeCompare(second.date))
                })
            })
            .catch(error => {
                const formatted = formatError(error)
                setAlert(formatted)
                return formatted
            })
    }

    /**
     * A new day is written where days are written: in its own dialog on a narrow screen, and at
     * the end of the list on a wide one. Either way it opens on a date inside the period.
     */
    function startAddingContent() {
        setNewContentDate(defaultEntryDate(period))
        if (stretch) {
            setEditedEntry(null)
            setOpenContentEditor(true)
        } else {
            setAddingContent(true)
        }
    }

    const stagedFor = contentId => staged[contentId] ?? EMPTY_STAGED

    function stageImage(contentId, file) {
        const pending = {key: `${Date.now()}-${file.name}`, file: file, url: URL.createObjectURL(file)}
        setStaged(previous => {
            const current = previous[contentId] ?? EMPTY_STAGED
            return {...previous, [contentId]: {...current, adds: [...current.adds, pending]}}
        })
    }

    function dropStagedImage(contentId, key) {
        setStaged(previous => {
            const current = previous[contentId] ?? EMPTY_STAGED
            current.adds.filter(add => add.key === key).forEach(add => URL.revokeObjectURL(add.url))
            return {...previous, [contentId]: {...current, adds: current.adds.filter(add => add.key !== key)}}
        })
    }

    /** Crossing a picture out is not deleting it; that waits for the entry to be saved. */
    function markImageRemoved(contentId, imageId, removed) {
        setStaged(previous => {
            const current = previous[contentId] ?? EMPTY_STAGED
            return {
                ...previous,
                [contentId]: {
                    ...current,
                    removes: removed
                        ? [...current.removes, imageId]
                        : current.removes.filter(id => id !== imageId),
                },
            }
        })
    }

    function discardStaged(contentId) {
        setStaged(previous => {
            const current = previous[contentId]
            if (!current) return previous

            current.adds.forEach(add => URL.revokeObjectURL(add.url))
            const next = {...previous}
            delete next[contentId]
            return next
        })
    }

    /** Everything staged for one entry, written in one go. Returns an error, or nothing. */
    async function commitStaged(contentId) {
        const {adds, removes} = stagedFor(contentId)
        if (adds.length === 0 && removes.length === 0) return null

        try {
            for (const imageId of removes) {
                await axios.delete(`${backend}/period/content/image/${imageId}`)
            }
            const added = []
            for (const add of adds) {
                const data = new FormData()
                data.append("file", add.file)
                added.push((await axios.post(`${backend}/period/content/${contentId}/image`, data)).data)
            }

            setContents(previous => previous.map(entry => entry.id === contentId
                ? {
                    ...entry,
                    // Newest first, the way they come back from the server.
                    images: [
                        ...added.reverse(),
                        ...entry.images.filter(image => !removes.includes(image.id)),
                    ],
                }
                : entry))
            discardStaged(contentId)
            return null
        } catch (error) {
            const formatted = formatError(error)
            setAlert(formatted)
            return formatted
        }
    }

    const estimatesShown = !stretch && Boolean(period.estimate)
    const revenueEstimatesShown = !stretch && Boolean(period.revenueEstimate)
    const targetSummaryShown = !(stretch && period.financial)
        && Boolean(formatTargetStats(period.targetStats))
    const targetsShown = targetSummaryShown
        && targetCandidateCount === 0
        && !targetCandidateFailed
    // Whatever of the estimates and the targets a period has sits in one block under the research.
    const estimatesInFooter = estimatesShown || revenueEstimatesShown
    const footerShown = estimatesInFooter || targetSummaryShown

    const footerLineStyle = {...NUMERIC_FONT, color: "text.primary", fontSize: 13}
    const footerLabelStyle = {...footerLineStyle, color: "text.secondary"}
    const footerNoteStyle = {
        ...NUMERIC_FONT,
        color: "text.disabled",
        fontStyle: "italic",
        fontSize: 11,
        marginTop: "-2px",
    }
    /**
     * Both rows of an estimates block are cells of one grid, which is what puts the bracketed
     * changes under the arrow rather than near it: the arrow is a column of its own, and the
     * changes start where that column starts. The past figures and the arrow are dropped on a
     * narrow screen, where their columns then take no width at all.
     */
    const footerRowStyle = {
        cursor: "pointer",
        display: "grid",
        gridTemplateColumns: "max-content max-content max-content",
        alignItems: "baseline",
        textAlign: "left",
    }

    function estimatesBlock({testId, ariaLabel, label, estimate, onOpen, format, formatPastTotal}) {
        const hiddenWhenNarrow = {display: {xs: "none", sm: "block"}}

        return (
            <Box
                data-testid={`${testId}-view`}
                aria-label={ariaLabel}
                role="button"
                tabIndex={0}
                onClick={onOpen}
                sx={footerRowStyle}
            >
                <Box data-testid={testId} sx={{display: "contents"}}>
                    <Box sx={footerLabelStyle}>{label}</Box>
                    {/* The space in front of the arrow is a hard one and belongs to this cell, so
                        the next column starts on the arrow glyph, not on the gap before it. */}
                    <Box sx={{...footerLineStyle, ...hiddenWhenNarrow}}>
                        {formatPastEstimates(estimate, format)}
                        {"\u00a0"}
                    </Box>
                    {/* The arrow opens this cell, so the bracketed changes below - the cell under
                        it - begin on exactly the same column. */}
                    <Box sx={footerLineStyle}>
                        <Box component="span" sx={{color: "text.secondary", display: {xs: "none", sm: "inline"}}}>
                            {"=>\u00a0"}
                        </Box>
                        {formatCurrentAndFutureEstimates(estimate, format)}
                    </Box>
                </Box>
                <Box sx={footerNoteStyle}>({formatEstimateDate(estimate.datetime)})</Box>
                <Box sx={{...footerNoteStyle, ...hiddenWhenNarrow, justifySelf: "center"}}>
                    ({formatPastTotal(estimate.pastTotal)})
                </Box>
                <Box sx={footerNoteStyle}>({formatEstimateChanges(estimate)})</Box>
            </Box>
        )
    }

    const financialSummary = period.financial &&
        <PeriodFinancialSummary
            financial={period.financial}
            shares={period.shares}
            priceHigh={period.priceHigh}
            priceLow={period.priceLow}
            currency={currency}
            compact={stretch}
        />

    const footer = footerShown && (
        <Box
            data-testid="period-footer"
            sx={{
                // The rule the reported financials draw under themselves, drawn again here:
                // it closes the research off from the figures that follow it.
                marginTop: "4px",
                paddingTop: "4px",
                borderTop: "1px solid",
                borderColor: "divider",
                display: "flex",
                alignItems: "flex-end",
                gap: "12px",
            }}
        >
            {estimatesInFooter &&
            <Box sx={{minWidth: 0}}>
                {estimatesShown && estimatesBlock({
                    testId: "period-estimates",
                    ariaLabel: "Add EPS Estimates",
                    label: "Eps estimates:\u00a0",
                    estimate: period.estimate,
                    onOpen: () => openEstimateDialog?.(period),
                    formatPastTotal: formatEstimatePastTotal,
                })}
                {revenueEstimatesShown && estimatesBlock({
                    testId: "period-revenue-estimates",
                    ariaLabel: "Add Revenue Estimates",
                    label: "Rev. estimates:\u00a0",
                    estimate: period.revenueEstimate,
                    onOpen: () => openRevenueEstimateDialog?.(period),
                    format: formatRevenueEstimateValue,
                    formatPastTotal: formatRevenueEstimatePastTotal,
                })}
            </Box>
            }
            {targetSummaryShown &&
                /* Beside the estimates rather than under them, in the corner: the estimates are
                   several rows of figures and the targets are one line, so stacking them wastes
                   a row. With nothing to sit beside, the line stays where it was. */
                <Box sx={{flexShrink: 0, marginLeft: estimatesInFooter ? "auto" : 0}}>
                    <PeriodTargetSummary
                        stats={period.targetStats}
                        currency={currency}
                        onOpen={() => openTargetDialog?.(period)}
                    />
                </Box>
            }
        </Box>
    )


    // The same two faces the reported financials use: a line of figures in the page's size with
    // tabular digits, and the bracketed detail under it in the smaller italic the per-share and
    // change figures are set in.

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
            {financialSummary}

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
                sx={stretch
                    ? {
                        flex: "1 1 auto",
                        minHeight: 0,
                        overflow: "auto",
                        display: "flex",
                        flexDirection: "column",
                        paddingTop: "4px",
                    }
                    : {paddingTop: "4px"}}
            >
                <PeriodContentList
                    period={period}
                    entries={contents}
                    compact={stretch}
                    adding={addingContent}
                    date={newContentDate}
                    onDateChange={setNewContentDate}
                    onAdded={() => setAddingContent(false)}
                    onSave={saveContent}
                    stagedFor={stagedFor}
                    onStageImage={stageImage}
                    onDropStagedImage={dropStagedImage}
                    onMarkImageRemoved={markImageRemoved}
                    onCommitImages={commitStaged}
                    onDiscardImages={discardStaged}
                    onOpenEditor={entry => {setEditedEntry(entry);setOpenContentEditor(true)}}
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
            {footer}
            <ContentEditorDialog
                open={openContentEditor}
                content={editedEntry?.content}
                title={[ticker, formatPeriodName(period.name),
                    editedEntry ? formatDate(editedEntry.date) : "new content"]
                    .filter(Boolean).join(" ")}
                handleClose={() => {setOpenContentEditor(false);setEditedEntry(null)}}
                save={content => saveContent(editedEntry ? editedEntry.date : newContentDate, content)}
                onSubmit={() => {
                    if (!editedEntry) {
                        return newContentDate ? null : {title: "No date", message: "Pick a date first."}
                    }
                    return commitStaged(editedEntry.id)
                }}
                onCancel={() => editedEntry && discardStaged(editedEntry.id)}
                header={!editedEntry &&
                    <ContentDatePicker
                        period={period}
                        value={newContentDate}
                        onChange={setNewContentDate}
                    />
                }
                footer={editedEntry &&
                    <PeriodContentImages
                        images={contents.find(entry => entry.id === editedEntry.id)?.images ?? []}
                        pending={stagedFor(editedEntry.id).adds}
                        removedIds={stagedFor(editedEntry.id).removes}
                        compact
                        editing
                        onAdd={file => stageImage(editedEntry.id, file)}
                        onDropPending={key => dropStagedImage(editedEntry.id, key)}
                        onMarkRemoved={(imageId, removed) =>
                            markImageRemoved(editedEntry.id, imageId, removed)}
                    />
                }
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
                <Tooltip title="Add Content" placement="left">
                    <Button aria-label="Add Content" onClick={startAddingContent}>
                        <PostAddIcon sx={{color: "success.main"}}/>
                    </Button>
                </Tooltip>
            </Stack>
        </BorderedSection>
    )
}
