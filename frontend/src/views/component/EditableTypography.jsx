import React, {useEffect, useState} from "react";
import {FormControl, FormHelperText, Input, InputLabel, Typography} from "@mui/material";
import "../../style/Blinking.css";

export const EditableTypography = ({value, label, validate, update, style}) => {

    const [editing, setEditing] = useState(false)
    const [showValue, setShowValue] = useState(value ? value : "")
    const [editValue, setEditValue] = useState(value ? value : "")

    const [error, setError] = useState(null)

    useEffect(() => {
        setError(null)
        // eslint-disable-next-line
    }, [editValue, editing])

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
        <div style={style}>
            {!editing &&
                <Typography
                    sx={{color: 'text.primary', fontWeight: 'medium', fontSize: 20, }}
                    onClick={() => setEditing(true)}
                >
                    {showValue}
                </Typography>
            }
            {editing &&
                <FormControl
                    fullWidth sx={{ m: 1 }}
                    variant="standard"
                    className={error ? "blinking" : ""}
                    error={validate(editValue) !== "" || error !== null}
                >
                    <InputLabel htmlFor={"editable-" + label}>Title</InputLabel>
                    <Input
                        id={"editable-" + label}
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
        </div>
    )
}