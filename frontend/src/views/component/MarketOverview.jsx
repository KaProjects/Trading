import {Box} from "@mui/material";
import TroubleshootIcon from "@mui/icons-material/Troubleshoot";
import React from "react";
import {formatDecimals, formatMillionsRounded} from "../../service/FormattingService";
import {WIDGET_FIRST_COLUMN_WIDTH, WIDGET_ICON_GAP, WIDGET_ICON_SIZE} from "./widgetLayout";

// The value over its label, in the same sizes the financials and estimates widgets use, so a
// row of market numbers reads exactly like a row of reported ones. The label is kept at every
// width here, unlike those widgets: with no heading of its own and only two values, there would
// be nothing left to say what the numbers are.
const Item = ({label, value, first = false}) => (
    <Box sx={{
        marginLeft: first ? 0 : "10px",
        minWidth: first ? WIDGET_FIRST_COLUMN_WIDTH : undefined,
        flexShrink: 0,
    }}>
        <Box sx={{fontWeight: 600, fontSize: 13, textAlign: "center"}}>{value}</Box>
        <Box sx={{color: "text.secondary", mx: 0.5, marginTop: "-2px", fontSize: 11, textAlign: "center"}}>
            {label}
        </Box>
    </Box>
);

/**
 * Market facts about the company, as opposed to anything it reported: what it is worth and how
 * it moves. Market cap lived in the financials widget for want of a better home, but it is
 * price times shares, not a reported figure, and beta has nothing to do with the income
 * statement either.
 *
 * Deliberately a single line with no title of its own. It sits above the financials on every
 * width, so it has to cost as little vertical space as possible, and every value carries its
 * own label, which leaves nothing for a heading to explain.
 */
export const MarketOverview = ({marketCap, beta, sx}) => {
    const hasMarketCap = marketCap !== null && marketCap !== undefined && marketCap !== ""
        && Number.isFinite(Number(marketCap));
    const hasBeta = beta !== null && beta !== undefined && beta !== ""
        && Number.isFinite(Number(beta));

    if (!hasMarketCap && !hasBeta) return null;

    return (
        <Box
            data-testid="market-overview"
            sx={{
                ...sx,
                // The padding lives on the inner box, as it does in the neighbouring widgets, so
                // this one ends up exactly as wide as they do rather than 14px wider.
                maxWidth: "550px",
                borderLeft: "3px solid",
                borderColor: "text.disabled",
                bgcolor: "rgba(120, 120, 120, 0.04)",
                borderRadius: "0 4px 4px 0",
            }}
        >
            <Box sx={{
                padding: "3px 7px",
                display: "flex",
                alignItems: "flex-start",
                gap: WIDGET_ICON_GAP,
            }}>
                {/* Carries an icon purely so its values start on the same column as the widgets
                    below it, which all indent past one. */}
                <Box sx={{display: "flex", color: "text.disabled", marginTop: "2px", flexShrink: 0}}>
                    <TroubleshootIcon sx={{fontSize: WIDGET_ICON_SIZE}}/>
                </Box>
                <Box sx={{display: "flex", alignItems: "flex-start"}}>
                    {hasMarketCap &&
                        <Item first label="mcap" value={formatMillionsRounded(Number(marketCap))}/>
                    }
                    {hasBeta &&
                        <Item first={!hasMarketCap} label="beta" value={formatDecimals(Number(beta), 0, 2)}/>
                    }
                </Box>
            </Box>
        </Box>
    );
};
