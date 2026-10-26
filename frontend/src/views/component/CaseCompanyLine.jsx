import {Box} from "@mui/material";
import React from "react";
import {NUMERIC_FONT} from "./widgetLayout";

/**
 * The day a company was taken into the case, read in the stream with the trades. A case grows
 * as it is thought about, and when a company joined it is part of what happened - it is also
 * the day its trades start counting towards the case.
 */
export const CaseCompanyLine = ({ticker}) => (
    <Box data-testid={`case-company-added-${ticker}`}
         sx={{...NUMERIC_FONT, display: "flex", alignItems: "baseline", gap: "6px", fontSize: 13}}>
        <Box component="span" sx={{fontWeight: 600, color: "text.secondary"}}>{ticker}</Box>
        <Box component="span" sx={{color: "text.secondary"}}>added to the case</Box>
    </Box>
);
