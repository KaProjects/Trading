import {Box, Button, IconButton, Tooltip} from "@mui/material";
import EditIcon from "@mui/icons-material/EditOutlined";
import SaveIcon from "@mui/icons-material/SaveOutlined";
import CloseIcon from "@mui/icons-material/Close";
import TableChartIcon from "@mui/icons-material/TableChartOutlined";
import React, {useEffect, useRef, useState} from "react";
import {ContentEditor} from "./ContentEditor";
import {caseImageUrl, ContentImages} from "./ContentImages";
import {DateHeading} from "./ContentEntry";
import {CaseTable} from "./CaseTable";
import {CaseTradeLine} from "./CaseTradeLine";

/** One day from the next. */
const DAY_GAP = "8px"
/** The rule between two days sits closer to the day it heads than to the one it closes. */
const AFTER_RULE = "2px"

export const today = () => new Date().toISOString().substring(0, 10)

/** Whether the case already holds a day for today, which is the only day that can be opened. */
export const hasEntryForToday = (entries = []) => entries.some(entry => entry.date === today())

/**
 * A day of a case, read or written.
 *
 * Reading is the usual state. Writing is asked for once - the pencil over the day - and from
 * then until Done the whole day is the editor: the text is open with the cursor in it, the
 * pictures and the tables carry their controls, and nothing is written to the server until Done
 * is pressed, which writes the text, the pictures and the tables together.
 */
const Day = ({
    entry,
    trades = [],
    editing,
    onEdit,
    onMoveEntry,
    onDone,
    onCancel,
    staged,
    onStageImage,
    onDropStagedImage,
    onMarkImageRemoved,
    onAddTable,
    onChangeTable,
    onDeleteTable,
}) => {
    const [draft, setDraft] = useState(null)
    const editorBox = useRef(null)

    // The pencil opens the editor, not a thing to click into: the cursor goes where the writing
    // happens, so a day can be written the moment it is opened.
    useEffect(() => {
        if (!editing) {
            setDraft(null)
            return
        }
        editorBox.current?.querySelector('[contenteditable="true"]')?.focus()
    }, [editing])

    return (
        <Box
            data-testid={`case-entry-${entry.date}`}
            sx={{
                display: "flex",
                flexDirection: "column",
                gap: "2px",
                // The padding is always there so nothing moves when the day opens; what the
                // writing adds is the frame around it, which says where the day being written
                // starts and ends.
                padding: "4px 6px",
                borderRadius: "4px",
                border: "1px solid",
                borderColor: editing ? "primary.main" : "transparent",
                backgroundColor: editing ? "action.hover" : "transparent",
                "&:hover .case-entry-actions": {opacity: 1},
            }}
        >
            {/* The day is headed by its date, and the one thing to do with it sits in the
                opposite corner: the pencil while it is being read, the save while it is being
                written, in the same place so the eye does not have to move. */}
            {/* A fixed height, so the row is as tall as the date in it and not as tall as the
                buttons beside it: what follows the date reads as the next line under it. */}
            <Box sx={{display: "flex", alignItems: "center", gap: "6px", height: "16px"}}>
                <Box onClick={() => onMoveEntry(entry)}
                     sx={{cursor: "pointer"}}
                     role="button"
                     aria-label={`Day written ${entry.date}`}>
                    <DateHeading date={entry.date} testId={`case-entry-date-${entry.date}`}/>
                </Box>
                {editing &&
                    <Box sx={{fontSize: 11, fontStyle: "italic", color: "primary.main"}}>writing</Box>
                }
                <Box sx={{flex: 1}}/>
                {/* Kept to the size of their icons: a button with the usual room around it
                    makes this row three times the height of the date in it, and everything
                    under the date - a trade, the writing - is pushed down by the difference. */}
                <Box className="case-entry-actions"
                     sx={{
                         display: "flex",
                         opacity: editing ? 1 : 0,
                         transition: "opacity 120ms ease-in-out",
                         "& .MuiIconButton-root": {padding: "2px"},
                     }}>
                    {editing &&
                        <Tooltip title="Cancel">
                            <IconButton aria-label={`Cancel ${entry.date}`} onClick={() => onCancel(entry)}>
                                <CloseIcon sx={{fontSize: 21, color: "text.secondary"}}/>
                            </IconButton>
                        </Tooltip>
                    }
                    {editing
                        ? <Tooltip title="Save">
                            <IconButton aria-label={`Save ${entry.date}`}
                                        onClick={() => onDone(entry, draft)}>
                                <SaveIcon sx={{fontSize: 21, color: "success.main"}}/>
                            </IconButton>
                        </Tooltip>
                        : <Tooltip title="Edit">
                            <IconButton aria-label={`Edit ${entry.date}`} onClick={onEdit}>
                                <EditIcon sx={{fontSize: 21, color: "text.secondary"}}/>
                            </IconButton>
                        </Tooltip>
                    }
                </Box>
            </Box>

            {/* What was acted on that day comes between the date and what was written about
                it: the trade is the fact, the writing is the reading of it. It reads with the
                date, so it keeps its distance from the writing rather than from the heading. */}
            {trades.length > 0 &&
                <Box sx={{display: "flex", flexDirection: "column", gap: "2px", marginBottom: "7px"}}>
                    {trades.map(trade => <CaseTradeLine key={trade.key} trade={trade}/>)}
                </Box>
            }

            <Box ref={editorBox}>
                <ContentEditor
                    // Re-keyed on the state it is in, so the editor opens on what was last
                    // saved and closes on what the day now holds.
                    key={`${entry.id}-${entry.date}-${entry.version ?? 0}-${editing}`}
                    content={entry.content}
                    alwaysEditing={editing}
                    autoSave={false}
                    onValueChange={setDraft}
                    update={() => null}
                    style={{margin: "0 5px 3px 0"}}
                    locked={!editing}
                />
            </Box>

            {(entry.tables ?? []).map(table => (
                <CaseTable
                    key={table.id}
                    table={table}
                    editable={editing}
                    onChange={changes => onChangeTable(table.id, changes)}
                    onDelete={() => onDeleteTable(entry.id, table.id)}
                />
            ))}

            {/* Under the tables it adds to, and there for as long as the day is being written
                - the same standing offer the picture strip makes with its own tile. */}
            {editing &&
                <Button
                    size="small"
                    aria-label={`Add table ${entry.date}`}
                    onClick={() => onAddTable(entry.id)}
                    startIcon={<TableChartIcon sx={{fontSize: 15}}/>}
                    sx={{alignSelf: "flex-start", fontSize: 11, color: "text.secondary", padding: "1px 6px"}}
                >
                    Add table
                </Button>
            }

            {/* The pictures come last, side by side - the layout the narrow period view uses
                - rather than in a column beside the text. What the day says is read first, the
                tables that carry it next, and the pictures are what it is all shown on. */}
            {(entry.images?.length > 0 || staged.adds.length > 0 || editing) &&
                <ContentImages
                    images={entry.images ?? []}
                    pending={staged.adds}
                    removedIds={staged.removes}
                    compact
                    editing={editing}
                    urlOf={caseImageUrl}
                    onAdd={file => onStageImage(entry.id, file)}
                    onDropPending={key => onDropStagedImage(entry.id, key)}
                    onMarkRemoved={(imageId, removed) => onMarkImageRemoved(entry.id, imageId, removed)}
                />
            }
        </Box>
    )
}

/**
 * What has been written into a case, newest day first. A case is kept forwards: the only new
 * day is today, so there is no date to pick here - the day a new entry opens on is the day it
 * is written.
 */
/** A day that was acted on but not written about: its date and what was done, nothing else. */
const TradeDay = ({date, trades}) => (
    <Box data-testid={`case-trades-${date}`}
         sx={{display: "flex", flexDirection: "column", gap: "2px", padding: "4px 6px"}}>
        <DateHeading date={date} testId={`case-trade-date-${date}`}/>
        {trades.map(trade => <CaseTradeLine key={trade.key} trade={trade}/>)}
    </Box>
)

/**
 * The stream, newest day first: one block per day, whatever happened on it. A trade is
 * something that happened on a day of the case, so it reads on that day rather than on one of
 * its own - under the date, above the writing that reads it, the way a period's estimates read
 * above its research.
 */
function byDate(entries, trades) {
    const days = new Map()
    const dayOf = date => {
        if (!days.has(date)) days.set(date, {key: date, date: date, entry: null, trades: []})
        return days.get(date)
    }

    entries.forEach(entry => {dayOf(entry.date).entry = entry})
    trades.forEach(trade => dayOf(trade.date).trades.push(trade))

    return [...days.values()].sort((first, second) => second.date.localeCompare(first.date))
}

export const CaseEntryList = ({
    entries = [],
    trades = [],
    stagedFor = () => ({adds: [], removes: []}),
    onDone,
    onStageImage,
    onDropStagedImage,
    onMarkImageRemoved,
    onAddTable,
    onChangeTable,
    onDeleteTable,
    editingDate,
    onEditingDateChange,
    onMoveEntry,
    onCancel,
}) => {
    return (
        <Box
            data-testid="case-entry-list"
            sx={{
                display: "flex",
                flexDirection: "column",
                gap: DAY_GAP,
            }}
        >
            {byDate(entries, trades).map((day, index) => (
                <Box
                    key={day.key}
                    // A rule between one day and the next, so it is plain where a day starts
                    // and ends. What belongs to the same day - its trades and its writing -
                    // stays together under the one heading.
                    sx={index === 0 ? undefined : {
                        borderTop: "1px solid",
                        borderColor: "divider",
                        paddingTop: AFTER_RULE,
                    }}
                >
                {!day.entry
                ? <TradeDay date={day.date} trades={day.trades}/>
                : <Day
                    entry={day.entry}
                    trades={day.trades}
                    editing={editingDate === day.entry.date}
                    onEdit={() => onEditingDateChange(day.entry.date)}
                    onMoveEntry={onMoveEntry}
                    onDone={onDone}
                    onCancel={onCancel}
                    staged={stagedFor(day.entry.id)}
                    onStageImage={onStageImage}
                    onDropStagedImage={onDropStagedImage}
                    onMarkImageRemoved={onMarkImageRemoved}
                    onAddTable={onAddTable}
                    onChangeTable={onChangeTable}
                    onDeleteTable={onDeleteTable}
                />}
                </Box>
            ))}
        </Box>
    )
}
