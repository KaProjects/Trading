import {Button, InputAdornment, Typography} from "@mui/material";
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import React from "react";
import Tooltip from "@mui/material/Tooltip";
import {Editable} from "./Editable";

export const EditableValueBox = ({value, suffix, label, style, valueStyle, formatValue, validate, update, secondary}) => {

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
                    <Button sx={{
                                color: 'text.primary',
                                borderRadius: 2,
                                boxShadow: "1px 1px 1px #eeeeee",
                                border: "1px solid #eeeeee",
                                height: secondary ? "auto" : "25px",
                                minHeight: "25px",
                                py: secondary ? 0.25 : 0,
                                flexDirection: "column",
                                lineHeight: 1.2,
                                textTransform: 'none',
                            }}
                            onClick={() => setEditing(true)}
                    >
                        {showValue &&
                            <Typography sx={{fontFamily: "Roboto", ...valueStyle}}>
                                {formatValue ? formatValue(showValue) : showValue}{suffix}
                            </Typography>
                        }
                        {!showValue && <ControlPointIcon sx={{color: '#eeeeee',}}/>}
                        {secondary}
                    </Button>
                </Tooltip>
            }
        </Editable>
    )
}
