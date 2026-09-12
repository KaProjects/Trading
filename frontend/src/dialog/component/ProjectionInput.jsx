import {Box, IconButton, InputBase} from "@mui/material";
import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import ArrowDropUpIcon from "@mui/icons-material/ArrowDropUp";
import React from "react";

export const ProjectionInput = ({value, onChange, onBlur, onStep, label, min}) => (
    <InputBase
        type="number"
        value={value}
        onChange={onChange}
        onBlur={onBlur}
        onKeyDown={event => {
            if (event.key === "ArrowUp" || event.key === "ArrowDown") {
                event.preventDefault();
                onStep(event.key === "ArrowUp" ? 1 : -1);
            }
        }}
        endAdornment={
            <Box
                className="projection-stepper"
                sx={{
                    display: "flex",
                    flexDirection: "column",
                    opacity: 0,
                    pointerEvents: "none",
                }}
            >
                <IconButton
                    aria-label={`Increase ${label.toLowerCase()}`}
                    onMouseDown={event => event.preventDefault()}
                    onClick={() => onStep(1)}
                    tabIndex={-1}
                    sx={{width: 18, height: 11, padding: 0, color: "#555"}}
                >
                    <ArrowDropUpIcon sx={{fontSize: 17}}/>
                </IconButton>
                <IconButton
                    aria-label={`Decrease ${label.toLowerCase()}`}
                    onMouseDown={event => event.preventDefault()}
                    onClick={() => onStep(-1)}
                    tabIndex={-1}
                    sx={{width: 18, height: 11, padding: 0, color: "#555"}}
                >
                    <ArrowDropDownIcon sx={{fontSize: 17}}/>
                </IconButton>
            </Box>
        }
        inputProps={{inputMode: "decimal", min, step: "any", "aria-label": label}}
        sx={{
            width: "100%",
            color: "#111",
            fontSize: "inherit",
            "& input": {padding: "4px 2px 4px 8px", textAlign: "right"},
            "& input::-webkit-outer-spin-button, & input::-webkit-inner-spin-button": {
                WebkitAppearance: "none",
                margin: 0,
            },
            "& input[type=number]": {MozAppearance: "textfield"},
            "&:focus-within .projection-stepper": {opacity: 1, pointerEvents: "auto"},
        }}
    />
);
