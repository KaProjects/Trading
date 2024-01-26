import React, {useState} from "react";
import {domain} from "../properties";
import axios from "axios";
import {FormControl, FormHelperText, Input, InputLabel, Typography} from "@mui/material";
import SnackbarErrorAlert from "./SnackbarErrorAlert";


const EditableTypography = props => {

    const {index, value, label} = props

    const style = {color: 'text.primary', fontWeight: 'medium', fontSize: 20, }

    const [editing, setEditing] = useState(false);
    const [showValue, setShowValue] = useState(value ? value : "");
    const [editValue, setEditValue] = useState(value ? value : "");

    const [alert, setAlert] = useState(null);

    function handleUnFocus() {
        setEditing(false)
        axios.put(domain + "/record", props.updateObject(editValue))
            .then((response) => {
                setShowValue(editValue)
                props.handleUpdate(editValue)
            }).catch((error) => {
            console.error(error)
            setAlert(error.response.data)
            setEditValue(showValue)
        })
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
                                 error={props.validateInput(editValue) !== ""}
                    >
                        <InputLabel htmlFor={"editable-" + label + "-" + index}>Title</InputLabel>
                        <Input
                            id={"editable-" + label + "-" + index}
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
                <SnackbarErrorAlert alert={alert} open={alert !== null} onClose={() => setAlert(null)}/>
            </>
        </div>
    )
}
export default EditableTypography;