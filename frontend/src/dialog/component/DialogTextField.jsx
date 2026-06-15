import {TextField} from "@mui/material";
import React from "react";


export const DialogTextField = ({validate, ...props}) => {
    return (
        <TextField
            required
            margin="dense"
            fullWidth
            variant="standard"
            {...props}
            error={validate() !== ""}
            helperText={validate()}
        />
    )
}
