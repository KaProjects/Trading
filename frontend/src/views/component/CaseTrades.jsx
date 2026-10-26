import {Box, IconButton, Tooltip} from "@mui/material";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import KeyboardArrowUpIcon from "@mui/icons-material/KeyboardArrowUp";
import UnfoldLessIcon from "@mui/icons-material/UnfoldLess";
import UnfoldMoreIcon from "@mui/icons-material/UnfoldMore";
import React, {useCallback, useEffect, useRef, useState} from "react";
import {formatDateShort, formatDecimals, formatPercent} from "../../service/FormattingService";
import {NUMERIC_FONT} from "./widgetLayout";
import {tightPlus} from "./tightPlus";

const CELL = {padding: "1px 6px 1px 0", whiteSpace: "nowrap"}
/** How many trades are shown at once before the rows start scrolling. */
const ROWS_SHOWN = 20
/** One row of this table, measured: the figures are one line at a fixed size. */
const ROW_HEIGHT = 17

/** Both figures carry their sign, so a gain and a loss read the same way at a glance. */
const signed = (value, suffix = "") =>
    (Number(value) > 0 ? "+" : "") + formatDecimals(value, 2, 2) + suffix

const profitColour = profit => (Number(profit) < 0 ? "error.main" : "success.main")

/** What the closed trades have made, one total per currency they were made in. */
export function profitByCurrency(trades = []) {
    const totals = new Map()
    trades
        .filter(trade => !trade.active && trade.profit !== null && trade.profit !== undefined)
        .forEach(trade => {
            const currency = trade.currency ?? ""
            totals.set(currency, (totals.get(currency) ?? 0) + Number(trade.profit))
        })
    return [...totals.entries()]
}

/**
 * The case a company at a time: what is still held in it, what it was paid for, and what the
 * part already sold has made. A company has one currency, so nothing here has to be kept apart
 * the way the totals do.
 */
export function positionsByCompany(trades = []) {
    const byTicker = new Map()

    trades.forEach(trade => {
        const position = byTicker.get(trade.ticker) ?? {
            ticker: trade.ticker,
            currency: trade.currency ?? "",
            quantity: 0,
            paid: 0,
            spent: 0,
            profit: 0,
            closed: 0,
        }

        if (trade.active) {
            // What is still held, and what it cost: the average is weighted by the size of
            // each purchase, which is what an average price of a position means.
            position.quantity += Number(trade.quantity ?? 0)
            position.paid += Number(trade.quantity ?? 0) * Number(trade.purchasePrice ?? 0)
        } else {
            position.profit += Number(trade.profit ?? 0)
            position.spent += Number(trade.purchaseTotal ?? 0)
            position.closed += 1
        }
        byTicker.set(trade.ticker, position)
    })

    return [...byTicker.values()]
        .map(position => ({
            ...position,
            averagePrice: position.quantity > 0 ? position.paid / position.quantity : null,
            profitPercentage: position.spent > 0 ? (position.profit / position.spent) * 100 : null,
        }))
        .sort((first, second) => first.ticker.localeCompare(second.ticker))
}

const Heading = ({labels}) => (
    <Box component="thead">
        <Box component="tr">
            {labels.map((heading, index) => (
                <Box component="th" key={index} sx={{
                    ...CELL,
                    textAlign: heading.left ? "left" : "right",
                    fontWeight: 400,
                    color: "text.disabled",
                    // The rows run under the heading rather than over it.
                    position: "sticky",
                    top: 0,
                    zIndex: 2,
                    bgcolor: "background.paper",
                }}>
                    {heading.label}
                </Box>
            ))}
        </Box>
    </Box>
)

/**
 * What the case has made so far, kept per currency: a case can cover companies that do not
 * share one. It stays in sight while the trades above it scroll.
 */
const Totals = ({trades, columns}) => (
    <Box component="tfoot">
        {profitByCurrency(trades).map(([currency, profit]) => (
            <Box component="tr" key={currency} data-testid={`case-profit-${currency}`}>
                <Box component="td" colSpan={columns - 2} sx={{...CELL, ...TOTAL_CELL}}/>
                {/* Under the profit column, where the column already says what it is. */}
                <Box component="td" sx={{
                    ...CELL,
                    ...TOTAL_CELL,
                    textAlign: "right",
                    fontWeight: 600,
                    color: profitColour(profit),
                }}>
                    {tightPlus(signed(profit, currency))}
                </Box>
                <Box component="td" sx={{...CELL, ...TOTAL_CELL}}/>
            </Box>
        ))}
    </Box>
)

const TOTAL_CELL = {
    borderTop: "1px solid",
    borderColor: "divider",
    position: "sticky",
    bottom: 0,
    zIndex: 2,
    bgcolor: "background.paper",
}

export const CaseTrades = ({trades = []}) => {
    // A case over one company has nothing to sum up: its summary would be the one line the
    // trades already add up to, so it is read as it was traded.
    const summarised = new Set(trades.map(trade => trade.ticker)).size > 1
    const [expanded, setExpanded] = useState(!summarised)
    const [reach, setReach] = useState({before: false, after: false})
    const scroller = useRef(null)

    const updateScrollHint = useCallback(() => {
        const box = scroller.current
        if (!box) return setReach({before: false, after: false})

        setReach({
            before: box.scrollTop > 1,
            after: box.scrollTop + box.clientHeight < box.scrollHeight - 1,
        })
    }, [])

    // The latest trades are at the bottom, so that is where the table opens.
    useEffect(() => {
        const box = scroller.current
        if (!box) return

        if (expanded) box.scrollTop = box.scrollHeight
        updateScrollHint()
    }, [expanded, trades.length, updateScrollHint])

    if (trades.length === 0) return null

    const positions = positionsByCompany(trades)
    const totalRows = profitByCurrency(trades).length
    // Enough room for the rows, the heading over them and the totals under them; past that the
    // rows scroll rather than the page.
    const capped = expanded && trades.length > ROWS_SHOWN
    const maxHeight = capped ? (ROWS_SHOWN + totalRows + 1) * ROW_HEIGHT : undefined

    return (
        <Box sx={{position: "relative"}}>
            {summarised &&
                <Tooltip title={expanded ? "Companies" : "Every trade"}>
                    <IconButton
                        aria-label={expanded ? "Show the companies only" : "Show every trade"}
                        size="small"
                        onClick={() => setExpanded(open => !open)}
                        // In the corner of the block, over the heading rather than beside the
                        // figures: the column under it is the one the eye reads down.
                        sx={{
                            position: "absolute",
                            top: "-7px",
                            right: "-6px",
                            zIndex: 4,
                            padding: "1px",
                            color: "text.secondary",
                        }}
                    >
                        {expanded
                            ? <UnfoldLessIcon sx={{fontSize: 16}}/>
                            : <UnfoldMoreIcon sx={{fontSize: 16}}/>
                        }
                    </IconButton>
                </Tooltip>
            }
            <Box sx={{position: "relative"}}>
                {/* Narrow screens scroll the table rather than the page: left to itself it is
                    wider than the phone and takes the page with it. */}
                <Box
                    ref={scroller}
                    onScroll={updateScrollHint}
                    sx={{overflow: "auto", maxWidth: "100%", maxHeight: maxHeight}}
                >
                    {expanded
                        ? <Box component="table" data-testid="case-trades" sx={TABLE}>
                            <Heading labels={[
                                {label: "", left: true},
                                {label: "bought", left: true},
                                {label: "quantity"},
                                {label: "price"},
                                {label: "sold", left: true},
                                {label: "price"},
                                {label: "profit"},
                                {label: ""},
                            ]}/>
                            <Box component="tbody">
                                {/* Oldest first: the table reads as the case was traded. */}
                                {[...trades]
                                    .sort((first, second) => String(first.purchaseDate)
                                        .localeCompare(String(second.purchaseDate)))
                                    .map(trade => (
                                        <Box component="tr" key={trade.id}>
                                            <Box component="td" sx={{...CELL, fontWeight: 600}}>
                                                {trade.ticker}
                                            </Box>
                                            <Box component="td" sx={CELL}>
                                                {formatDateShort(trade.purchaseDate)}
                                            </Box>
                                            <Box component="td" sx={{...CELL, textAlign: "right"}}>
                                                {formatDecimals(trade.quantity, 0, 4)}
                                            </Box>
                                            <Box component="td" sx={{...CELL, textAlign: "right"}}>
                                                {formatDecimals(trade.purchasePrice, 2, 2) + (trade.currency ?? "")}
                                            </Box>
                                            {/* A part-sold position is two trades sharing a
                                                purchase date: one closed here, one still open. */}
                                            <Box component="td" sx={{
                                                ...CELL,
                                                color: trade.active ? "text.disabled" : "text.primary",
                                            }}>
                                                {trade.active ? "open" : formatDateShort(trade.sellDate)}
                                            </Box>
                                            <Box component="td" sx={{...CELL, textAlign: "right"}}>
                                                {trade.active
                                                    ? ""
                                                    : formatDecimals(trade.sellPrice, 2, 2) + (trade.currency ?? "")}
                                            </Box>
                                            <Box component="td" sx={{
                                                ...CELL,
                                                textAlign: "right",
                                                color: profitColour(trade.profit),
                                            }}>
                                                {trade.active ? "" : tightPlus(signed(trade.profit, trade.currency ?? ""))}
                                            </Box>
                                            <Box component="td" sx={{
                                                ...CELL,
                                                textAlign: "right",
                                                color: profitColour(trade.profit),
                                            }}>
                                                {trade.active
                                                    ? ""
                                                    : tightPlus(formatPercent(trade.profitPercentage, true, 1))}
                                            </Box>
                                        </Box>
                                    ))}
                            </Box>
                            <Totals trades={trades} columns={8}/>
                        </Box>

                        : <Box component="table" data-testid="case-trades-summary" sx={TABLE}>
                            <Heading labels={[
                                {label: "", left: true},
                                {label: "held"},
                                {label: "avg price"},
                                {label: "profit"},
                                {label: ""},
                            ]}/>
                            <Box component="tbody">
                                {positions.map(position => (
                                    <Box component="tr" key={position.ticker}
                                         data-testid={`case-position-${position.ticker}`}>
                                        <Box component="td" sx={{...CELL, fontWeight: 600}}>
                                            {position.ticker}
                                        </Box>
                                        {/* Nothing held any more is left blank rather than
                                            written as a nought: the position is closed, not empty. */}
                                        <Box component="td" sx={{...CELL, textAlign: "right"}}>
                                            {position.quantity > 0 ? formatDecimals(position.quantity, 0, 4) : ""}
                                        </Box>
                                        <Box component="td" sx={{...CELL, textAlign: "right"}}>
                                            {position.averagePrice === null
                                                ? ""
                                                : formatDecimals(position.averagePrice, 2, 2) + position.currency}
                                        </Box>
                                        <Box component="td" sx={{
                                            ...CELL,
                                            textAlign: "right",
                                            color: profitColour(position.profit),
                                        }}>
                                            {position.closed === 0
                                                ? ""
                                                : tightPlus(signed(position.profit, position.currency))}
                                        </Box>
                                        <Box component="td" sx={{
                                            ...CELL,
                                            textAlign: "right",
                                            color: profitColour(position.profit),
                                        }}>
                                            {position.profitPercentage === null
                                                ? ""
                                                : tightPlus(formatPercent(position.profitPercentage, true, 1))}
                                        </Box>
                                    </Box>
                                ))}
                            </Box>
                            <Totals trades={trades} columns={5}/>
                        </Box>
                    }
                </Box>

                {/* Where the scrollbar is, so it is plain there is more of the case either way. */}
                {reach.before && <Hint icon={KeyboardArrowUpIcon} end="up" sx={{top: "2px"}}/>}
                {reach.after && <Hint icon={KeyboardArrowDownIcon} end="down" sx={{bottom: "2px"}}/>}
            </Box>
        </Box>
    )
}

const TABLE = {...NUMERIC_FONT, borderCollapse: "collapse", fontSize: 12, width: "100%"}

const Hint = ({icon, end, sx}) => React.createElement(icon, {
    "data-testid": `case-trades-scroll-${end}`,
    sx: {
        position: "absolute",
        right: "-2px",
        fontSize: 16,
        color: "text.disabled",
        pointerEvents: "none",
        bgcolor: "background.paper",
        borderRadius: "50%",
        zIndex: 3,
        ...sx,
    },
})
