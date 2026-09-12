import {TextField} from "@mui/material";
import React from "react";


export const DialogDatePicker = ({value, validate, warning, slotProps, ...props}) => {
    const validation = validate ? validate() : "";
    const showWarning = validation === "" && Boolean(warning);

    return (
        <TextField
            required
            margin="dense"
            fullWidth
            variant="standard"
            type="date"
            value={value}
            {...props}
            slotProps={{
                ...slotProps,
                inputLabel: {
                    ...slotProps?.inputLabel,
                    shrink: true,
                },
                ...(showWarning ? {formHelperText: {sx: {
                    color: "warning.main",
                    "&.Mui-disabled": {color: "warning.main"},
                }}} : {}),
            }}
            error={validate ? validation !== "" : value === ""}
            helperText={validation || warning || ""}
        />
    )
}
