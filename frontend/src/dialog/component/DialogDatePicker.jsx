import {TextField} from "@mui/material";
import React from "react";


export const DialogDatePicker = ({value, validate, slotProps, ...props}) => {
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
            }}
            error={validate ? validate() !== "" : value === ""}
            helperText={validate ? validate() : ""}
        />
    )
}
