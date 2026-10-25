import {Box, IconButton, TextField, Tooltip} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/DeleteOutline";
import React from "react";
import {NUMERIC_FONT} from "./widgetLayout";

/** A cell's rating cycles through these; the blank one is "not rated". */
export const RATINGS = ["", "++", "+", "~", "-", "--"]

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

const nextRating = rating => RATINGS[(RATINGS.indexOf(rating ?? "") + 1) % RATINGS.length]

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

            <Box component="table" sx={{borderCollapse: "collapse", fontSize: 12, marginTop: "2px"}}>
                <Box component="thead">
                    <Box component="tr">
                        <Box component="th" sx={{...CELL, borderLeft: "1px solid", borderColor: "divider"}}/>
                        {columns.map((column, index) => (
                            <Box component="th" key={index} sx={{...CELL, minWidth: "60px"}}>
                                {editable
                                    ? <TextField
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
                                    ? <TextField
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
                                        <Box
                                            component={editable ? "button" : "span"}
                                            aria-label={editable
                                                ? `Rating ${rowIndex + 1} ${columns[cellIndex] || cellIndex + 1}`
                                                : undefined}
                                            onClick={editable
                                                ? () => changeCell(rowIndex, cellIndex, {rating: nextRating(cell.rating)})
                                                : undefined}
                                            sx={{
                                                ...NUMERIC_FONT,
                                                fontSize: 11,
                                                fontWeight: 700,
                                                minWidth: "18px",
                                                border: "none",
                                                background: "transparent",
                                                cursor: editable ? "pointer" : "default",
                                                color: RATING_COLOUR[cell.rating] ?? "text.disabled",
                                            }}
                                        >
                                            {cell.rating || (editable ? "·" : "")}
                                        </Box>
                                    </Box>
                                </Box>
                            ))}
                        </Box>
                    ))}
                </Box>
            </Box>
        </Box>
    )
}
