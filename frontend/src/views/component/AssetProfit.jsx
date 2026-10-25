import {Box} from "@mui/material";
import React from "react";
import {formatDecimals} from "../../service/FormattingService";

export function profitColor(value) {
    if (Number(value) > 0) return "success.dark";
    if (Number(value) < 0) return "error.dark";
    return "text.primary";
}

export function formatProfitPercent(value) {
    if (value === null || value === undefined || isNaN(Number(value))) return "-";
    const formatted = formatDecimals(value, 0, 2);
    return `${Number(value) > 0 ? "+" : ""}${formatted}%`;
}

export function formatProfitValue(value, currency) {
    if (value === null || value === undefined || isNaN(Number(value))) return "-";
    const number = Number(value);
    const formatted = formatDecimals(Math.abs(number), 0, 2);
    const sign = number > 0 ? "+" : number < 0 ? "-" : "";
    return `${sign}${formatted}${currency}`;
}

export const AssetProfit = ({value, percent, currency, fontSize = 14, valueWidth, percentWidth, testId, percentTestId, sx}) => (
    <Box
        data-testid={testId}
        sx={{
            display: "flex",
            justifyContent: "flex-end",
            gap: "4px",
            color: profitColor(value),
            opacity: 0.78,
            fontWeight: 500,
            fontSize,
            whiteSpace: "nowrap",
            ...sx,
        }}
    >
        <Box component="span" sx={{width: valueWidth, textAlign: "right"}}>
            {formatProfitValue(value, currency)}
        </Box>
        <Box
            component="span"
            data-testid={percentTestId}
            sx={{fontSize: fontSize - 2, width: percentWidth, textAlign: "right"}}
        >
            ({formatProfitPercent(percent)})
        </Box>
    </Box>
);
