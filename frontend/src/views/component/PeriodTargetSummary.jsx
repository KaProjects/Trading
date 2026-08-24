import {Typography} from "@mui/material";
import React from "react";
import {formatDecimals} from "../../service/FormattingService";

const formatTarget = (value) => value < 10
    ? formatDecimals(value, 1, 1)
    : formatDecimals(value, 0, 0);

export const PeriodTargetSummary = ({stats, currency}) => {
    if (!stats || stats.count < 1) return null;

    return (
        <Typography data-testid="period-target-summary" sx={{color: "text.secondary", fontSize: 14}}>
            Targets: {stats.count}@({formatTarget(stats.maximum)}-{formatTarget(stats.minimum)})~{formatTarget(stats.average)}{currency}
        </Typography>
    );
};
