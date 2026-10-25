import {Box, ButtonBase} from "@mui/material";
import React from "react";
import {formatTargetStats} from "../../service/FormattingService";
import {NUMERIC_FONT} from "./widgetLayout";

export const PeriodTargetSummary = ({stats, currency, onOpen}) => {
    const summary = formatTargetStats(stats);
    if (!summary) return null;

    return (
        <ButtonBase
            data-testid="period-target-summary"
            aria-label="Targets"
            onClick={onOpen}
            disabled={!onOpen}
            /* Set like the reported financials beside it: the label in the secondary colour, the
               figures in the primary one with tabular digits. */
            sx={{
                ...NUMERIC_FONT,
                display: "flex",
                justifyContent: "flex-start",
                color: "text.primary",
                fontSize: 13,
            }}
        >
            <Box component="span" sx={{color: "text.secondary"}}>Targets:&nbsp;</Box>
            {summary}{currency}
        </ButtonBase>
    );
};
