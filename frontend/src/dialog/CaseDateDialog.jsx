import {Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField} from "@mui/material";
import React, {useEffect, useState} from "react";
/**
 * The theme makes every dialog full screen on a phone, which suits an editor and not a single
 * field. This asks for the plain centred dialog back, inside the same media query so it wins.
 */
const COMPACT_PAPER = {
    margin: "24px",
    width: "calc(100% - 48px)",
    height: "auto",
    maxHeight: "calc(100% - 48px)",
    borderRadius: "4px",
};


/**
 * The day something happened on. A case and its days are written forwards, but one kept from
 * the past is written down after the fact - so the dates can be put right, here rather than in
 * the page, because changing a date is deliberate.
 */
export const CaseDateDialog = ({open, title, initialDate = "", onSubmit, handleClose}) => {
    const [date, setDate] = useState(initialDate)

    useEffect(() => {if (open) setDate(initialDate)}, [open, initialDate])

    function submit() {
        if (!date) return

        onSubmit(date)
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
            <DialogTitle sx={{fontSize: 16}}>{title}</DialogTitle>
            <DialogContent>
                {/* No label here either: the dialog title says which day this is. */}
                <TextField
                    autoFocus
                    fullWidth
                    size="small"
                    type="date"
                    inputProps={{"aria-label": "Date"}}
                    value={date}
                    onChange={event => setDate(event.target.value)}
                    onKeyDown={event => {if (event.key === "Enter") submit()}}
                />
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose}>Cancel</Button>
                <Button onClick={submit} disabled={!date}>Save</Button>
            </DialogActions>
        </Dialog>
    )
}
