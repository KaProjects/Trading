import React, {useEffect, useState} from "react";
import {Box, FormControl, FormHelperText, Input, InputLabel} from "@mui/material";
import "../../style/Blinking.css";

export const Editable = ({value, label, validate, update, style, startAdornment, children}) => {

    const [editing, setEditing] = useState(false)
    const [showValue, setShowValue] = useState(value ? value : "")
    const [editValue, setEditValue] = useState(value ? value : "")

    const [error, setError] = useState(null)

    useEffect(() => {
        setError(null)
    }, [editValue, editing])

    useEffect(() => {
        const newValue = value ?? "";
        setShowValue(newValue);
        setEditValue(newValue);
        setEditing(false);
        setError(null);
    }, [value])

    async function handleUpdate()
    {
        if (showValue !== editValue) {
            const error = await update(editValue);
            if (error) {
                setError(error)
            } else {
                setShowValue(editValue)
                setEditing(false)
            }
        } else {
            setEditing(false)
        }
    }

    return (
        <Box sx={style}>
            {!editing && children({showValue, setEditing})}
            {editing &&
                <FormControl
                    fullWidth sx={{m: 1}}
                    variant="standard"
                    className={error ? "blinking" : ""}
                    error={validate(editValue) !== "" || error !== null}
                >
                    <InputLabel htmlFor={"editable-" + label}>{label}</InputLabel>
                    <Input
                        id={"editable-" + label}
                        startAdornment={startAdornment}
                        value={editValue}
                        onChange={(e) => {setEditValue(e.target.value);setError(null);}}
                        autoFocus
                        onBlur={handleUpdate}
                        onKeyDown={e => {
                            if(e.key === 'Enter') handleUpdate()
                            if(e.key === 'Escape') {
                                setEditValue(showValue);
                                const target = e.currentTarget;
                                setTimeout(() => target.blur(), 100);
                            }
                        }}
                    />
                    {validate(editValue) &&
                        <FormHelperText id={"editable-" + label}>{validate(editValue)}</FormHelperText>
                    }
                </FormControl>
            }
        </Box>
    )
}
