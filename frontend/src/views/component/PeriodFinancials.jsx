import {
    Box,
    Button,
    Grid,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
} from "@mui/material";
import React from "react";
import {ReactComponent as FinancialsIcon} from "../../assets/icons/financials.svg";
import {
    formatDecimals,
    formatMillions,
    formatPercent,
    formatPeriodName,
    isNotAValue,
} from "../../service/FormattingService";

const headers = ["Period", "Revenue", "Gross Profit", "Operating Income", "Net Income", "Dividend"];

const formatDividend = (value) => value === 0 || isNotAValue(value) ? "-" : formatMillions(value) || "-";

const FinancialSummaryItem = ({value, label, margin, first = false}) => (
    <Box sx={{marginLeft: first ? {xs: 0, sm: "5px"} : "10px", flexShrink: 0}}>
        <Box sx={{fontSize: 9, textAlign: "center", marginBottom: "0px"}}>({formatDecimals(margin, 0, 0)}%)</Box>
        <Box sx={{fontWeight: "bold", fontSize: 13, textAlign: "center"}}>{formatMillions(value)}</Box>
        <Box sx={{color: "lightgrey", fontWeight: "bold", mx: 0.5, fontSize: 12, textAlign: "center"}}>{label}</Box>
    </Box>
);

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
        <Table size="small" aria-label="financials table" stickyHeader sx={{minWidth: 650}}>
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
                        <FinancialTableCell value={formatMillions(financial.grossProfit.value)} margin={financial.grossProfit.margin} yoy={financial.grossProfit.yoy} qoq={financial.grossProfit.qoq} fontSize={fontSize}/>
                        <FinancialTableCell value={formatMillions(financial.operatingIncome.value)} margin={financial.operatingIncome.margin} yoy={financial.operatingIncome.yoy} qoq={financial.operatingIncome.qoq} fontSize={fontSize}/>
                        <FinancialTableCell value={formatMillions(financial.netIncome.value)} margin={financial.netIncome.margin} yoy={financial.netIncome.yoy} qoq={financial.netIncome.qoq} fontSize={fontSize}/>
                        <FinancialTableCell value={formatDividend(financial.dividend)} fontSize={fontSize}/>
                    </TableRow>
                ))}
            </TableBody>
        </Table>
    </TableContainer>
);

export const PeriodFinancials = ({ttm, onOpen, sx}) => (
    <Paper
        elevation={0}
        sx={{
            ...sx,
            marginLeft: {xs: "-5px", sm: 0},
            width: {xs: "calc(100% + 5px)", sm: "100%"},
            maxWidth: {xs: "calc(100% + 5px)", sm: "100%"},
            overflowX: "auto",
            overflowY: "hidden",
            "& .overview-action": {
                opacity: {xs: 1, sm: 0},
                pointerEvents: {xs: "auto", sm: "none"},
                width: {xs: "36px", sm: 0},
                padding: {xs: "6px", sm: 0},
                overflow: "hidden",
                transition: "opacity 120ms ease-in-out, width 120ms ease-in-out, padding 120ms ease-in-out",
            },
            "&:hover .overview-action, &:focus-within .overview-action": {
                opacity: 1,
                pointerEvents: "auto",
                width: "36px",
                padding: "6px",
            },
        }}
    >
        <Grid
            container
            wrap="nowrap"
            direction="row"
            justifyContent="flex-start"
            alignItems="stretch"
            sx={{width: "max-content", minWidth: "100%"}}
        >
            {ttm &&
                <>
                    <FinancialSummaryItem first value={ttm.revenue.value} label="revenue" margin={ttm.revenue.margin}/>
                    <FinancialSummaryItem value={ttm.grossProfit.value} label="gross profit" margin={ttm.grossProfit.margin}/>
                    <FinancialSummaryItem value={ttm.operatingIncome.value} label="op. income" margin={ttm.operatingIncome.margin}/>
                    <FinancialSummaryItem value={ttm.netIncome.value} label="net income" margin={ttm.netIncome.margin}/>
                    <Button className="overview-action" aria-label="Open financials" sx={{minWidth: 0, height: "36px", color: "primary.main", flexShrink: 0}} onClick={onOpen}>
                        <FinancialsIcon width="24" height="24"/>
                    </Button>
                </>
            }
        </Grid>
    </Paper>
);
