import {Button, Dialog, DialogActions, DialogContent, DialogTitle} from "@mui/material";
import React from "react";
import {FinancialsTable} from "../views/component/PeriodFinancials";

export const FinancialsDialog = ({open, handleClose, ticker, financials}) => (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="lg">
        <DialogTitle>{ticker} - Financials</DialogTitle>
        <DialogContent dividers sx={{display: "flex", minHeight: 0, overflow: "hidden", padding: 0}}>
            <FinancialsTable financials={financials} fontSize={16} scrollable/>
        </DialogContent>
        <DialogActions>
            <Button onClick={handleClose}>Close</Button>
        </DialogActions>
    </Dialog>
);
