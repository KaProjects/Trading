import React from "react";
import {Box, IconButton, Tab, Tabs} from "@mui/material";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import useMediaQuery from "@mui/material/useMediaQuery";

export const COMPACT_TABS_BREAKPOINT = 700

const ARROW_SX = {
    color: "inherit",
    padding: 0,
    flexShrink: 0,
    "&.Mui-disabled": {visibility: "hidden"},
}

export const MainBarTabs = ({labels, value, setValue}) => {
    const compact = useMediaQuery(`(max-width:${COMPACT_TABS_BREAKPOINT}px)`)

    if (!compact) {
        return (
            <Tabs
                value={value}
                onChange={(event, next) => setValue(next)}
                slotProps={{indicator: {style: {backgroundColor: "white"}}}}
                textColor="inherit"
                variant="scrollable"
                scrollButtons={false}
                sx={{"& .MuiTab-root": {minWidth: 90}}}
            >
                {labels.map(label => <Tab key={label} label={label}/>)}
            </Tabs>
        )
    }

    return (
        <Box sx={{display: "flex", alignItems: "center", flexShrink: 0, marginRight: "3px"}}>
            <IconButton
                size="small"
                sx={ARROW_SX}
                disabled={value <= 0}
                onClick={() => setValue(value - 1)}
                aria-label="previous tab"
            >
                <ChevronLeftIcon fontSize="small"/>
            </IconButton>
            <Box component="span" sx={{
                fontSize: "0.875rem",
                fontWeight: 500,
                textTransform: "uppercase",
                whiteSpace: "nowrap",
            }}>
                {labels[value] ?? ""}
            </Box>
            <IconButton
                size="small"
                sx={ARROW_SX}
                disabled={value >= labels.length - 1}
                onClick={() => setValue(value + 1)}
                aria-label="next tab"
            >
                <ChevronRightIcon fontSize="small"/>
            </IconButton>
        </Box>
    )
}
