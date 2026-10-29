import {Box} from "@mui/material";
import React, {useEffect, useState} from "react";
import {ContentEditor} from "./ContentEditor";
import {ContentEntry, DateHeading, isEmptyEntry} from "./ContentEntry";
import {PeriodEstimateLine} from "./PeriodEstimateLine";

/** One day from the next, one estimate from the next, and a day's research from its estimates. */
const DAY_GAP = "4px"
const ESTIMATE_GAP = "0px"
const AFTER_ESTIMATES_GAP = "2px"

export {isEmptyEntry};

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
                ? <ContentEntry
                    key={item.key}
                    entry={item.entry}
                    outside={isOutsidePeriod(item.entry, period)}
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
                    testIdFor={entry => `period-content-${entry.date}`}
                    dateTestIdFor={entry => `period-content-date-${entry.date}`}
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
