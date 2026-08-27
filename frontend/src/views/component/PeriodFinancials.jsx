import {
    Box,
    ButtonBase,
    Grid,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
} from "@mui/material";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import React from "react";
import {ReactComponent as FinancialsIcon} from "../../assets/icons/financials.svg";
import {
    formatMillions,
    formatPercent,
    formatPeriodName,
    isNotAValue,
} from "../../service/FormattingService";

const headers = [
    "Period",
    "Revenue",
    "Gross Profit",
    "Operating Income",
    "Net Income",
    "Dividend",
    "CapEx",
    "FCF",
];

const formatDividend = (value) => value === 0 || isNotAValue(value) ? "-" : formatMillions(value) || "-";
const formatFinancialValue = (value) => isNotAValue(value) ? "-" : formatMillions(value) || "-";

const periodSequence = period => {
    const year = Number(period?.year);
    if (!Number.isInteger(year)) return null;

    if (/^Q[1-4]$/.test(period?.type)) return year * 4 + Number(period.type[1]) - 1;
    if (/^H[1-2]$/.test(period?.type)) return year * 2 + Number(period.type[1]) - 1;
    if (period?.type === "FY") return year;
    return null;
};

const latestCompletePeriods = financials => {
    const type = financials?.[0]?.period?.type;
    const count = type?.startsWith("Q") ? 4 : type?.startsWith("H") ? 2 : type === "FY" ? 1 : 0;
    const cadence = type?.[0];
    const periods = financials?.slice(0, count) ?? [];

    if (count === 0 || periods.length !== count) return [];
    if (periods.some(financial => financial.period?.type?.[0] !== cadence)) return [];

    const sequences = periods.map(financial => periodSequence(financial.period));
    if (sequences.some(sequence => sequence === null)) return [];
    if (sequences.some((sequence, index) => index > 0 && sequences[index - 1] - sequence !== 1)) return [];
    return periods;
};

const hasCompleteValues = (periods, value) => periods.length > 0
    && periods.every(financial => !isNotAValue(value(financial)));

const formatSummaryMargin = margin => {
    const decimals = Math.abs(margin) < 10 && !Number.isInteger(margin) ? 1 : 0;
    return formatPercent(margin, false, decimals);
};

const FinancialSummaryItem = ({value, label, margin, first = false}) => {
    const formattedMargin = formatSummaryMargin(margin);

    return (
        <Box sx={{marginLeft: first ? 0 : "10px", flexShrink: 0}}>
            <Box sx={{color: "text.secondary", fontSize: 9, textAlign: "center"}}>
                {formattedMargin ? `(${formattedMargin})` : "\u00a0"}
            </Box>
            <Box sx={{fontWeight: 600, fontSize: 13, textAlign: "center"}}>{formatMillions(value)}</Box>
            <Box sx={{color: "text.secondary", mx: 0.5, fontSize: 11, textAlign: "center"}}>{label}</Box>
        </Box>
    );
};

const FinancialTableCell = ({value, margin, yoy, qoq, fontSize}) => {
    const formattedYoy = formatPercent(yoy, true, 0);
    const formattedQoq = formatPercent(qoq, true, 0);
    const changesValue = formattedYoy || formattedQoq
        ? (formattedYoy ? formattedYoy : "") + (formattedYoy && formattedQoq ? " / " : "") + (formattedQoq ? formattedQoq : "")
        : "";
    const annotationSize = fontSize - 5;

    return (
        <TableCell sx={{textAlign: "center", fontSize}}>
            {!isNotAValue(margin) ?
                <Box sx={{display: "flex", flexDirection: "column"}}>
                    <Box sx={{fontSize: annotationSize, marginBottom: "-3px", color: "grey"}}>
                        ({formatPercent(margin, false, 0)})
                    </Box>
                    <Box sx={{fontSize}}>{value}</Box>
                    <Box sx={{fontSize: annotationSize, marginTop: "-3px", color: "grey"}}>{changesValue}</Box>
                </Box>
                :
                <Box sx={{fontSize}}>{value}</Box>
            }
        </TableCell>
    );
};

export const FinancialsTable = ({financials, fontSize = 14, scrollable = false}) => (
    <TableContainer sx={{
        width: "100%",
        maxWidth: "100%",
        overflow: "auto",
        ...(scrollable && {flex: 1, minHeight: 0}),
    }}>
        <Table size="small" aria-label="financials table" stickyHeader sx={{minWidth: 850}}>
            <TableHead>
                <TableRow>
                    {headers.map(column => <TableCell key={column} sx={{fontSize, textAlign: "center"}}>{column}</TableCell>)}
                </TableRow>
            </TableHead>
            <TableBody>
                {financials.map(financial => (
                    <TableRow key={formatPeriodName(financial.period)}>
                        <FinancialTableCell value={formatPeriodName(financial.period)} fontSize={fontSize}/>
                        <FinancialTableCell value={formatMillions(financial.revenue.value)} margin={financial.revenue.margin} yoy={financial.revenue.yoy} qoq={financial.revenue.qoq} fontSize={fontSize}/>
                        <FinancialTableCell value={formatFinancialValue(financial.grossProfit.value)} margin={financial.grossProfit.margin} yoy={financial.grossProfit.yoy} qoq={financial.grossProfit.qoq} fontSize={fontSize}/>
                        <FinancialTableCell value={formatFinancialValue(financial.operatingIncome.value)} margin={financial.operatingIncome.margin} yoy={financial.operatingIncome.yoy} qoq={financial.operatingIncome.qoq} fontSize={fontSize}/>
                        <FinancialTableCell value={formatMillions(financial.netIncome.value)} margin={financial.netIncome.margin} yoy={financial.netIncome.yoy} qoq={financial.netIncome.qoq} fontSize={fontSize}/>
                        <FinancialTableCell value={formatDividend(financial.dividend)} fontSize={fontSize}/>
                        <FinancialTableCell value={formatFinancialValue(financial.capex.value)} margin={financial.capex.margin} yoy={financial.capex.yoy} qoq={financial.capex.qoq} fontSize={fontSize}/>
                        <FinancialTableCell value={formatFinancialValue(financial.freeCashFlow.value)} margin={financial.freeCashFlow.margin} yoy={financial.freeCashFlow.yoy} qoq={financial.freeCashFlow.qoq} fontSize={fontSize}/>
                    </TableRow>
                ))}
            </TableBody>
        </Table>
    </TableContainer>
);

export const PeriodFinancials = ({ttm, financials = [], onOpen, sx}) => {
    if (!ttm) return null;

    const completePeriods = latestCompletePeriods(financials);
    const showDividend = !isNotAValue(ttm.dividend)
        && hasCompleteValues(completePeriods, financial => financial.dividend);
    const showCapex = !isNotAValue(ttm.capex?.value)
        && hasCompleteValues(completePeriods, financial => financial.capex?.value);
    const showFreeCashFlow = !isNotAValue(ttm.freeCashFlow?.value)
        && hasCompleteValues(completePeriods, financial => financial.freeCashFlow?.value);

    return (
        <Box
            sx={{
                ...sx,
                maxWidth: "520px",
                borderLeft: "3px solid",
                borderColor: "success.main",
                bgcolor: "rgba(46, 125, 50, 0.04)",
                borderRadius: "0 4px 4px 0",
            }}
        >
            <ButtonBase
                aria-label="Open financials"
                onClick={onOpen}
                sx={{width: "100%", padding: "5px 7px", textAlign: "left", alignItems: "flex-start", gap: "7px"}}
            >
                <Box sx={{display: "flex", color: "success.main", marginTop: "1px", flexShrink: 0}}>
                    <FinancialsIcon width="17" height="17"/>
                </Box>
                <Box sx={{flex: 1, minWidth: 0}}>
                    <Box sx={{display: "flex", alignItems: "center", gap: "6px", marginBottom: "2px", color: "text.secondary", fontSize: 11}}>
                        <Box component="span" sx={{fontWeight: 600, color: "text.primary"}}>Financials</Box>
                    </Box>
                    <Box sx={{maxWidth: "100%", overflowX: {xs: "auto", sm: "visible"}, overflowY: "hidden"}}>
                        <Grid
                            container
                            wrap="nowrap"
                            direction="row"
                            justifyContent="flex-start"
                            alignItems="stretch"
                            sx={{width: "max-content", minWidth: "100%"}}
                        >
                            <FinancialSummaryItem first value={ttm.revenue.value} label="revenue"/>
                            {!isNotAValue(ttm.grossProfit?.value) &&
                                <FinancialSummaryItem value={ttm.grossProfit.value} label="gross profit" margin={ttm.grossProfit.margin}/>
                            }
                            {!isNotAValue(ttm.operatingIncome?.value) &&
                                <FinancialSummaryItem value={ttm.operatingIncome.value} label="op. income" margin={ttm.operatingIncome.margin}/>
                            }
                            <FinancialSummaryItem value={ttm.netIncome.value} label="net income" margin={ttm.netIncome.margin}/>
                            {showDividend &&
                                <FinancialSummaryItem value={ttm.dividend} label="dividend" margin={ttm.dividendMargin}/>
                            }
                            {showCapex &&
                                <FinancialSummaryItem value={ttm.capex.value} label="capex" margin={ttm.capex.margin}/>
                            }
                            {showFreeCashFlow &&
                                <FinancialSummaryItem value={ttm.freeCashFlow.value} label="fcf" margin={ttm.freeCashFlow.margin}/>
                            }
                        </Grid>
                    </Box>
                </Box>
                <ChevronRightIcon sx={{fontSize: 17, color: "text.secondary", marginTop: "1px", flexShrink: 0}}/>
            </ButtonBase>
        </Box>
    );
};
