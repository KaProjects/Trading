import React from "react";
import {Typography} from "@mui/material";
import {Editable} from "./Editable";

export const EditableTypography = ({value, label, validate, update, style}) => {

    return (
        <Editable value={value} label={label} validate={validate} update={update} style={style}>
            {({showValue, setEditing}) =>
                <Typography
                    sx={{color: 'text.primary', fontWeight: 'medium', fontSize: 20, }}
                    onClick={() => setEditing(true)}
                >
                    {showValue}
                </Typography>
            }
        </Editable>
    )
}
