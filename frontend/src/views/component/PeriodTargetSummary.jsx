import {Typography} from "@mui/material";
import React from "react";
import {formatTargetStats} from "../../service/FormattingService";

export const PeriodTargetSummary = ({stats, currency}) => {
    const summary = formatTargetStats(stats);
    if (!summary) return null;

    return (
        <Typography data-testid="period-target-summary" sx={{color: "text.secondary", fontSize: 14}}>
            Targets: {summary}{currency}
        </Typography>
    );
};
