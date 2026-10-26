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
 * The one field a case cannot do without. It names a new case before it exists, and renames an
 * existing one; either way the name is what the case is known by, so it is asked for up front
 * rather than typed into the page.
 */
export const CaseNameDialog = ({open, title, initialName = "", onSubmit, handleClose}) => {
    const [name, setName] = useState(initialName)

    useEffect(() => {if (open) setName(initialName)}, [open, initialName])

    function submit() {
        if (!name.trim()) return

        onSubmit(name.trim())
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
                {/* No label: the dialog title already says what is being named. */}
                <TextField
                    autoFocus
                    fullWidth
                    size="small"
                    inputProps={{"aria-label": "Case name"}}
                    value={name}
                    onChange={event => setName(event.target.value)}
                    onKeyDown={event => {if (event.key === "Enter") submit()}}
                />
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose}>Cancel</Button>
                <Button onClick={submit} disabled={!name.trim()}>Save</Button>
            </DialogActions>
        </Dialog>
    )
}
