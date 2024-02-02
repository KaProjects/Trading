import {Button, FormControl, FormHelperText, Input, InputAdornment, InputLabel, Typography} from "@mui/material";
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import React, {useState} from "react";
import Tooltip from "@mui/material/Tooltip";
import {domain} from "../properties";
import axios from "axios";
import SnackbarErrorAlert from "./SnackbarErrorAlert";
import {handleError} from "../utils";

const EditableValueBox = props => {

    const {index, value, suffix, label} = props

    const style = {color: 'text.primary', borderRadius: 2, boxShadow: "1px 1px 1px lightgrey", border: "1px solid lightgrey", height: "25px", textTransform: 'none'}

    const [editing, setEditing] = useState(false)
    const [showValue, setShowValue] = useState(value ? value : "")
    const [editValue, setEditValue] = useState(value ? value : "")

    const [alert, setAlert] = useState(null)

    function handleUnFocus() {
        setEditing(false)
        axios.put(domain + "/record", props.updateObject(editValue))
            .then((response) => {
                setShowValue(editValue)
                props.handleUpdate(editValue)
            }).catch((error) => {
                setAlert(handleError(error))
                setEditValue(showValue)
            })
    }

    return (
        <div style={props.style}>
            <>
            {!editing &&
                <Tooltip
                    title={label}
                    placement="top"
                    slotProps={{popper: {modifiers: [{name: 'offset', options: {offset: [0, -13],},},],},}}
                >
                    <Button sx={style} onClick={() => setEditing(true)}>
                        {showValue && <Typography sx={{fontFamily: "Roboto",}}>{showValue}{suffix}</Typography>}
                        {!showValue && <ControlPointIcon sx={{color: 'lightgrey',}}/>}
                    </Button>
                </Tooltip>
            }
            {editing &&
                <FormControl fullWidth sx={{ m: 1 }} variant="standard"
                             error={props.validateInput(editValue) !== ""}
                >
                    <InputLabel htmlFor={"editable-" + label + "-" + index}>{label}</InputLabel>
                    <Input
                        id={"editable-" + label + "-" + index}
                        startAdornment={<InputAdornment position="start">{suffix}</InputAdornment>}
                        value={editValue}
                        onChange={(e) => setEditValue(e.target.value)}
                        autoFocus
                        onBlur={handleUnFocus}
                        onKeyDown={e => {if(e.key === 'Enter') handleUnFocus()}}
                    />
                    {props.validateInput(editValue) &&
                        <FormHelperText id={"editable-" + label + "-" + index}>{props.validateInput(editValue)}</FormHelperText>
                    }
                </FormControl>
            }
            </>
            <SnackbarErrorAlert alert={alert} open={alert !== null} onClose={() => setAlert(null)}/>
        </div>
    )
}
export default EditableValueBox;