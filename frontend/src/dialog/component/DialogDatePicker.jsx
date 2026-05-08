import {TextField} from "@mui/material";
import React from "react";


export const DialogDatePicker = ({value, validate, ...props}) => {
    return (
        <TextField
            required
            margin="dense"
            fullWidth
            variant="standard"
            type="date"
            value={value}
            {...props}
            error={validate ? validate() !== "" : value === ""}
            helperText={validate ? validate() : ""}
        />
    )
}
