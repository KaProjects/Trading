import {Stack, Typography} from "@mui/material";
import CalendarTodayIcon from "@mui/icons-material/CalendarToday";
import AccessTimeIcon from "@mui/icons-material/AccessTime";
import React from "react";

export const DateTime = ({value, sx, iconMarginTop = '0'}) => {
    if (value === null || value === undefined) return null;

    let [date, time] = value.split("T")
    let [year, month, day] = date.split("-")

    const typoSx = {fontSize: sx.fontSize, align: 'center', noWrap: true, variant: 'string'}
    const clockFontSize = typeof sx.fontSize === 'number'
        ? sx.fontSize + 1
        : Object.fromEntries(Object.entries(sx.fontSize).map(([breakpoint, size]) => [breakpoint, size + 1]))

    return <Stack sx={sx} direction="row" alignItems={"stretch"}>
        <CalendarTodayIcon sx={{fontSize: sx.fontSize, marginTop: iconMarginTop, marginRight: '0'}}/>
        <Typography sx={typoSx}>
            {day}.{month}.{year}
        </Typography>
        <AccessTimeIcon sx={{fontSize: clockFontSize, marginTop: iconMarginTop, marginRight: '1px'}}/>
        <Typography sx={typoSx}>
            {time}
        </Typography>
    </Stack>
}