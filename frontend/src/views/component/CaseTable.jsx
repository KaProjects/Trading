import {Box, IconButton, ListItemText, Menu, MenuItem, TextField, Tooltip} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import CloseIcon from "@mui/icons-material/Close";
import DeleteIcon from "@mui/icons-material/DeleteOutline";
import React, {useState} from "react";
import {NUMERIC_FONT} from "./widgetLayout";

/** A cell's rating is picked from these; the blank one is "not rated". */
export const RATINGS = ["", "++", "+", "~", "-", "--"]

const blank = value => !value || !String(value).trim()

/**
 * Nothing has been typed into it. A table is created the moment it is asked for - it needs an
 * id for anything else to work - so one that stays like this was never really wanted, and is
 * dropped rather than saved.
 */
export const isEmptyTable = table => blank(table?.title)
    && (table?.columns ?? []).every(blank)
    && (table?.rows ?? []).every(row => blank(row.label)
        && (row.cells ?? []).every(cell => blank(cell.value) && blank(cell.rating)))

const RATING_COLOUR = {
    "++": "success.dark",
    "+": "success.main",
    "~": "text.disabled",
    "-": "error.main",
    "--": "error.dark",
}

const CELL = {
    borderBottom: "1px solid",
    borderRight: "1px solid",
    borderColor: "divider",
    padding: "1px 4px",
};

const INPUT = {
    "& .MuiInputBase-input": {fontSize: 12, padding: "1px 2px"},
    "& .MuiInput-underline:before": {borderBottom: "none"},
    "& .MuiInput-underline:hover:not(.Mui-disabled):before": {borderBottom: "1px dotted"},
};

/** The rating of one cell: clicked, then picked from the list rather than cycled through it. */
const RatingPicker = ({value, label, onPick}) => {
    const [anchor, setAnchor] = useState(null)

    return (
        <>
            <Box
                component="button"
                aria-label={label}
                onClick={event => setAnchor(event.currentTarget)}
                sx={{
                    ...NUMERIC_FONT,
                    fontSize: 11,
                    fontWeight: 700,
                    minWidth: "18px",
                    border: "none",
                    background: "transparent",
                    cursor: "pointer",
                    color: RATING_COLOUR[value] ?? "text.disabled",
                }}
            >
                {value || "\u00b7"}
            </Box>
            <Menu open={Boolean(anchor)} anchorEl={anchor} onClose={() => setAnchor(null)}>
                {RATINGS.map(rating => (
                    <MenuItem
                        key={rating || "none"}
                        selected={rating === (value ?? "")}
                        onClick={() => {setAnchor(null);onPick(rating)}}
                    >
                        <ListItemText primaryTypographyProps={{
                            fontSize: 13,
                            fontWeight: 700,
                            color: RATING_COLOUR[rating] ?? "text.disabled",
                        }}>
                            {rating || "none"}
                        </ListItemText>
                    </MenuItem>
                ))}
            </Menu>
        </>
    )
}

/**
 * A table typed into a day of a case: the companies across the top, a metric down the side, a
 * value in each cell and a rating beside it. The figures are typed rather than pulled from
 * anywhere - it is a snapshot of the day it was written, which is what makes it worth keeping.
 */
export const CaseTable = ({table, editable = false, onChange, onDelete}) => {
    const columns = table.columns ?? []
    const rows = table.rows ?? []

    function change(next) {
        onChange({title: table.title ?? "", columns: columns, rows: rows, ...next})
    }

    function changeCell(rowIndex, cellIndex, cell) {
        change({
            rows: rows.map((row, index) => index !== rowIndex ? row : {
                ...row,
                cells: row.cells.map((each, position) => position === cellIndex ? {...each, ...cell} : each),
            }),
        })
    }

    return (
        <Box data-testid={`case-table-${table.id}`} sx={{marginTop: "6px"}}>
            <Box sx={{display: "flex", alignItems: "center", gap: "4px"}}>
                {editable
                    ? <TextField
                        variant="standard"
                        placeholder="table"
                        inputProps={{"aria-label": "Table title"}}
                        sx={{...INPUT, maxWidth: "200px"}}
                        value={table.title ?? ""}
                        onChange={event => change({title: event.target.value})}
                    />
                    : <Box sx={{fontSize: 12, fontWeight: 600, color: "text.secondary"}}>{table.title}</Box>
                }
                {editable &&
                    <>
                        <Tooltip title="Add column">
                            <IconButton size="small" aria-label="Add column"
                                        onClick={() => change({
                                            columns: [...columns, ""],
                                            rows: rows.map(row => ({...row, cells: [...row.cells, {value: "", rating: ""}]})),
                                        })}>
                                <AddIcon sx={{fontSize: 14}}/>
                            </IconButton>
                        </Tooltip>
                        <Tooltip title="Add row">
                            <IconButton size="small" aria-label="Add row"
                                        onClick={() => change({
                                            rows: [...rows, {label: "", cells: columns.map(() => ({value: "", rating: ""}))}],
                                        })}>
                                <AddIcon sx={{fontSize: 14, transform: "rotate(90deg)"}}/>
                            </IconButton>
                        </Tooltip>
                        <Tooltip title="Delete table">
                            <IconButton size="small" aria-label="Delete table" onClick={onDelete}>
                                <DeleteIcon sx={{fontSize: 14}}/>
                            </IconButton>
                        </Tooltip>
                    </>
                }
            </Box>

            {/* A wide grid scrolls on its own; on a phone it would otherwise widen the page. */}
            <Box sx={{overflowX: "auto", maxWidth: "100%"}}>
            <Box component="table" sx={{borderCollapse: "collapse", fontSize: 12, marginTop: "2px"}}>
                <Box component="thead">
                    <Box component="tr">
                        <Box component="th" sx={{...CELL, borderLeft: "1px solid", borderColor: "divider"}}/>
                        {columns.map((column, index) => (
                            <Box component="th" key={index} sx={{...CELL, minWidth: "60px"}}>
                                {editable
                                    ? <Box sx={{display: "flex", alignItems: "center"}}>
                                        <TextField
                                            variant="standard"
                                            placeholder="ticker"
                                            inputProps={{"aria-label": `Column ${index + 1}`}}
                                            sx={INPUT}
                                            value={column}
                                            onChange={event => change({
                                                columns: columns.map((each, position) =>
                                                    position === index ? event.target.value : each),
                                            })}
                                        />
                                        <Tooltip title="Delete column">
                                            <IconButton size="small" aria-label={`Delete column ${index + 1}`}
                                                        onClick={() => change({
                                                            columns: columns.filter((each, position) => position !== index),
                                                            rows: rows.map(row => ({
                                                                ...row,
                                                                cells: row.cells.filter((each, position) => position !== index),
                                                            })),
                                                        })}>
                                                <CloseIcon sx={{fontSize: 12}}/>
                                            </IconButton>
                                        </Tooltip>
                                    </Box>
                                    : <Box sx={{fontWeight: 600}}>{column}</Box>
                                }
                            </Box>
                        ))}
                    </Box>
                </Box>
                <Box component="tbody">
                    {rows.map((row, rowIndex) => (
                        <Box component="tr" key={rowIndex}>
                            <Box component="td" sx={{...CELL, borderLeft: "1px solid", borderColor: "divider",
                                color: "text.secondary", whiteSpace: "nowrap"}}>
                                {editable
                                    ? <Box sx={{display: "flex", alignItems: "center"}}>
                                        <TextField
                                            variant="standard"
                                            placeholder="metric"
                                            inputProps={{"aria-label": `Row ${rowIndex + 1}`}}
                                            sx={INPUT}
                                            value={row.label ?? ""}
                                            onChange={event => change({
                                                rows: rows.map((each, index) =>
                                                    index === rowIndex ? {...each, label: event.target.value} : each),
                                            })}
                                        />
                                        <Tooltip title="Delete row">
                                            <IconButton size="small" aria-label={`Delete row ${rowIndex + 1}`}
                                                        onClick={() => change({
                                                            rows: rows.filter((each, index) => index !== rowIndex),
                                                        })}>
                                                <CloseIcon sx={{fontSize: 12}}/>
                                            </IconButton>
                                        </Tooltip>
                                    </Box>
                                    : row.label
                                }
                            </Box>
                            {row.cells.map((cell, cellIndex) => (
                                <Box component="td" key={cellIndex} sx={{...CELL, ...NUMERIC_FONT, textAlign: "right"}}>
                                    <Box sx={{display: "flex", alignItems: "baseline", gap: "3px"}}>
                                        {editable
                                            ? <TextField
                                                variant="standard"
                                                inputProps={{"aria-label": `Row ${rowIndex + 1} ${columns[cellIndex] || cellIndex + 1}`}}
                                                sx={INPUT}
                                                value={cell.value ?? ""}
                                                onChange={event =>
                                                    changeCell(rowIndex, cellIndex, {value: event.target.value})}
                                            />
                                            : <Box sx={{flex: 1}}>{cell.value}</Box>
                                        }
                                        {editable
                                            ? <RatingPicker
                                                value={cell.rating}
                                                label={`Rating ${rowIndex + 1} ${columns[cellIndex] || cellIndex + 1}`}
                                                onPick={rating => changeCell(rowIndex, cellIndex, {rating: rating})}
                                            />
                                            : <Box component="span" sx={{
                                                ...NUMERIC_FONT,
                                                fontSize: 11,
                                                fontWeight: 700,
                                                minWidth: "18px",
                                                color: RATING_COLOUR[cell.rating] ?? "text.disabled",
                                            }}>
                                                {cell.rating}
                                            </Box>
                                        }
                                    </Box>
                                </Box>
                            ))}
                        </Box>
                    ))}
                </Box>
            </Box>
            </Box>
        </Box>
    )
}
