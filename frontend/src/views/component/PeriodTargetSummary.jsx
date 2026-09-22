import {ButtonBase} from "@mui/material";
import React from "react";
import {formatTargetStats} from "../../service/FormattingService";

export const PeriodTargetSummary = ({stats, currency, onOpen}) => {
    const summary = formatTargetStats(stats);
    if (!summary) return null;

    return (
        <ButtonBase
            data-testid="period-target-summary"
            aria-label="Targets"
            onClick={onOpen}
            disabled={!onOpen}
            sx={{
                display: "flex",
                justifyContent: "flex-start",
                color: "text.secondary",
                fontSize: 14,
            }}
        >
            Targets: {summary}{currency}
        </ButtonBase>
    );
};
