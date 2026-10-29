import {
    Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, TextField, Tooltip,
} from "@mui/material";
import WarningAmberIcon from "@mui/icons-material/WarningAmber";
import React, {useEffect, useState} from "react";
import {ReactComponent as DeleteIcon} from "../assets/icons/delete.svg";

/**
 * The theme makes every dialog full screen on a phone, which suits an editor and not a short
 * list. This asks for the plain centred dialog back, inside the same media query so it wins.
 */
const COMPACT_PAPER = {
    margin: "24px",
    width: "calc(100% - 48px)",
    height: "auto",
    maxHeight: "calc(100% - 48px)",
    borderRadius: "4px",
};

const keyOf = company => (company.id === null || company.id === undefined
    ? `name:${company.ticker}`
    : `id:${company.id}`)

/**
 * The companies a case covers, taken off it or re-dated here rather than in the page. A company
 * added to a case is meant to stay in it - it is part of what the case was about, whether or
 * not it is still worth watching - so taking one off is deliberate, and says what it would cost
 * where the case has already traded in it.
 */
export const CaseCompaniesDialog = ({open, companies = [], tradeCounts = {}, onSubmit, handleClose}) => {
    const [rows, setRows] = useState([])

    useEffect(() => {if (open) setRows(companies.map(company => ({...company})))}, [open, companies])

    function submit() {
        onSubmit(rows.map(row => (row.id === null || row.id === undefined
            ? {name: row.ticker, addedAt: row.addedAt}
            : {id: row.id, addedAt: row.addedAt})))
        handleClose()
    }

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            fullWidth
            maxWidth="xs"
            slotProps={{paper: {sx: theme => ({
                ...COMPACT_PAPER,
                [theme.breakpoints.down("sm")]: COMPACT_PAPER,
            })}}}
        >
            <DialogTitle sx={{fontSize: 16}}>Companies</DialogTitle>
            <DialogContent>
                {rows.length === 0 &&
                    <Box sx={{fontSize: 13, color: "text.secondary"}}>The case covers no companies.</Box>
                }
                {rows.map(row => {
                    const traded = tradeCounts[row.ticker] ?? 0
                    return (
                        <Box key={keyOf(row)} data-testid={`case-company-${row.ticker}`}
                             sx={{display: "flex", alignItems: "center", gap: "6px", marginBottom: "6px"}}>
                            <Box sx={{
                                width: "72px",
                                fontWeight: 600,
                                fontSize: 13,
                                fontStyle: row.id ? "normal" : "italic",
                                overflow: "hidden",
                                textOverflow: "ellipsis",
                            }}>
                                {row.ticker}
                            </Box>
                            {/* The day it joined the case, which is the day its trades start
                                counting towards it. */}
                            <TextField
                                size="small"
                                type="date"
                                inputProps={{"aria-label": `Added ${row.ticker}`}}
                                value={row.addedAt ?? ""}
                                onChange={event => setRows(previous => previous.map(each =>
                                    keyOf(each) === keyOf(row)
                                        ? {...each, addedAt: event.target.value}
                                        : each))}
                                sx={{flex: 1, "& input": {fontSize: 12, padding: "6px 8px"}}}
                            />
                            {/* Taking it off does not take the trades with it - they happened -
                                but the case stops counting them, so it is worth saying. */}
                            <Tooltip title={traded > 0
                                ? `${traded} ${traded === 1 ? "trade" : "trades"} count towards the case`
                                : ""}>
                                <Box sx={{display: "flex", width: "18px"}}>
                                    {traded > 0 &&
                                        <WarningAmberIcon data-testid={`case-company-traded-${row.ticker}`}
                                                          sx={{fontSize: 16, color: "warning.main"}}/>
                                    }
                                </Box>
                            </Tooltip>
                            <Tooltip title="Take off the case">
                                <IconButton
                                    size="small"
                                    aria-label={`Remove ${row.ticker}`}
                                    onClick={() => setRows(previous =>
                                        previous.filter(each => keyOf(each) !== keyOf(row)))}
                                    sx={{padding: "2px"}}
                                >
                                    <DeleteIcon width={15} height={15}/>
                                </IconButton>
                            </Tooltip>
                        </Box>
                    )
                })}
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose}>Cancel</Button>
                <Button onClick={submit}>Save</Button>
            </DialogActions>
        </Dialog>
    )
}
