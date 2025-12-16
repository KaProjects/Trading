import {Alert, AlertTitle, Snackbar} from "@mui/material";
import React from "react";


const SnackbarErrorAlert = props => {
    const {error, open, onClose} = props

    return (
        <Snackbar
            open={open}
            onClose={() => {
                setTimeout(() => onClose(), 5000);
            }}
            anchorOrigin={{ horizontal: 'center', vertical: 'bottom' }}
        >
            {error &&
                <Alert onClose={onClose} severity="error" variant="filled">
                    <AlertTitle>{error.title}</AlertTitle>
                    {error.message}
                </Alert>
            }
        </Snackbar>
    )
}
export default SnackbarErrorAlert

