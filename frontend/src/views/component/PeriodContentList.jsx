import {Box} from "@mui/material";
import React, {useRef, useState} from "react";
import {ContentEditor} from "./ContentEditor";
import {PeriodContentImages} from "./PeriodContentImages";
import {formatDate} from "../../service/FormattingService";

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
export function isOutsidePeriod(entry, period) {
    if (entry?.outsidePeriod !== undefined && entry.outsidePeriod !== null) return entry.outsidePeriod;

    const start = period?.previousReportDate?.substring(0, 10);
    const end = period?.reportDate?.substring(0, 10);
    if (!start || !end) return false;
    return entry.date < start || entry.date > end;
}

/** The date a new entry is written for, held inside the period it belongs to. */
export const ContentDatePicker = ({period, value, onChange}) => {
    const range = periodRange(period);

    return (
        <Box
            component="input"
            type="date"
            aria-label="New content date"
            value={value}
            min={range.start || undefined}
            max={range.end || undefined}
            onChange={event => onChange(event.target.value)}
            sx={{
                width: "130px",
                fontSize: 11,
                fontWeight: 600,
                color: "text.secondary",
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
            sx={{display: "flex", flexDirection: "column", gap: "2px"}}
        >
            <Box
                data-testid={`period-content-date-${entry.date}`}
                title={outside ? "This date is outside the period" : undefined}
                sx={{
                    fontSize: 11,
                    fontWeight: 600,
                    textDecoration: "underline",
                    alignSelf: "flex-start",
                    color: outside ? "error.main" : "text.secondary",
                }}
            >
                {formatDate(entry.date)}
            </Box>
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
                        style={{margin: "0 5px 6px 0", flex: "1 1 auto", minHeight: 0}}
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
    date = "",
    onDateChange,
}) => {
    async function saveNew(value) {
        if (!date) return;

        await onSave(date, value);
        onAdded?.();
    }

    return (
        <Box data-testid="period-content-list" sx={{display: "flex", flexDirection: "column", gap: "8px"}}>
            {entries.map(entry => (
                <Entry
                    key={entry.id ?? entry.date}
                    entry={entry}
                    period={period}
                    compact={compact}
                    staged={stagedFor(entry.id)}
                    onSave={onSave}
                    onStageImage={onStageImage}
                    onDropStagedImage={onDropStagedImage}
                    onMarkImageRemoved={onMarkImageRemoved}
                    onCommitImages={onCommitImages}
                    onDiscardImages={onDiscardImages}
                    onOpenEditor={onOpenEditor}
                />
            ))}

            {adding &&
                <Box data-testid="period-content-new" sx={{display: "flex", flexDirection: "column", gap: "2px"}}>
                    {/* The picker cannot reach past the period, which is the same rule the
                        backend applies; an empty date means today was already past it. */}
                    <ContentDatePicker period={period} value={date} onChange={onDateChange}/>
                    <ContentEditor
                        content={null}
                        update={saveNew}
                        style={{margin: "0 5px 6px 0"}}
                        alwaysEditing
                    />
                </Box>
            }
        </Box>
    );
};
