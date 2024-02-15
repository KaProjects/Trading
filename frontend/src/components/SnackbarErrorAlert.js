import {Alert, Snackbar} from "@mui/material";
import React from "react";


const SnackbarErrorAlert = props => {
    const {alert, open, onClose} = props

    return (
        <Snackbar
            open={open}
            autoHideDuration={6000}
            onClose={onClose}
            anchorOrigin={{ horizontal: 'center', vertical: 'bottom' }}
        >
            <Alert
                onClose={onClose}
                severity="error"
                variant="filled"
            >
                {alert}
            </Alert>
        </Snackbar>
    )
}
export default SnackbarErrorAlert

