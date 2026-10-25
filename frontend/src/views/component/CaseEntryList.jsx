import {Box} from "@mui/material";
import React from "react";
import {ContentEntry} from "./ContentEntry";

/** One day from the next; the figure the period stream uses, so the two read alike. */
const DAY_GAP = "4px"

export const today = () => new Date().toISOString().substring(0, 10)

/** Whether the case already holds a day for today, which is the only day that can be opened. */
export const hasEntryForToday = (entries = []) => entries.some(entry => entry.date === today())

/**
 * What has been written into a case, newest day first. A case is kept forwards: the only new
 * day is today, so there is no date to pick here - the day a new entry opens on is the day it
 * is written.
 */
export const CaseEntryList = ({
    entries = [],
    compact = false,
    stagedFor = () => ({adds: [], removes: []}),
    onSave,
    onStageImage,
    onDropStagedImage,
    onMarkImageRemoved,
    onCommitImages,
    onDiscardImages,
    onOpenEditor,
}) => {
    const days = [...entries].sort((first, second) => second.date.localeCompare(first.date))

    return (
        <Box data-testid="case-entry-list" sx={{display: "flex", flexDirection: "column"}}>
            {days.map((entry, index) => (
                <ContentEntry
                    key={entry.id ?? entry.date}
                    entry={entry}
                    headed
                    spacingBefore={index === 0 ? 0 : DAY_GAP}
                    compact={compact}
                    staged={stagedFor(entry.id)}
                    onSave={onSave}
                    onStageImage={onStageImage}
                    onDropStagedImage={onDropStagedImage}
                    onMarkImageRemoved={onMarkImageRemoved}
                    onCommitImages={onCommitImages}
                    onDiscardImages={onDiscardImages}
                    onOpenEditor={onOpenEditor}
                    testIdFor={each => `case-entry-${each.date}`}
                    dateTestIdFor={each => `case-entry-date-${each.date}`}
                />
            ))}
        </Box>
    )
}
