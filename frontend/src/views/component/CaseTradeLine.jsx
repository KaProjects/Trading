import {Box} from "@mui/material";
import React from "react";
import {NUMERIC_FONT} from "./widgetLayout";
import {formatDecimals, formatPercent} from "../../service/FormattingService";
import {tightPlus} from "./tightPlus";

/**
 * A trade on a day of the case: what was bought or sold, and what came of it where it was a
 * sale. It reads in the stream rather than only in the table above it, because a case is read
 * as what happened in order - and a trade is the part of it that was acted on. The day names
 * itself; this is one line of what happened on it.
 */
const PROFIT = {fontWeight: 600}

/** Both figures carry their sign, so a gain and a loss read the same way at a glance. */
const signed = value => (Number(value) > 0 ? "+" : "") + formatDecimals(value, 2, 2)

export const CaseTradeLine = ({trade, onOpen}) => (
    <Box data-testid={`case-trade-${trade.key}`}>
        <Box sx={{...NUMERIC_FONT, display: "flex", alignItems: "baseline", gap: "6px", fontSize: 13}}>
            <Box component="span" sx={{fontWeight: 600, color: "text.secondary"}}>{trade.ticker}</Box>
            {/* The record written for this trade reads between the company and what was
                traded, and is what opens it. */}
            {onOpen &&
                <Box
                    component="span"
                    role="button"
                    aria-label={`Open the ${trade.ticker} record`}
                    onClick={onOpen}
                    sx={{
                        color: "text.secondary",
                        cursor: "pointer",
                        "&:hover": {textDecoration: "underline"},
                    }}
                >
                    new record
                </Box>
            }
            <Box component="span">
                {`${trade.sold ? "sold" : "bought"} ${formatDecimals(trade.quantity, 0, 4)}`
                    + `@${formatDecimals(trade.price, 2, 2)}${trade.currency ?? ""}`}
            </Box>
            {/* What the purchase came to altogether, fees included. */}
            {!trade.sold && trade.total !== null && trade.total !== undefined &&
                <Box component="span" sx={{color: "text.secondary"}}>
                    {`(${formatDecimals(trade.total, 2, 2)}${trade.currency ?? ""})`}
                </Box>
            }
            {/* What the sale made, in money and in percent: the money is what was earned, the
                percent is how well. */}
            {trade.sold &&
                <Box component="span" sx={{
                    ...PROFIT,
                    color: Number(trade.profit) < 0 ? "error.main" : "success.main",
                }}>
                    {tightPlus(signed(trade.profit) + (trade.currency ?? ""))}
                </Box>
            }
            {trade.sold &&
                <Box component="span" sx={{
                    ...PROFIT,
                    color: Number(trade.profitPercentage) < 0 ? "error.main" : "success.main",
                }}>
                    {tightPlus(formatPercent(trade.profitPercentage, true, 1))}
                </Box>
            }
        </Box>
    </Box>
);
