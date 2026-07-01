import {Button, FormControl, FormHelperText, Input, InputAdornment, InputLabel, Typography} from "@mui/material";
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import React, {useEffect, useState} from "react";
import Tooltip from "@mui/material/Tooltip";

export const EditableValueBox = ({value, suffix, label, style, validate, update}) => {

    const [editing, setEditing] = useState(false)
    const [showValue, setShowValue] = useState(value ? value : "")
    const [editValue, setEditValue] = useState(value ? value : "")

    const [error, setError] = useState(null)

    useEffect(() => {
        setError(null)
        // eslint-disable-next-line
    }, [editValue, editing])

    async function handleUpdate() {
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
                <Tooltip
                    title={label}
                    placement="top"
                    slotProps={{popper: {modifiers: [{name: 'offset', options: {offset: [0, -13],},},],},}}
                >
                    <Button sx={{color: 'text.primary', borderRadius: 2, boxShadow: "1px 1px 1px lightgrey", border: "1px solid lightgrey", height: "25px", textTransform: 'none'}}
                            onClick={() => setEditing(true)}
                    >
                        {showValue && <Typography sx={{fontFamily: "Roboto",}}>{showValue}{suffix}</Typography>}
                        {!showValue && <ControlPointIcon sx={{color: 'lightgrey',}}/>}
                    </Button>
                </Tooltip>
            }
            {editing &&
                <FormControl
                    fullWidth sx={{ m: 1 }}
                    variant="standard"
                    className={error ? "blinking" : ""}
                    error={validate(editValue) !== "" || error !== null}
                >
                    <InputLabel htmlFor={"editable-" + label}>{label}</InputLabel>
                    <Input
                        id={"editable-" + label}
                        startAdornment={<InputAdornment position="start">{suffix}</InputAdornment>}
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
