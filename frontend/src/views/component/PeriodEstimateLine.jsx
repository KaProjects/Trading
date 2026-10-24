import {Box} from "@mui/material";
import React from "react";
import {formatDecimals, formatMillionsRounded, formatPercent} from "../../service/FormattingService";
import {NUMERIC_FONT} from "./widgetLayout";
import {tightPlus} from "./tightPlus";

const LINE = {...NUMERIC_FONT, color: "text.primary", fontSize: 13};
const LABEL = {...LINE, color: "text.secondary"};
const NOTE = {
    ...NUMERIC_FONT,
    color: "text.disabled",
    fontStyle: "italic",
    fontSize: 11,
    // The brackets read with the figures under them rather than floating between the rows.
    marginBottom: "-2px",
    paddingLeft: "10px",
};
const ROW = {
    display: "grid",
    gridTemplateColumns: "max-content max-content max-content",
    alignItems: "baseline",
    textAlign: "left",
};
/**
 * Two rows: the label and the brackets above, the figures below, starting at the edge under the
 * label rather than behind it. The figures span the label's column, which is what puts them
 * there while the label still has a column of its own to sit in.
 *
 * The past figures and their total are dropped on a narrow screen, where the figures that are
 * left start at the edge as well. Every cell says which column and row it belongs to, because a
 * cell that is not displayed leaves the grid altogether: with the placement left to flow,
 * dropping those two would pull the rest out of line.
 */
const HIDDEN_WHEN_NARROW = {display: {xs: "none", sm: "block"}};
const cell = (column, row) => ({gridColumn: column, gridRow: row});
// The figures sit a character in from the label above them, which reads as belonging to it
// rather than as a line of its own.
const FIGURES_INDENT = {paddingLeft: "1ch"};

export const formatEstimateValue = value =>
    (value === null || value === undefined || value === "" ? "-" : formatDecimals(value, 0, 2) || "-");

export const formatRevenueEstimateValue = value =>
    (value === null || value === undefined || value === ""
        ? "-"
        : formatMillionsRounded(Number(value)) || "-");

export const formatEstimatePastTotal = value => formatDecimals(value, 0, 2) || "-";

export const formatRevenueEstimatePastTotal = value =>
    (value === null || value === undefined || value === ""
        ? "-"
        : formatMillionsRounded(Number(value)) || "-");

/**
 * Figures in the text colour, everything that is not a figure in the label's grey: the bars
 * between the windows carry no information, and at the same weight as the numbers they read like
 * part of them.
 */
function figures(values, format, separatorStyle = {color: "text.secondary"}) {
    return values.map((value, index) => (
        <React.Fragment key={index}>
            {index > 0 && <Box component="span" sx={separatorStyle}>{" | "}</Box>}
            {format(value)}
        </React.Fragment>
    ));
}

function changes(estimate) {
    const formatChange = value => tightPlus(formatPercent(value, true, 1) || "-");
    // The whole row is already the one quiet grey, so the bars take its colour rather than the
    // darker one they are given between the figures above.
    return figures(
        [estimate.currentChange, estimate.next1Change, estimate.next2Change, estimate.next3Change],
        formatChange,
        {});
}

/**
 * One snapshot of estimates: the four windows behind, then the four ahead, with how much each of
 * those moved underneath. It carries a date of its own, which is why it reads beside the research
 * written on the same day rather than in a column of its own.
 */
export const PeriodEstimateLine = ({
    testId,
    label,
    estimate,
    format = formatEstimateValue,
    pastTotalFormat = formatEstimatePastTotal,
}) => (
    <Box data-testid={testId} sx={ROW}>
        <Box sx={{...LABEL, ...cell(1, 1)}}>{label}</Box>
        {/* The space in front of the arrow is a hard one and belongs to this cell, so the next
            column starts on the arrow glyph, not on the gap before it. */}
        <Box sx={{...LINE, ...HIDDEN_WHEN_NARROW, ...FIGURES_INDENT, gridColumn: "1 / span 2", gridRow: 2}}>
            {figures([estimate.past4, estimate.past3, estimate.past2, estimate.past1], format)}
            {" "}
        </Box>
        {/* The arrow opens this cell, so the bracketed changes below begin on the same column. */}
        <Box sx={{...LINE, paddingLeft: {xs: FIGURES_INDENT.paddingLeft, sm: 0}, gridColumn: {xs: "1 / -1", sm: 3}, gridRow: 2}}>
            <Box component="span" sx={{color: "text.secondary", display: {xs: "none", sm: "inline"}}}>
                {"=> "}
            </Box>
            {figures([estimate.current, estimate.next1, estimate.next2, estimate.next3], format)}
        </Box>

        <Box sx={{...NOTE, ...HIDDEN_WHEN_NARROW, ...cell(2, 1), justifySelf: "center"}}>
            ({pastTotalFormat(estimate.pastTotal)})
        </Box>
        <Box sx={{...NOTE, ...cell(3, 1)}}>({changes(estimate)})</Box>
    </Box>
);
