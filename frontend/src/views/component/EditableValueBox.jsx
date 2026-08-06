import {Button, InputAdornment, Typography} from "@mui/material";
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import React from "react";
import Tooltip from "@mui/material/Tooltip";
import {Editable} from "./Editable";

export const EditableValueBox = ({
    value,
    prefix,
    suffix,
    label,
    style,
    valueStyle,
    formatValue,
    validate,
    update,
    secondary,
    disabled = false,
}) => {

    return (
        <Editable
            value={value}
            label={label}
            validate={validate}
            update={update}
            style={style}
            startAdornment={(prefix || suffix)
                ? <InputAdornment position="start">{prefix || suffix}</InputAdornment>
                : undefined
            }
        >
            {({showValue, setEditing}) =>
                <Tooltip
                    title={label}
                    placement="top"
                    slotProps={{popper: {modifiers: [{name: 'offset', options: {offset: [0, -13],},},],},}}
                >
                    <Button sx={{
                                color: 'text.secondary',
                                borderRadius: 2,
                                boxShadow: "1px 1px 1px rgba(0, 0, 0, 0.025)",
                                border: "1px solid rgba(0, 0, 0, 0.04)",
                                height: secondary ? "auto" : "25px",
                                minHeight: "25px",
                                py: secondary ? 0.25 : 0,
                                flexDirection: "column",
                                lineHeight: 1.2,
                                textTransform: 'none',
                                cursor: disabled ? "default" : "pointer",
                            }}
                            aria-disabled={disabled}
                            onClick={() => {
                                if (!disabled) setEditing(true);
                            }}
                    >
                        {showValue &&
                            <Typography sx={{fontFamily: "Roboto", ...valueStyle}}>
                                {prefix}{formatValue ? formatValue(showValue) : showValue}{suffix}
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
