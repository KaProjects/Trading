import {Button, Typography} from "@mui/material";
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import React from "react";
import Tooltip from "@mui/material/Tooltip";

const EditableValueBox = props => {

    const {value, suffix, label} = props

    const style = {color: 'text.primary', borderRadius: 2, boxShadow: "1px 1px 1px lightgrey", border: "1px solid lightgrey", height: "25px",}

    return (
        <div style={props.style}>
            <Tooltip
                title={label}
                placement="top"
                slotProps={{popper: {modifiers: [{name: 'offset', options: {offset: [0, -13],},},],},}}
            >
                <Button sx={style}>
                    {value && <Typography sx={{fontFamily: "Roboto",}}>{value}{suffix}</Typography>}
                    {!value && <ControlPointIcon sx={{color: 'lightgrey',}}/>}
                </Button>
            </Tooltip>
        </div>
    )
}
export default EditableValueBox;