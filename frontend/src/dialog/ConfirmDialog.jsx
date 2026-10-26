import {Button, Dialog, DialogActions, DialogContent, DialogTitle} from "@mui/material";
import React from "react";

/**
 * The theme makes every dialog full screen on a phone, which suits an editor and not a
 * question. This asks for the plain centred dialog back, inside the same media query so it wins.
 */
const COMPACT_PAPER = {
    margin: "24px",
    width: "calc(100% - 48px)",
    height: "auto",
    maxHeight: "calc(100% - 48px)",
    borderRadius: "4px",
};

/** A question asked before something that cannot be taken back. */
export const ConfirmDialog = ({open, title, message, confirmLabel = "Delete", onConfirm, handleClose}) => (
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
        <DialogContent sx={{fontSize: 13}}>{message}</DialogContent>
        <DialogActions>
            <Button onClick={handleClose}>Cancel</Button>
            <Button
                color="error"
                onClick={() => {onConfirm();handleClose()}}
            >
                {confirmLabel}
            </Button>
        </DialogActions>
    </Dialog>
);
