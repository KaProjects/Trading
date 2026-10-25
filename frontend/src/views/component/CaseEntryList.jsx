import {Box} from "@mui/material";
import React from "react";
import {ContentEntry} from "./ContentEntry";
import {CaseTable} from "./CaseTable";
import {Button, Tooltip} from "@mui/material";
import TableChartIcon from "@mui/icons-material/TableChartOutlined";

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
    onAddTable,
    onChangeTable,
    onDeleteTable,
}) => {
    const days = [...entries].sort((first, second) => second.date.localeCompare(first.date))

    return (
        <Box data-testid="case-entry-list" sx={{display: "flex", flexDirection: "column"}}>
            {days.map((entry, index) => (
                <Box key={entry.id ?? entry.date}>
                <ContentEntry
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
                {(entry.tables ?? []).map(table => (
                    <CaseTable
                        key={table.id}
                        table={table}
                        editable={Boolean(entry.id)}
                        onChange={changes => onChangeTable(table.id, changes)}
                        onDelete={() => onDeleteTable(entry.id, table.id)}
                    />
                ))}
                {entry.id &&
                    <Tooltip title="Add table">
                        <Button size="small" aria-label={`Add table ${entry.date}`}
                                onClick={() => onAddTable(entry.id)}
                                sx={{minWidth: 0, padding: "2px 4px", marginTop: "2px"}}>
                            <TableChartIcon sx={{fontSize: 16, color: "text.secondary"}}/>
                        </Button>
                    </Tooltip>
                }
                </Box>
            ))}
        </Box>
    )
}
