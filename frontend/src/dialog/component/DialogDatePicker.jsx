import {TextField} from "@mui/material";
import React from "react";


export const DialogDatePicker = ({value, ...props}) => {
    return (
        <TextField
            required
            margin="dense"
            fullWidth
            variant="standard"
            type="date"
            value={value}
            {...props}
            error={value === ""}
        />
    )
}
