import React, {useEffect, useState} from "react";
import {FormControl, FormHelperText, Input, InputLabel, Typography} from "@mui/material";
import "../style/Blinking.css";

const EditableTypography = props => {

    const {index, value, label} = props

    const style = {color: 'text.primary', fontWeight: 'medium', fontSize: 20, }

    const [editing, setEditing] = useState(false)
    const [showValue, setShowValue] = useState(value ? value : "")
    const [editValue, setEditValue] = useState(value ? value : "")

    const [error, setError] = useState(null)
    const [disabled, setDisabled] = useState(false)

    useEffect(() => {
        if (!editing) {
            setDisabled(false)
            setError(null)
        }
        // eslint-disable-next-line
    }, [editing])

    useEffect(() => {
        setError(null)
        // eslint-disable-next-line
    }, [editValue])

    async function handleUpdate(unfocused)
    {
        const error = await props.updateObject(editValue);

        if (error) {
            setError(error)
            if (unfocused) {
                setDisabled(true)
                setTimeout(() => {
                    setEditing(false)
                    setEditValue(showValue)
                }, 1000);
            }
        } else {
            setEditing(false)
            setShowValue(editValue)
        }
    }

    return (
        <div style={props.style}>
            <>
                {!editing &&
                    <Typography sx={style} onClick={() => setEditing(true)}>
                        {showValue}
                    </Typography>
                }
                {editing &&
                    <FormControl fullWidth sx={{ m: 1 }} variant="standard"
                                 className={error ? "blinking" : ""}
                                 error={props.validateInput(editValue) !== "" || error !== null}
                    >
                        <InputLabel htmlFor={"editable-" + label + "-" + index}>Title</InputLabel>
                        <Input
                            disabled={disabled}
                            id={"editable-" + label + "-" + index}
                            value={editValue}
                            onChange={(e) => setEditValue(e.target.value)}
                            autoFocus
                            onBlur={() => handleUpdate(true)}
                            onKeyDown={e => {
                                if(e.key === 'Enter') handleUpdate(false)
                                if(e.key === 'Escape') {setEditing(false);setEditValue(showValue);}
                            }}
                        />
                        {props.validateInput(editValue) &&
                            <FormHelperText id={"editable-" + label + "-" + index}>{props.validateInput(editValue)}</FormHelperText>
                        }
                    </FormControl>
                }
            </>
        </div>
    )
}
export default EditableTypography