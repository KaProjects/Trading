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

    useEffect(() => {
        setError(null)
        // eslint-disable-next-line
    }, [editValue, editing])

    async function handleUpdate()
    {
        if (showValue !== editValue) {
            const error = await props.updateObject(editValue);
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
                            id={"editable-" + label + "-" + index}
                            value={editValue}
                            onChange={(e) => setEditValue(e.target.value)}
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