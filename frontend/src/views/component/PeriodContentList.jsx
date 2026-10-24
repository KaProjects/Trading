import {Box} from "@mui/material";
import React, {useEffect, useRef, useState} from "react";
import {ContentEditor} from "./ContentEditor";
import {PeriodContentImages} from "./PeriodContentImages";
import {PeriodEstimateLine} from "./PeriodEstimateLine";
import {formatDate} from "../../service/FormattingService";

/** One day from the next, one estimate from the next, and a day's research from its estimates. */
const DAY_GAP = "4px"
const ESTIMATE_GAP = "0px"
const AFTER_ESTIMATES_GAP = "2px"

/** The editor's empty document: an entry holding only this, and no images, is not shown. */
const EMPTY_DOCUMENT = '[{"type":"paragraph","children":[{"text":""}]}]'

export function isEmptyEntry(entry) {
    const content = entry?.content
    const blank = !content || content.trim() === "" || content === EMPTY_DOCUMENT
    return blank && (entry?.images ?? []).length === 0
}

export const periodRange = period => ({
    start: period?.previousReportDate?.substring(0, 10) ?? "",
    end: period?.reportDate?.substring(0, 10) ?? "",
});

/**
 * The day a new entry opens on: today, and nothing at all when today is not a day this period
 * covers - better an empty picker than a date that was never meant.
 */
export function defaultEntryDate(period, today = new Date()) {
    const now = today.toISOString().substring(0, 10);
    const {start, end} = periodRange(period);

    if (end && now > end) return "";
    if (start && now < start) return "";
    return now;
}

/** A date the period no longer covers is shown in red rather than refused after the fact. */
/** Whether the period already has an entry for this day. */
export function isDateTaken(entries, date) {
    return Boolean(date) && (entries ?? []).some(entry => entry.date === date);
}

export function isOutsidePeriod(entry, period) {
    if (entry?.outsidePeriod !== undefined && entry.outsidePeriod !== null) return entry.outsidePeriod;

    const start = period?.previousReportDate?.substring(0, 10);
    const end = period?.reportDate?.substring(0, 10);
    if (!start || !end) return false;
    return entry.date < start || entry.date > end;
}

const DateHeading = ({date, outside = false, testId}) => (
    <Box
        data-testid={testId}
        title={outside ? "This date is outside the period" : undefined}
        sx={{
            fontSize: 11,
            fontWeight: 600,
            textDecoration: "underline",
            alignSelf: "flex-start",
            color: outside ? "error.main" : "text.secondary",
        }}
    >
        {formatDate(date)}
    </Box>
);

/**
 * The date a new entry is written for, held inside the period it belongs to. `problem` is what
 * stands in the way of writing that day, which the picker says in red: a day the period already
 * has is an entry of its own to be written in, and saving over it from here would replace what
 * it holds.
 */
export const ContentDatePicker = ({period, value, onChange, problem = ""}) => {
    const range = periodRange(period);

    return (
        <Box
            component="input"
            type="date"
            aria-label="New content date"
            title={problem || undefined}
            value={value}
            min={range.start || undefined}
            max={range.end || undefined}
            onChange={event => onChange(event.target.value)}
            sx={{
                width: "130px",
                fontSize: 11,
                fontWeight: 600,
                color: problem ? "error.main" : "text.secondary",
                textDecoration: "underline",
                border: "none",
                outline: "none",
                background: "transparent",
                fontFamily: "inherit",
                padding: 0,
            }}
        />
    );
};

const Entry = ({
    entry,
    period,
    headed = true,
    spacingBefore = 0,
    compact,
    staged,
    onSave,
    onStageImage,
    onDropStagedImage,
    onMarkImageRemoved,
    onCommitImages,
    onDiscardImages,
    onOpenEditor,
}) => {
    const outside = isOutsidePeriod(entry, period);
    // Adding and removing pictures belongs to writing, so those controls appear with the cursor
    // and go again when it leaves. Focus is watched on the whole entry, which is what keeps them
    // in place while the strip beside the text is being used.
    const [editing, setEditing] = useState(false);
    // Escape puts the text back, and has to put the pictures back with it; anything else that
    // ends the writing keeps them, saving them alongside the text.
    const cancelled = useRef(false);

    function stopEditing() {
        setEditing(false);
        if (cancelled.current) {
            onDiscardImages(entry.id);
        } else {
            onCommitImages(entry.id);
        }
        cancelled.current = false;
    }

    return (
        <Box
            data-testid={`period-content-${entry.date}`}
            // Only focus that lands inside the entry counts. The picture viewer is a child of
            // this entry in the React tree but a portal in the page, so without the check its
            // focus would read as the entry being written in, and merely opening a picture
            // would bring up the editing controls.
            onFocusCapture={event => {
                if (event.currentTarget.contains(event.target)) setEditing(true);
            }}
            onBlurCapture={event => {
                if (!event.currentTarget.contains(event.target)) return;
                if (event.currentTarget.contains(event.relatedTarget)) return;

                if (event.relatedTarget) {
                    stopEditing();
                    return;
                }

                // Focus went nowhere the page can name, which is both what clicking an empty
                // patch looks like and what the file picker looks like. The picker takes focus
                // off the window itself, so writing carries on until the window has it back and
                // the cursor is somewhere else.
                const entry = event.currentTarget;
                setTimeout(() => {
                    if (document.hasFocus() && !entry.contains(document.activeElement)) {
                        stopEditing();
                    }
                }, 150);
            }}
            sx={{display: "flex", flexDirection: "column", gap: "2px", marginTop: spacingBefore}}
        >
            {headed &&
                <DateHeading date={entry.date} outside={outside} testId={`period-content-date-${entry.date}`}/>
            }
            <Box sx={{
                display: "flex",
                flexDirection: compact ? "column" : "row",
                // The text and the pictures beside it are the same height, so the editor can be
                // reached by clicking anywhere in the entry rather than on the words themselves.
                alignItems: compact ? "flex-start" : "stretch",
                gap: compact ? "4px" : "8px",
            }}>
                <Box
                    sx={{
                        flex: "1 1 auto",
                        minWidth: 0,
                        width: "100%",
                        display: "flex",
                        flexDirection: "column",
                    }}
                    onClick={compact ? () => onOpenEditor(entry) : undefined}
                >
                    <ContentEditor
                        // The version makes a saved entry start the editor again on what was
                        // saved: without it the editor keeps showing the text it was opened with.
                        key={`${entry.id}-${entry.date}-${entry.version ?? 0}`}
                        content={entry.content}
                        update={value => onSave(entry.date, value)}
                        onCancel={() => {cancelled.current = true}}
                        style={{margin: "0 5px 3px 0", flex: "1 1 auto", minHeight: 0}}
                        locked={compact}
                    />
                </Box>
                <PeriodContentImages
                    images={entry.images}
                    pending={staged.adds}
                    removedIds={staged.removes}
                    compact={compact}
                    editing={editing}
                    onAdd={file => onStageImage(entry.id, file)}
                    onDropPending={key => onDropStagedImage(entry.id, key)}
                    onMarkRemoved={(imageId, removed) =>
                        onMarkImageRemoved(entry.id, imageId, removed)}
                />
            </Box>
        </Box>
    );
};

/**
 * A period's research, one entry per day, oldest first, with a tile at the end to start the next
 * one. The tile carries its own date so a new entry can be written straight away; the date of an
 * entry that already exists is fixed, because the date is what identifies it.
 */
/**
 * The days of a period in one stream: research written on a day, and the estimates recorded on
 * one, in date order. A day opens with the estimates taken that day - the figures one was
 * writing about - and the research follows under the same heading.
 */
function byDate(entries, estimates) {
    const items = entries.map(entry => ({key: `content-${entry.id ?? entry.date}`, date: entry.date, entry: entry}));

    estimates.forEach(estimate => {
        items.push({key: `estimate-${estimate.key}`, date: estimate.date ?? "", line: estimate.line});
    });

    // The newest day first, the way a period is read: what was last recorded about it is what
    // one came for. Estimates that carry no date of their own still belong on the page; they
    // read at the end, with nothing above them to head a day they cannot name.
    items.sort((first, second) => Number(!first.date) - Number(!second.date)
        || second.date.localeCompare(first.date)
        // The day's estimates first, the research written that day under them.
        || Number(Boolean(first.entry)) - Number(Boolean(second.entry)));

    // A day is named once, by whatever comes first on it: an estimate under the research of the
    // same day, or under another estimate, reads as part of that day rather than a new one.
    // What is left of a day sits closer to it than the next day does, so the space above each
    // part says which day it belongs to.
    return items.map((item, index) => {
        const previous = items[index - 1];
        const sameDay = Boolean(previous) && previous.date === item.date;

        return {
            ...item,
            headed: Boolean(item.date) && !sameDay,
            spacingBefore: !previous ? 0
                : !sameDay || !previous.line ? DAY_GAP
                : item.line ? ESTIMATE_GAP
                : AFTER_ESTIMATES_GAP,
        };
    });
}

export const PeriodContentList = ({
    period,
    entries = [],
    compact = false,
    adding = false,
    onSave,
    stagedFor = () => ({adds: [], removes: []}),
    onStageImage,
    onDropStagedImage,
    onMarkImageRemoved,
    onCommitImages,
    onDiscardImages,
    onOpenEditor,
    onAdded,
    onCancelAdding,
    date = "",
    onDateChange,
    estimates = [],
}) => {
    const taken = isDateTaken(entries, date);
    // What a refused save left behind. Picking another day is the answer to it, so that is what
    // clears it; the editor's own marking is no use here, since Slate reports a change as the
    // cursor leaves and that change wipes the error it had just been given.
    const [refused, setRefused] = useState("");
    const problem = taken ? "This date already has content" : refused;

    useEffect(() => setRefused(""), [date]);

    /**
     * A day that cannot be written answers with an error rather than quietly doing nothing: the
     * editor keeps what was typed and stays open. Returning nothing would read as saved, and the
     * day's text would be gone with the block that held it.
     */
    async function saveNew(value) {
        if (!date) {
            setRefused("Pick a date first");
            return {title: "No date", message: "Pick a date first."}
        }
        if (taken) {
            return {title: "Date already used", message: "This period already has content for that date."}
        }

        const error = await onSave(date, value);
        if (error) return error;
        onAdded?.();
    }

    return (
        <Box data-testid="period-content-list" sx={{display: "flex", flexDirection: "column"}}>
            {/* The newest day reads first, and a day being written is the newest of them. */}
            {adding &&
                <Box
                    data-testid="period-content-new"
                    sx={{
                        display: "flex",
                        flexDirection: "column",
                        gap: "2px",
                        marginBottom: DAY_GAP,
                        // A day that cannot be saved says so around the whole block, not only on
                        // the date: the text is held here until it can be written somewhere.
                        ...(problem && {
                            outline: "1px solid",
                            outlineColor: "error.main",
                            borderRadius: "4px",
                            padding: "2px",
                        }),
                    }}
                >
                    {/* The picker cannot reach past the period, which is the same rule the
                        backend applies; an empty date means today was already past it. */}
                    <Box sx={{display: "flex", alignItems: "baseline", gap: "8px"}}>
                        <ContentDatePicker
                            period={period}
                            value={date}
                            onChange={onDateChange}
                            problem={problem}
                        />
                        {problem &&
                            <Box data-testid="period-content-new-problem"
                                 sx={{fontSize: 11, fontStyle: "italic", color: "error.main"}}>
                                {problem}
                            </Box>
                        }
                    </Box>
                    <ContentEditor
                        content={null}
                        update={saveNew}
                        style={{margin: "0 5px 3px 0"}}
                        alwaysEditing
                        // Escape gives up on the day altogether, so the whole block goes with it.
                        onCancel={onCancelAdding}
                    />
                </Box>
            }

            {byDate(entries, estimates).map(item => item.entry
                ? <Entry
                    key={item.key}
                    entry={item.entry}
                    period={period}
                    headed={item.headed}
                    spacingBefore={item.spacingBefore}
                    compact={compact}
                    staged={stagedFor(item.entry.id)}
                    onSave={onSave}
                    onStageImage={onStageImage}
                    onDropStagedImage={onDropStagedImage}
                    onMarkImageRemoved={onMarkImageRemoved}
                    onCommitImages={onCommitImages}
                    onDiscardImages={onDiscardImages}
                    onOpenEditor={onOpenEditor}
                />
                : <Box
                    key={item.key}
                    sx={{
                        display: "flex",
                        flexDirection: "column",
                        gap: "2px",
                        marginTop: item.spacingBefore,
                    }}
                >
                    {/* A day's estimates are shown, not written: the heading only repeats the
                        date where no research was written that day. */}
                    {item.headed &&
                        <DateHeading
                            date={item.date}
                            outside={isOutsidePeriod({date: item.date}, period)}
                            testId={`period-estimate-date-${item.date}`}
                        />
                    }
                    <PeriodEstimateLine {...item.line}/>
                </Box>)}
        </Box>
    );
};
