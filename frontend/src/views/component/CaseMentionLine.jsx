import {Box} from "@mui/material";
import React from "react";
import {NUMERIC_FONT} from "./widgetLayout";

/** What each kind of mention is called where the case reads it. */
const SAID = {
    RECORD: "new record",
    RESEARCH: "new research",
    REPORT: "reported",
};

/**
 * Something of a company's own on a day the case covers it - a record written, research added
 * to a period, earnings reported. The case does not hold any of it; the line opens it to be
 * read where it was written.
 */
export const CaseMentionLine = ({mention, onOpen}) => (
    <Box data-testid={`case-mention-${mention.kind}-${mention.id}`}
         sx={{...NUMERIC_FONT, display: "flex", alignItems: "baseline", gap: "6px", fontSize: 13}}>
        <Box component="span" sx={{fontWeight: 600, color: "text.secondary"}}>{mention.ticker}</Box>
        <Box
            component="span"
            role="button"
            aria-label={`Open ${mention.ticker} ${SAID[mention.kind] ?? mention.kind}`}
            onClick={onOpen}
            sx={{
                color: "text.secondary",
                cursor: "pointer",
                "&:hover": {textDecoration: "underline"},
            }}
        >
            {SAID[mention.kind] ?? mention.kind}
        </Box>
    </Box>
);
