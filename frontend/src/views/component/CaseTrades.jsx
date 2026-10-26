import {Box, IconButton, Tooltip} from "@mui/material";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import KeyboardArrowUpIcon from "@mui/icons-material/KeyboardArrowUp";
import UnfoldLessIcon from "@mui/icons-material/UnfoldLess";
import UnfoldMoreIcon from "@mui/icons-material/UnfoldMore";
import useMediaQuery from "@mui/material/useMediaQuery";
import {SWIPE_NAV_BREAKPOINT} from "./MainBar";
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

/**
 * What a position is worth today is not money made, so it is not written as though it were:
 * the trades view greys the figures of an open trade and mutes the green and the red, and a
 * case says it the same way.
 */
const UNREALISED_GREY = "#adadad"
const unrealisedColour = profit => (Number(profit) < 0 ? "#d99595" : "#99bb99")

/** What one open trade is worth now against what it was paid for. */
const onPaper = trade =>
    unrealised(trade.quantity, Number(trade.quantity ?? 0) * Number(trade.purchasePrice ?? 0),
        trade.currentPrice)

/** What an open position has made on paper: today's price against what was paid for it. */
const unrealised = (quantity, paid, price) => {
    if (price === null || price === undefined || !paid) return null

    const now = Number(quantity) * Number(price)
    return {profit: now - paid, percentage: ((now - paid) / paid) * 100}
}

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

/** What the open positions are worth on paper, one total per currency they were bought in. */
export function unrealisedByCurrency(trades = []) {
    const totals = new Map()
    trades.filter(trade => trade.active).forEach(trade => {
        const paper = onPaper(trade)
        if (!paper) return

        const currency = trade.currency ?? ""
        totals.set(currency, (totals.get(currency) ?? 0) + paper.profit)
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
            price: null,
        }

        if (trade.active) {
            // What is still held, and what it cost: the average is weighted by the size of
            // each purchase, which is what an average price of a position means.
            position.quantity += Number(trade.quantity ?? 0)
            position.paid += Number(trade.quantity ?? 0) * Number(trade.purchasePrice ?? 0)
            if (trade.currentPrice !== null && trade.currentPrice !== undefined) {
                position.price = Number(trade.currentPrice)
            }
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
            onPaper: unrealised(position.quantity, position.paid, position.price),
        }))
        .sort((first, second) => first.ticker.localeCompare(second.ticker))
}

const Heading = ({labels}) => (
    // Held as a whole, like the foot: a heading held cell by cell lets the rows show through
    // the gaps between them as they scroll under it.
    <Box component="thead" sx={{position: "sticky", top: 0, zIndex: 3, bgcolor: "background.paper"}}>
        <Box component="tr">
            {labels.map((heading, index) => (
                <Box component="th" key={index} sx={{
                    ...CELL,
                    textAlign: heading.left ? "left" : "right",
                    fontWeight: 400,
                    color: "text.disabled",
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
const Totals = ({trades, columns, unrealisedColumn = false}) => {
    const realised = new Map(profitByCurrency(trades))
    const onPaperTotals = new Map(unrealisedByCurrency(trades))
    // Every currency the case has anything to say about, whether it was made or is only held.
    const currencies = [...new Set([...realised.keys(), ...onPaperTotals.keys()])]

    return (
        // The whole foot is held at the bottom, not each row of it: rows pinned one by one all
        // land in the same place and only the last of them is left to read.
        <Box component="tfoot" sx={{position: "sticky", bottom: 0, zIndex: 3, bgcolor: "background.paper"}}>
            {currencies.map((currency, index) => {
                // One rule over the totals, none between them: they are one block, not rows of
                // a table of their own.
                const held = index === 0
                    ? {...TOTAL_CELL, borderTop: "1px solid", borderColor: "divider"}
                    : TOTAL_CELL
                const made = realised.get(currency)
                const onPaperTotal = onPaperTotals.get(currency)
                const figure = {...CELL, ...held, textAlign: "right", fontWeight: 600}

                return (
                    <Box component="tr" key={currency} data-testid={`case-profit-${currency}`}>
                        <Box component="td" colSpan={unrealisedColumn ? columns - 4 : columns - 2}
                             sx={{...CELL, ...held}}/>
                        {/* Each total under the column it belongs to, and what is only held on
                            paper reads muted, the way the figures above it do. */}
                        {unrealisedColumn &&
                            <>
                                <Box component="td"
                                     data-testid={`case-unrealized-${currency}`}
                                     sx={{...figure, color: unrealisedColour(onPaperTotal)}}>
                                    {onPaperTotal === undefined
                                        ? ""
                                        : tightPlus(signed(onPaperTotal, currency))}
                                </Box>
                                <Box component="td" sx={{...CELL, ...held}}/>
                            </>
                        }
                        <Box component="td" sx={{...figure, color: profitColour(made)}}>
                            {made === undefined ? "" : tightPlus(signed(made, currency))}
                        </Box>
                        {/* Where the totals have no column of their own for it, what is still
                            held on paper follows what was made, in brackets beside it. */}
                        <Box component="td" sx={{
                            ...CELL,
                            ...held,
                            textAlign: "right",
                            color: unrealisedColour(onPaperTotal),
                        }}>
                            {unrealisedColumn || onPaperTotal === undefined
                                ? ""
                                : <>({tightPlus(signed(onPaperTotal, currency))})</>}
                        </Box>
                    </Box>
                )
            })}
        </Box>
    )
}

const TOTAL_CELL = {bgcolor: "background.paper"}

export const CaseTrades = ({trades = []}) => {
    // A case over one company has nothing to sum up: its summary would be the one line the
    // trades already add up to, so it is read as it was traded.
    const summarised = new Set(trades.map(trade => trade.ticker)).size > 1
    const [expanded, setExpanded] = useState(!summarised)
    // A phone has room for the case or for its trades, not both: there the table is folded
    // away until it is asked for.
    const narrow = useMediaQuery(`(max-width:${SWIPE_NAV_BREAKPOINT}px)`)
    const [shown, setShown] = useState(false)
    const open = !narrow || shown
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

    // Folded away, the card is the invitation to open it: no buttons to aim at, the whole
    // strip is the thing to press, and it keeps as little of the screen as it can.
    if (!open) {
        return (
            <Box
                data-testid="case-trades-folded"
                role="button"
                aria-label="Show the trades"
                onClick={() => setShown(true)}
                sx={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    gap: "3px",
                    // Eats into the card's own padding: there is nothing here to give room to.
                    margin: "-6px 0",
                    cursor: "pointer",
                    fontSize: 11,
                    color: "text.disabled",
                }}
            >
                <UnfoldMoreIcon sx={{fontSize: 14}}/>
                trades
            </Box>
        )
    }

    return (
        <Box sx={{position: "relative"}}>
            {narrow &&
                <Tooltip title="Hide the trades">
                    <IconButton
                        aria-label="Hide the trades"
                        size="small"
                        onClick={() => setShown(false)}
                        sx={{
                            position: "absolute",
                            top: "-7px",
                            right: "16px",
                            zIndex: 4,
                            padding: "1px",
                            color: "text.secondary",
                        }}
                    >
                        <KeyboardArrowUpIcon sx={{fontSize: 16}}/>
                    </IconButton>
                </Tooltip>
            }
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
                                            {/* An open trade is read at what the company last
                                                traded at, and what that is worth on paper -
                                                greyed, because none of it has been made yet. */}
                                            <Box component="td" sx={{
                                                ...CELL,
                                                textAlign: "right",
                                                color: trade.active ? UNREALISED_GREY : "text.primary",
                                            }}>
                                                {trade.active
                                                    ? (onPaper(trade)
                                                        ? formatDecimals(trade.currentPrice, 2, 2) + (trade.currency ?? "")
                                                        : "")
                                                    : formatDecimals(trade.sellPrice, 2, 2) + (trade.currency ?? "")}
                                            </Box>
                                            <Box component="td" sx={{
                                                ...CELL,
                                                textAlign: "right",
                                                color: trade.active
                                                    ? unrealisedColour(onPaper(trade)?.profit)
                                                    : profitColour(trade.profit),
                                            }}>
                                                {trade.active
                                                    ? (onPaper(trade)
                                                        ? tightPlus(signed(onPaper(trade).profit, trade.currency ?? ""))
                                                        : "")
                                                    : tightPlus(signed(trade.profit, trade.currency ?? ""))}
                                            </Box>
                                            <Box component="td" sx={{
                                                ...CELL,
                                                textAlign: "right",
                                                color: trade.active
                                                    ? unrealisedColour(onPaper(trade)?.profit)
                                                    : profitColour(trade.profit),
                                            }}>
                                                {trade.active
                                                    ? (onPaper(trade)
                                                        ? tightPlus(formatPercent(onPaper(trade).percentage, true, 1))
                                                        : "")
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
                                {label: "unrealized"},
                                {label: ""},
                                {label: "realized"},
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
                                        {/* What the open part is worth on paper, before what
                                            the closed part actually made. */}
                                        <Box component="td" sx={{
                                            ...CELL,
                                            textAlign: "right",
                                            color: unrealisedColour(position.onPaper?.profit),
                                        }}>
                                            {position.onPaper
                                                ? tightPlus(signed(position.onPaper.profit, position.currency))
                                                : ""}
                                        </Box>
                                        <Box component="td" sx={{
                                            ...CELL,
                                            textAlign: "right",
                                            color: unrealisedColour(position.onPaper?.profit),
                                        }}>
                                            {position.onPaper
                                                ? tightPlus(formatPercent(position.onPaper.percentage, true, 1))
                                                : ""}
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
                            <Totals trades={trades} columns={7} unrealisedColumn/>
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

// Separate borders rather than collapsed: a collapsed table paints the rows that scroll under
// a held heading or foot straight through its background.
const TABLE = {
    ...NUMERIC_FONT,
    borderCollapse: "separate",
    borderSpacing: 0,
    fontSize: 12,
    width: "100%",
}

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
