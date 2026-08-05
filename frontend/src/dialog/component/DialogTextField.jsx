import {TextField} from "@mui/material";
import React from "react";


export const DialogTextField = ({validate, helperText, showValidationError = true, ...props}) => {
    const validation = validate ? validate() : "";

    return (
        <TextField
            required
            margin="dense"
            fullWidth
            variant="standard"
            {...props}
            error={showValidationError && validation !== ""}
            helperText={helperText ?? validation}
        />
    )
}
