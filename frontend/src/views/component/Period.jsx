import {BorderedSection} from "./BorderedSection";
import React, {useCallback, useEffect, useRef, useState} from "react";
import {PeriodFinancialSummary} from "./PeriodFinancialSummary";
import {Badge, Box, Button, Stack} from "@mui/material";
import Tooltip from "@mui/material/Tooltip";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import {formatDate, formatError, formatPeriodName, formatTargetStats} from "../../service/FormattingService";
import {nextPeriod} from "../../service/PeriodService";
import axios from "axios";
import {backend} from "../../properties";
import {ContentDatePicker, PeriodContentList, defaultEntryDate, isDateTaken} from "./PeriodContentList";
import {formatRevenueEstimatePastTotal, formatRevenueEstimateValue} from "./PeriodEstimateLine";
import {ContentImages} from "./ContentImages";
import {ContentEditorDialog} from "../../dialog/ContentEditorDialog";
import {ReactComponent as FinancialsPlusIcon} from "../../assets/icons/financials-plus.svg";
import {ReactComponent as EpsEstimatesPlusIcon} from "../../assets/icons/estimates-plus-eps.svg";
import {ReactComponent as RevenueEstimatesPlusIcon} from "../../assets/icons/estimates-plus-revenue.svg";
import EditNoteIcon from "@mui/icons-material/EditNote";
import PostAddIcon from "@mui/icons-material/PostAdd";
import NewspaperOutlinedIcon from "@mui/icons-material/NewspaperOutlined";
import TrackChangesIcon from "@mui/icons-material/TrackChanges";
import {PeriodTargetSummary} from "./PeriodTargetSummary";
import {useStagedImages} from "./useStagedImages";

/** What the period's action buttons occupy on the right-hand edge of the card, measured from it. */
const ACTION_COLUMN_WIDTH = "29px"

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
                        .sort((first, second) => second.date.localeCompare(first.date))
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

    const {
        stagedFor, stageImage, dropStagedImage, markImageRemoved, discardStaged, commitStaged,
    } = useStagedImages({
        uploadUrl: contentId => `${backend}/period/content/${contentId}/image`,
        deleteUrl: imageId => `${backend}/period/content/image/${imageId}`,
        applied: (contentId, {added, removedIds}) => setContents(previous =>
            previous.map(entry => entry.id === contentId
                ? {...entry, images: [...added, ...entry.images.filter(image => !removedIds.includes(image.id))]}
                : entry)),
        setAlert,
    })


    // The reported figures draw their own rule above the research; without them the research is
    // what opens the card, and it reads better up against the title than a line below it.
    const contentTop = period.financial ? {paddingTop: "1px"} : {marginTop: "-3px"}

    const newContentDateTaken = isDateTaken(contents, newContentDate)

    const targetSummaryShown = Boolean(formatTargetStats(period.targetStats))
    const targetsShown = targetSummaryShown
        && targetCandidateCount === 0
        && !targetCandidateFailed

    /**
     * Estimates are dated, so they belong among the days rather than in a block of their own:
     * the page reads as what was recorded about the period, in the order it was recorded. A
     * period keeps every estimate taken while it ran, not only the last of them, so each reads
     * on the day it was taken.
     */
    const estimateLines = [
        ...(period.estimates ?? []).map((estimate, index) => ({
            key: `eps-${estimate.id ?? index}`,
            date: estimate.datetime?.substring(0, 10),
            line: {
                testId: "period-estimates",
                label: "Eps estimates:",
                estimate: estimate,
            },
        })),
        ...(period.revenueEstimates ?? []).map((estimate, index) => ({
            key: `revenue-${estimate.id ?? index}`,
            date: estimate.datetime?.substring(0, 10),
            line: {
                testId: "period-revenue-estimates",
                label: "Rev. estimates:",
                estimate: estimate,
                format: formatRevenueEstimateValue,
                pastTotalFormat: formatRevenueEstimatePastTotal,
            },
        })),
    ]

    /**
     * The targets read beside the period's own figures on a wide screen: in the free corner of
     * the reported financials once there are any, and in the card's top corner while there are
     * none. The narrow view has no corner to spare, so there they stay under the research.
     */
    const targetSummary = targetSummaryShown &&
        <PeriodTargetSummary
            stats={period.targetStats}
            currency={currency}
            onOpen={() => openTargetDialog?.(period)}
        />

    const financialSummary = period.financial &&
        <PeriodFinancialSummary
            financial={period.financial}
            shares={period.shares}
            priceHigh={period.priceHigh}
            priceLow={period.priceLow}
            currency={currency}
            compact={stretch}
            targets={stretch ? null : targetSummary}
        />

    /**
     * Nothing was reported, so there is no block to sit in: the targets take the card's own top
     * corner. They are laid over the research rather than above it, which leaves the research
     * where it starts instead of a line lower for a figure it reads beside.
     */
    const cornerTargets = !stretch && !period.financial && targetSummary &&
        <Box sx={{
            position: "absolute",
            top: "4px",
            right: ACTION_COLUMN_WIDTH,
            zIndex: 1,
        }}>
            {targetSummary}
        </Box>

    /** The narrow view keeps them where they were: under the research, behind a rule. */
    const footer = stretch && targetSummary && (
        <Box
            data-testid="period-footer"
            sx={{
                marginTop: "4px",
                paddingTop: "4px",
                borderTop: "1px solid",
                borderColor: "divider",
            }}
        >
            {targetSummary}
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
            /* Short of this the card can collapse to a line or two, which leaves no room
               beside it for the action buttons. */
            style={{color: 'text.primary', minHeight: "80px"}}
            stretch={stretch}
            highlightTitle={stretch}
        >
            {cornerTargets}
            {financialSummary}

            <Box sx={{
                // The card's own bottom padding leaves more room under the last day than the
                // days leave between themselves; half of it is enough.
                marginBottom: "-3px",
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
                        ...contentTop,
                    }
                    : contentTop}
            >
                <PeriodContentList
                    period={period}
                    entries={contents}
                    compact={stretch}
                    adding={addingContent}
                    date={newContentDate}
                    onDateChange={setNewContentDate}
                    estimates={estimateLines}
                    onAdded={() => setAddingContent(false)}
                    onCancelAdding={() => setAddingContent(false)}
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
                        if (!newContentDate) return {title: "No date", message: "Pick a date first."}
                        // The day is already an entry of its own: writing it here would replace
                        // what that entry holds rather than add to the period.
                        if (newContentDateTaken) {
                            return {
                                title: "Date already used",
                                message: "This period already has content for that date.",
                            }
                        }
                        return null
                    }
                    return commitStaged(editedEntry.id)
                }}
                onCancel={() => editedEntry && discardStaged(editedEntry.id)}
                header={!editedEntry &&
                    <ContentDatePicker
                        period={period}
                        value={newContentDate}
                        onChange={setNewContentDate}
                        problem={newContentDateTaken ? "This date already has content" : ""}
                    />
                }
                footer={editedEntry &&
                    <ContentImages
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
                       position: "absolute", top: "6px", right: "4px", zIndex: 1,
                       opacity: {xs: 1, sm: 0}, pointerEvents: {xs: "auto", sm: "none"},
                       maxHeight: "calc(100% - 12px)", overflowY: "auto", overflowX: "hidden",
                       // Room for the scrollbar when there are more buttons than fit, taken
                       // back off the margin so the icons still sit against the card's edge.
                       paddingRight: "5px", marginRight: "-5px",
                       transition: "opacity 120ms ease-in-out",
                       ".mainContainer:hover &": {opacity: 1, pointerEvents: "auto",},
                       "& .MuiButton-root": {minWidth: 0, padding: "2px", lineHeight: 0,},
                       "& svg": {width: "20px", height: "20px", display: "block",},
                   }}
            >
                {/* One new day at a time: while one is open, the button that would start
                    another is gone - the day being written is the one to finish. */}
                {!addingContent && !openContentEditor &&
                    <Tooltip title="Add Content" placement="left">
                        <Button aria-label="Add Content" onClick={startAddingContent}>
                            <PostAddIcon sx={{color: "success.main"}}/>
                        </Button>
                    </Tooltip>
                }
                {!period.financial &&
                    <Tooltip title="Add Financials" placement="left">
                        <Button onClick={openDialog}>
                            <FinancialsPlusIcon/>
                        </Button>
                    </Tooltip>
                }
                <Tooltip title="Add EPS Estimates" placement="left">
                    <Button aria-label="Add EPS Estimates" onClick={() => openEstimateDialog?.(period)}>
                        <EpsEstimatesPlusIcon/>
                    </Button>
                </Tooltip>
                <Tooltip title="Add Revenue Estimates" placement="left">
                    <Button aria-label="Add Revenue Estimates" onClick={() => openRevenueEstimateDialog?.(period)}>
                        <RevenueEstimatesPlusIcon/>
                    </Button>
                </Tooltip>
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
