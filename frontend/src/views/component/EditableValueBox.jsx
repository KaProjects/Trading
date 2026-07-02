import {Button, InputAdornment, Typography} from "@mui/material";
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import React from "react";
import Tooltip from "@mui/material/Tooltip";
import {Editable} from "./Editable";

export const EditableValueBox = ({value, suffix, label, style, validate, update}) => {

    return (
        <Editable
            value={value}
            label={label}
            validate={validate}
            update={update}
            style={style}
            startAdornment={<InputAdornment position="start">{suffix}</InputAdornment>}
        >
            {({showValue, setEditing}) =>
                <Tooltip
                    title={label}
                    placement="top"
                    slotProps={{popper: {modifiers: [{name: 'offset', options: {offset: [0, -13],},},],},}}
                >
                    <Button sx={{color: 'text.primary', borderRadius: 2, boxShadow: "1px 1px 1px #eeeeee", border: "1px solid #eeeeee", height: "25px", textTransform: 'none'}}
                            onClick={() => setEditing(true)}
                    >
                        {showValue && <Typography sx={{fontFamily: "Roboto",}}>{showValue}{suffix}</Typography>}
                        {!showValue && <ControlPointIcon sx={{color: '#eeeeee',}}/>}
                    </Button>
                </Tooltip>
            }
        </Editable>
    )
}
