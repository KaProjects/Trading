import {Box} from "@mui/material";
import React from "react";
import {formatDecimals, formatMillions, formatPercent, isNotAValue} from "../../service/FormattingService";
import {NUMERIC_FONT as NUMERIC} from "./widgetLayout";
import {tightPlus} from "./tightPlus";

const perShare = (value, shares) => {
    if (isNotAValue(value) || isNotAValue(shares) || Number(shares) === 0) return null;
    return Number(value) / Number(shares);
};

const changeColor = value => {
    if (isNotAValue(value)) return "text.secondary";
    return Number(value) < 0 ? "error.main" : "success.main";
};

/** Quarter over quarter, then year over year - the same order the financials dialog uses. */
const Changes = ({metric}) => {
    const qoq = formatPercent(metric?.qoq, true, 0);
    const yoy = formatPercent(metric?.yoy, true, 0);
    if (!qoq && !yoy) return <Box sx={{paddingLeft: "12px"}}/>;

    return (
        <Box sx={{
            ...NUMERIC,
            textAlign: "right",
            whiteSpace: "nowrap",
            fontStyle: "italic",
            paddingLeft: "12px",
        }}>
            <Box component="span" sx={{color: changeColor(metric?.qoq)}}>{tightPlus(qoq)}</Box>
            {qoq && yoy &&
                <Box component="span" sx={{color: "text.disabled"}}>/</Box>
            }
            <Box component="span" sx={{color: changeColor(metric?.yoy)}}>{tightPlus(yoy)}</Box>
        </Box>
    );
};

const Margin = ({value, dash = false}) => (
    <Box sx={{
        ...NUMERIC,
        textAlign: "right",
        paddingLeft: "12px",
        color: dash ? "text.disabled" : "text.primary",
    }}>
        {dash ? "\u2013" : formatPercent(value, false, 1)}
    </Box>
);

const Row = ({label, metric, shares, dash = false}) => {
    const share = perShare(metric?.value, shares);

    return (
        <>
            <Box sx={{color: "text.secondary", whiteSpace: "nowrap"}}>{label}</Box>
            <Box sx={{
                ...NUMERIC,
                textAlign: "right",
                color: "text.primary",
                whiteSpace: "nowrap",
                paddingLeft: "12px",
            }}>
                {formatMillions(metric?.value)}
            </Box>
            <Box sx={{
                ...NUMERIC,
                textAlign: "right",
                fontSize: 11,
                fontStyle: "italic",
                color: "text.disabled",
                paddingLeft: "4px",
            }}>
                {share === null ? "" : "(" + formatDecimals(share, 2, 2) + ")"}
            </Box>
            <Margin value={metric?.margin} dash={dash}/>
            <Changes metric={metric}/>
        </>
    );
};

const BLANK = {label: "", value: ""};

const Aside = ({label, value, note, noteSize = 11, firstInRow = true}) => (
    <>
        <Box sx={{
            color: "text.disabled",
            whiteSpace: "nowrap",
            paddingLeft: firstInRow ? 0 : "14px",
        }}>
            {label}
        </Box>
        <Box sx={{
            ...NUMERIC,
            textAlign: "right",
            color: "text.primary",
            whiteSpace: "nowrap",
            paddingLeft: "8px",
        }}>
            {note &&
                <Box component="span" sx={{fontSize: noteSize, fontStyle: "italic", color: "text.disabled"}}>
                    {"(" + note + ") "}
                </Box>
            }
            {value}
        </Box>
    </>
);

/**
 * One family of figures beside the income matrix, behind its own rule. `pairs` is how many
 * label/value pairs sit on a row: one makes a tall column for a wide screen, two makes a short
 * block for a narrow one.
 */
const AsideColumn = ({entries, pairs = 1, divider = "left", noteSize}) => {
    if (entries.length === 0) return null;

    return (
        <Box sx={{
            display: "grid",
            gridTemplateColumns: "auto auto ".repeat(pairs).trim(),
            columnGap: 0,
            rowGap: "1px",
            alignItems: "baseline",
            marginLeft: divider === "left" ? "12px" : 0,
            paddingLeft: divider === "left" ? "12px" : 0,
            borderLeft: divider === "left" ? "1px solid" : "none",
            borderColor: "divider",
            flexShrink: 0,
        }}>
            {entries.map((entry, index) => (
                <Aside
                    key={entry.label || "blank-" + index}
                    label={entry.label}
                    value={entry.value}
                    note={entry.note}
                    noteSize={noteSize}
                    /* Only a pair that follows another on the same row needs clearing from it.
                       One entry is one pair, so a row holds `pairs` of them. */
                    firstInRow={index % pairs === 0}
                />
            ))}
        </Box>
    );
};

/**
 * A period's reported figures: the income metrics as a matrix on the left, and the two families
 * that are not income - the cash-flow figures and the period's price range - alongside it on the
 * right, where they cannot be mistaken for more rows of the same table.
 */
export const PeriodFinancialSummary = ({financial, shares, priceHigh, priceLow, currency, compact = false}) => {
    if (!financial) return null;

    // A company that paid nothing has no dividend to report: "(0.00%) 0M" reads like a measured
    // figure, where a dash says the same thing without the false precision. Only an actual zero -
    // a dividend that is simply missing is dropped from the list below, as the other figures are.
    const noDividend = !isNotAValue(financial.dividend) && Number(financial.dividend) === 0;

    const cashFlow = [
        {label: "CapEx", value: formatMillions(financial.capex?.value)},
        {label: "FCF", value: formatMillions(financial.freeCashFlow?.value)},
        {
            label: compact ? "Div." : "Dividend",
            value: noDividend ? "\u2013" : formatMillions(financial.dividend),
            note: noDividend ? null : formatPercent(financial.dividendMargin, false, 2),
        },
        {label: compact ? "EPS adj." : "Adj. EPS", value: formatDecimals(financial.adjustedEps, 2, 2)},
    ].filter(entry => entry.value && entry.value !== "-");

    const price = [
        {label: compact ? "H" : "High", value: priceHigh},
        {label: compact ? "L" : "Low", value: priceLow},
    ]
        .filter(entry => !isNotAValue(entry.value))
        .map(entry => ({
            label: entry.label,
            value: formatDecimals(entry.value, 2, 2) + (currency ?? ""),
        }));

    // Row major: two cash-flow pairs, then that row's price pair in the third column.
    const narrowAside = [];
    const rows = Math.max(Math.ceil(cashFlow.length / 2), price.length);
    for (let row = 0; row < rows; row++) {
        narrowAside.push(
            cashFlow[row * 2] ?? BLANK,
            cashFlow[row * 2 + 1] ?? BLANK,
            price[row] ?? BLANK,
        );
    }

    return (
        <Box
            data-testid="period-financial-summary"
            sx={{
                fontSize: 13,
                color: "text.secondary",
                display: "flex",
                flexDirection: compact ? "column" : "row",
                alignItems: "flex-start",
                gap: compact ? "3px" : "4px",
                // Closes the block off from the research content that follows it.
                paddingBottom: "4px",
                borderBottom: "1px solid",
                borderColor: "divider",
            }}
        >
            <Box sx={{
                display: "grid",
                gridTemplateColumns: "auto auto auto auto auto",
                columnGap: 0,
                rowGap: "1px",
                alignItems: "baseline",
                flexShrink: 0,
            }}>
                <Row label="Revenue" metric={financial.revenue} shares={shares} dash/>
                <Row label="Gross p." metric={financial.grossProfit} shares={shares}/>
                <Row
                    label={compact ? "Op. inc." : "Op. income"}
                    metric={financial.operatingIncome}
                    shares={shares}
                />
                <Row
                    label={compact ? "Net inc." : "Net income"}
                    metric={financial.netIncome}
                    shares={shares}
                />
            </Box>

            {!compact &&
                <>
                    <AsideColumn entries={cashFlow}/>
                    <AsideColumn entries={price}/>
                </>
            }

            {/* Narrow: the two families sit on one short row below the matrix, kept clear of the
                period's action buttons which own the right-hand edge at this width. */}
            {compact && narrowAside.length > 0 &&
                <Box sx={{
                    marginTop: "3px",
                    paddingTop: "3px",
                    // The action buttons own the right-hand edge at this width.
                    paddingRight: "44px",
                    borderTop: "1px solid",
                    borderColor: "divider",
                    width: "100%",
                    boxSizing: "border-box",
                }}>
                    <AsideColumn entries={narrowAside} pairs={3} divider="none" noteSize={8}/>
                </Box>
            }
        </Box>
    );
};
