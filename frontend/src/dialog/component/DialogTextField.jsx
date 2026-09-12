import {TextField} from "@mui/material";
import React from "react";


export const DialogTextField = ({validate, warning, helperText, showValidationError = true, ...props}) => {
    const validation = validate ? validate() : "";
    const showWarning = validation === "" && Boolean(warning);

    return (
        <TextField
            required
            margin="dense"
            fullWidth
            variant="standard"
            {...props}
            error={showValidationError && validation !== ""}
            helperText={helperText ?? (validation || warning || "")}
            slotProps={showWarning
                ? {formHelperText: {sx: {
                    color: "warning.main",
                    "&.Mui-disabled": {color: "warning.main"},
                }}}
                : undefined}
        />
    )
}
