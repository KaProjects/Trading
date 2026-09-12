import {
    Alert,
    AlertTitle,
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    IconButton,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TextField,
} from "@mui/material";
import SaveOutlinedIcon from "@mui/icons-material/SaveOutlined";
import useMediaQuery from "@mui/material/useMediaQuery";
import axios from "axios";
import React, {useEffect, useState} from "react";
import {backend} from "../properties";
import {
    formatDate,
    formatDecimals,
    formatError,
    formatMillionsRounded,
    formatPercent,
    formatPeriodName,
} from "../service/FormattingService";
import {validateNumber} from "../service/ValidationService";
import {ProjectionInput} from "./component/ProjectionInput";

const priceRows = [
    {label: "t + 20%", factor: 1.20},
    {label: "t + 10%", factor: 1.10},
    {label: "t + 5%", factor: 1.05},
    {label: "target ~", factor: 1, target: true},
    {label: "t - 5%", factor: 0.95},
    {label: "t - 10%", factor: 0.90},
    {label: "t - 20%", factor: 0.80},
];

const psRows = [
    {label: "t + 6", adjustment: 6},
    {label: "t + 4", adjustment: 4},
    {label: "t + 2", adjustment: 2},
    {label: "target ~", adjustment: 0, target: true},
    {label: "t - 2", adjustment: -2},
    {label: "t - 4", adjustment: -4},
    {label: "t - 6", adjustment: -6},
];

const revenueColumns = [
    {key: "ttm", label: "ttm"},
    {key: "current", label: "current"},
    {key: "next1", label: "next 1"},
    {key: "next2", label: "next 2"},
    {key: "next3", label: "next 3"},
];

const marginRows = [
    {key: "grossProfit", label: "Gross profit", shortLabel: "Gross P.", expenseLabel: "Gross exp. (%)"},
    {key: "operatingIncome", label: "Oper. income", shortLabel: "Op. Inc.", expenseLabel: "Oper. exp. (%)"},
    {key: "netIncome", label: "Net income", shortLabel: "Net Inc.", expenseLabel: "Non-op. exp. (%)"},
];

const financialCellSx = {padding: "6px 6px"};

const FinancialCell = ({name, children}) => (
    <Box sx={{display: "flex", alignItems: "baseline", justifyContent: "space-between", gap: "3px"}}>
        <Box component="span" sx={{fontSize: 10, opacity: 0.7, whiteSpace: "nowrap"}}>({name})</Box>
        <Box component="span" sx={{whiteSpace: "nowrap"}}>{children}</Box>
    </Box>
);

const EMPTY_EXPENSE_ADJUSTMENTS = {grossProfit: "0", operatingIncome: "0", netIncome: "0"};

const estimateFields = [
    {key: "past4", label: "Past 4"},
    {key: "past3", label: "Past 3"},
    {key: "past2", label: "Past 2"},
    {key: "past1", label: "Past 1"},
    {key: "current", label: "Current"},
    {key: "next1", label: "Next 1"},
    {key: "next2", label: "Next 2"},
    {key: "next3", label: "Next 3"},
];

const border = "1px solid rgba(0, 0, 0, 0.35)";
const headerColor = "#93c47d";
const priceColor = "#b6d7a8";
const ratioColor = "#ffe599";
const targetRatioColor = "#ffd966";
const columnWidth = 110;

const numberValue = value => {
    if (value === null || value === undefined || String(value).trim() === "") return null;
    const number = Number(String(value).replace(",", "."));
    return Number.isFinite(number) ? number : null;
};

const projectedPrice = (targetPrice, factor) => {
    const price = numberValue(targetPrice);
    return price === null ? null : price * factor;
};

const psFromPrice = (price, revenuePerShare) => {
    if (price === null || revenuePerShare === null || revenuePerShare === 0) return null;
    return price / revenuePerShare;
};

const priceToSales = (price, revenuePerShare) => {
    const ps = psFromPrice(price, revenuePerShare);
    return ps === null ? "-" : formatDecimals(ps, 2, 2) || "-";
};

const projectedPs = (targetPs, adjustment) => {
    const ps = numberValue(targetPs);
    return ps === null ? null : ps + adjustment;
};

const salesToPrice = (ps, revenuePerShare) => {
    if (ps === null || revenuePerShare === null) return "-";
    return formatDecimals(ps * revenuePerShare, 0, 2) || "-";
};

const inputNumber = value => String(Math.round(value * 100000000) / 100000000);
const estimateInputPattern = /^-?(?:\d+(?:\.\d*)?|\.\d*)?$/;
const persistedEstimateKeys = ["current", "next1", "next2", "next3"];
const editableEstimateFields = estimateFields.filter(field => persistedEstimateKeys.includes(field.key));

const currentDate = () => {
    const date = new Date();
    return [
        date.getFullYear(),
        String(date.getMonth() + 1).padStart(2, "0"),
        String(date.getDate()).padStart(2, "0"),
    ].join("-");
};

export const RevenueProjectionsDialog = ({
    open,
    handleClose,
    triggerRefresh,
    ticker,
    currentPrice,
    ttm,
    latestPeriod,
    previousPeriod,
}) => {
    const isNarrowScreen = useMediaQuery("(max-width:599.95px)");
    const [targetPrice, setTargetPrice] = useState("");
    const [targetPs, setTargetPs] = useState("10");
    const [forecastAdjustment, setForecastAdjustment] = useState("0");
    const [expenseAdjustments, setExpenseAdjustments] = useState(EMPTY_EXPENSE_ADJUSTMENTS);
    const [estimateValues, setEstimateValues] = useState({});
    const [persistedEstimateValues, setPersistedEstimateValues] = useState({});
    const [openPersistConfirmation, setOpenPersistConfirmation] = useState(false);
    const [persistDate, setPersistDate] = useState("");
    const [savingEstimate, setSavingEstimate] = useState(false);
    const [saveError, setSaveError] = useState(null);
    const shares = numberValue(latestPeriod?.shares) ?? numberValue(previousPeriod?.shares);
    const previousPriceHigh = numberValue(previousPeriod?.priceHigh);
    const previousPriceLow = numberValue(previousPeriod?.priceLow);
    const previousPriceRows = previousPriceHigh !== null && previousPriceLow !== null
        ? [
            {label: "High", price: previousPriceHigh},
            {label: "Low", price: previousPriceLow},
        ]
        : [];
    const periodName = typeof latestPeriod?.name === "string"
        ? latestPeriod.name
        : formatPeriodName(latestPeriod?.name);

    useEffect(() => {
        if (open) {
            const price = numberValue(currentPrice);
            setTargetPrice(price === null ? "" : price.toFixed(2));
            setTargetPs("10");
            setForecastAdjustment("0");
            setExpenseAdjustments(EMPTY_EXPENSE_ADJUSTMENTS);
            const values = Object.fromEntries(estimateFields.map(field => {
                const value = latestPeriod?.revenueEstimate?.[field.key];
                return [field.key, numberValue(value) === null ? "" : String(value)];
            }));
            setEstimateValues(values);
            setPersistedEstimateValues(Object.fromEntries(persistedEstimateKeys.map(key => [key, values[key]])));
            setOpenPersistConfirmation(false);
            setSaveError(null);
        }
    }, [open, currentPrice, latestPeriod]);

    const baseEstimateSequence = estimateFields.map(field => numberValue(estimateValues[field.key]));
    const adjustment = numberValue(forecastAdjustment) ?? 0;
    const estimateSequence = baseEstimateSequence.map((value, index) => {
        if (value === null || index < 4) return value;
        return Math.round(value * (1 + adjustment / 100) * 100) / 100;
    });
    const rollingRevenue = (sequence, offset) => {
        const values = sequence.slice(offset, offset + 4);
        return values.length === 4 && !values.includes(null)
            ? values.reduce((sum, value) => sum + value, 0)
            : null;
    };
    const perShare = total => total === null || shares === null || shares === 0 ? null : total / shares;
    const revenues = Object.fromEntries(revenueColumns.map((column, index) => {
        const total = rollingRevenue(estimateSequence, index);
        return [column.key, {value: total, perShare: perShare(total)}];
    }));
    const baseRollingRevenue = revenueColumns.map((column, index) =>
        rollingRevenue(baseEstimateSequence, index));
    const rollingChanges = Object.fromEntries(persistedEstimateKeys.map((key, index) => {
        const previous = baseRollingRevenue[index];
        const current = baseRollingRevenue[index + 1];
        const change = previous === null || current === null || previous === 0
            ? null
            : (current / previous - 1) * 100;
        return [key, change];
    }));
    const previousFourRevenue = baseRollingRevenue[0];
    const nextFourRevenue = baseRollingRevenue[4];
    const yearOverYearChange = previousFourRevenue === null
        || nextFourRevenue === null
        || previousFourRevenue === 0
        ? null
        : (nextFourRevenue / previousFourRevenue - 1) * 100;
    const allPersistedValuesValid = persistedEstimateKeys.every(key =>
        validateNumber(estimateValues[key] ?? "", false, 8, 2, true) === "");
    const persistedValuesChanged = persistedEstimateKeys.some(key =>
        numberValue(estimateValues[key]) !== numberValue(persistedEstimateValues[key]));
    const canPersistEstimate = Boolean(latestPeriod?.id)
        && allPersistedValuesValid
        && persistedValuesChanged
        && !savingEstimate;

    const ttmValue = key => {
        const value = ttm?.[key]?.value;
        return value === null || value === undefined ? null : Number(value);
    };
    const baselineRevenue = ttmValue("revenue") ?? revenues.ttm?.value ?? null;
    const ttmGrossProfit = ttmValue("grossProfit");
    const ttmOperatingIncome = ttmValue("operatingIncome");
    const ttmNetIncome = ttmValue("netIncome");
    const marginOf = (value, revenue) => value === null
        || value === undefined
        || value < 0
        || revenue === null
        || revenue <= 0
        ? null
        : value / revenue * 100;
    const expenseFactor = key => 1 + (numberValue(expenseAdjustments[key]) ?? 0) / 100;
    const projectedFinancials = (projectedRevenue, quartersForward) => {
        if (projectedRevenue === null || baselineRevenue === null || baselineRevenue === 0) {
            return {grossProfit: null, operatingIncome: null, netIncome: null};
        }

        const revenueGrowth = projectedRevenue / baselineRevenue - 1;
        const grossExpenseGrowth = 1 + revenueGrowth * expenseFactor("grossProfit");
        const grossProfit = ttmGrossProfit === null
            ? null
            : projectedRevenue - (baselineRevenue - ttmGrossProfit) * grossExpenseGrowth;
        const operatingIncome = grossProfit === null || ttmOperatingIncome === null
            ? null
            : grossProfit - (ttmGrossProfit - ttmOperatingIncome)
                * Math.pow(expenseFactor("operatingIncome"), quartersForward);
        const netIncome = operatingIncome === null || ttmNetIncome === null
            ? null
            : operatingIncome - (ttmOperatingIncome - ttmNetIncome)
                * Math.pow(expenseFactor("netIncome"), quartersForward);
        return {grossProfit, operatingIncome, netIncome};
    };
    const projectionsByColumn = Object.fromEntries(revenueColumns.slice(1).map((column, index) => [
        column.key,
        projectedFinancials(revenues?.[column.key]?.value ?? null, index + 1),
    ]));
    const formatMargin = value => value === null ? "-" : formatPercent(value, false, 1) || "-";

    const stepExpenseAdjustment = (key, direction) => {
        const value = numberValue(expenseAdjustments[key]) ?? 0;
        setExpenseAdjustments(adjustments => ({
            ...adjustments,
            [key]: inputNumber(value + direction * 5),
        }));
    };

    const stepTargetPrice = direction => {
        const price = numberValue(targetPrice);
        if (price === null || price <= 0) {
            setTargetPrice("1.00");
            return;
        }
        setTargetPrice((price * (direction > 0 ? 1.05 : 0.95)).toFixed(2));
    };

    const formatTargetPrice = () => {
        const price = numberValue(targetPrice);
        if (price !== null) setTargetPrice((price > 0 ? price : 1).toFixed(2));
    };

    const stepTargetPs = direction => {
        const ps = numberValue(targetPs);
        if (ps === null || ps < 1) {
            setTargetPs("2");
            return;
        }
        setTargetPs(inputNumber(Math.max(1, ps + direction * 2)));
    };

    const stepForecastAdjustment = direction => {
        const value = numberValue(forecastAdjustment) ?? 0;
        setForecastAdjustment(inputNumber(value + direction * 5));
    };

    const openPersistEstimateConfirmation = () => {
        setPersistDate(currentDate());
        setSaveError(null);
        setOpenPersistConfirmation(true);
    };

    const persistEstimate = () => {
        if (!canPersistEstimate) return;

        setSavingEstimate(true);
        setSaveError(null);
        axios.post(`${backend}/estimate/${latestPeriod.id}/revenue`, {
            date: persistDate,
            current: estimateValues.current,
            next1: estimateValues.next1,
            next2: estimateValues.next2,
            next3: estimateValues.next3,
        })
            .then(() => {
                setPersistedEstimateValues(Object.fromEntries(
                    persistedEstimateKeys.map(key => [key, estimateValues[key]])));
                setOpenPersistConfirmation(false);
                triggerRefresh?.();
            })
            .catch(error => setSaveError(formatError(error)))
            .finally(() => setSavingEstimate(false));
    };

    return (
        <>
        <Dialog
            open={open}
            onClose={handleClose}
            fullWidth
            maxWidth="lg"
            slotProps={{
                paper: {sx: {
                    height: {xs: "100%", sm: "calc(100vh - 64px)"},
                    maxHeight: {xs: "none", sm: "900px"},
                }},
            }}
        >
            <DialogTitle>
                {ticker} - {periodName || "-"} - {isNarrowScreen ? "S&P&M Projections" : "Sales and Prices and Margins Projections"}
            </DialogTitle>
            <DialogContent sx={{padding: 2, display: "flex", flex: "1 1 0", flexDirection: "column", overflow: "hidden", minHeight: 0}}>
                <Box
                    data-testid="estimate-settings-scroll"
                    sx={{
                        display: "flex",
                        flex: "0 0 auto",
                        alignItems: "flex-start",
                        gap: 1,
                        marginBottom: 1,
                        paddingTop: 1,
                        overflowX: "auto",
                    }}
                >
                    <Box
                        sx={{
                            display: "grid",
                            gridTemplateColumns: "repeat(4, 80px)",
                            columnGap: "20px",
                            flex: "0 0 auto",
                        }}
                    >
                        {editableEstimateFields.map(field => {
                            const missing = numberValue(estimateValues[field.key]) === null;
                            const showRollingChange = persistedEstimateKeys.includes(field.key);
                            const rollingChange = showRollingChange
                                ? formatPercent(rollingChanges[field.key], true, 1) || "-"
                                : " ";
                            const showYearOverYearChange = field.key === "next3" && !missing;
                            return (
                                <TextField
                                key={field.key}
                                type="text"
                                size="small"
                                variant="standard"
                                label={field.label}
                                value={estimateValues[field.key] ?? ""}
                                onChange={event => {
                                    const value = event.target.value;
                                    if (estimateInputPattern.test(value)) {
                                        setEstimateValues(values => ({
                                            ...values,
                                            [field.key]: value,
                                        }));
                                    }
                                }}
                                error={missing}
                                helperText={missing
                                    ? "Required"
                                    : showYearOverYearChange
                                        ? <>
                                            <Box component="span" sx={{display: "block"}}>{rollingChange}</Box>
                                            <Box component="span" sx={{display: "block", whiteSpace: "nowrap"}}>
                                                ({formatPercent(yearOverYearChange, true, 1) || "-"})
                                            </Box>
                                        </>
                                        : rollingChange}
                                inputProps={{inputMode: "decimal"}}
                                sx={{
                                    "& .MuiInputBase-input": {textAlign: "center", paddingBottom: "2px"},
                                    "& .MuiInputLabel-root": {
                                        width: "100%",
                                        textAlign: "center",
                                        transformOrigin: "top center",
                                    },
                                    "& .MuiInputLabel-root.MuiInputLabel-shrink": {
                                        transform: "translate(0, 0.5px) scale(0.75)",
                                    },
                                }}
                                FormHelperTextProps={{
                                    sx: {
                                        fontSize: showRollingChange && !missing ? 12 : 10,
                                        color: showRollingChange && !missing ? "text.primary" : undefined,
                                        lineHeight: 1.25,
                                        margin: 0,
                                        textAlign: "center",
                                    },
                                }}
                                />
                            );
                        })}
                    </Box>
                    <IconButton
                        aria-label="Save estimate"
                        color="primary"
                        disabled={!canPersistEstimate}
                        onClick={openPersistEstimateConfirmation}
                        sx={{marginTop: "5px"}}
                    >
                        <SaveOutlinedIcon/>
                    </IconButton>
                    <Box sx={{
                        display: {xs: "none", sm: "block"},
                        width: "130px",
                        flex: "0 0 auto",
                        marginLeft: 2,
                    }}>
                        <Box sx={{color: "text.secondary", fontSize: 11, textAlign: "center"}}>
                            Forecast adjustment (%)
                        </Box>
                        <Box sx={{borderBottom: "1px solid rgba(0, 0, 0, 0.42)"}}>
                            <ProjectionInput
                                value={forecastAdjustment}
                                onChange={event => setForecastAdjustment(event.target.value)}
                                onStep={stepForecastAdjustment}
                                label="Forecast adjustment (%)"
                            />
                        </Box>
                    </Box>
                    <Box
                        data-testid="revenue-projection-shares"
                        sx={{
                            display: {xs: "none", sm: "block"},
                            flex: "0 0 auto",
                            marginLeft: 2,
                            marginTop: "18px",
                            color: "text.secondary",
                            fontSize: 11,
                            whiteSpace: "nowrap",
                        }}
                    >
                        Shares: {shares === null ? "-" : formatMillionsRounded(shares)}
                    </Box>
                </Box>
                <Box
                    data-testid="projection-tables-top-fade"
                    sx={{
                        flex: "0 0 auto",
                        height: "8px",
                        marginX: -2,
                        background: "linear-gradient(to bottom, transparent, rgba(0, 0, 0, 0.16))",
                    }}
                />
                <Box
                    data-testid="projection-tables-scroll"
                    sx={{
                        flex: "1 1 auto",
                        minHeight: 0,
                        overflowY: "auto",
                        marginLeft: {xs: -2, sm: 0},
                        marginRight: {xs: -2, sm: 0},
                    }}
                >
                <Box>
                <TableContainer>
                    <Table
                        size="small"
                        aria-label="revenue margin projections"
                        sx={{tableLayout: "fixed", width: columnWidth * (revenueColumns.length + 2)}}
                    >
                        <colgroup>
                            <col style={{width: columnWidth}}/>
                            <col style={{width: columnWidth}}/>
                            <col style={{width: columnWidth}}/>
                            {revenueColumns.slice(1).map(column => (
                                <col key={column.key} style={{width: columnWidth}}/>
                            ))}
                        </colgroup>
                        <TableHead>
                            <TableRow>
                                <TableCell sx={{border}}/>
                                <TableCell sx={{border, ...financialCellSx, backgroundColor: headerColor, color: "#111"}}>
                                    <FinancialCell name="Financials">TTM</FinancialCell>
                                </TableCell>
                                <TableCell align="center" sx={{border, backgroundColor: headerColor, color: "#111"}}>
                                    Margin
                                </TableCell>
                                <TableCell colSpan={4} sx={{border, backgroundColor: headerColor, color: "#111"}}>
                                    Margin (forward)
                                </TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            <TableRow>
                                <TableCell sx={{border, backgroundColor: priceColor, color: "#111", whiteSpace: "nowrap"}}>
                                    Expense adj.
                                </TableCell>
                                <TableCell
                                    align="right"
                                    aria-label="ttm revenues"
                                    sx={{border, ...financialCellSx, backgroundColor: priceColor, color: "#111"}}
                                >
                                    <FinancialCell name="Revenues">
                                        {baselineRevenue === null ? "-" : formatMillionsRounded(baselineRevenue) || "-"}
                                    </FinancialCell>
                                </TableCell>
                                <TableCell
                                    align="right"
                                    aria-label="ttm revenues margin"
                                    sx={{border, backgroundColor: priceColor, color: "#111"}}
                                >
                                    {formatMargin(baselineRevenue === null ? null : 100)}
                                </TableCell>
                                {revenueColumns.slice(1).map(column => (
                                    <TableCell
                                        key={column.key}
                                        align="right"
                                        aria-label={`${column.label} revenues`}
                                        sx={{border, backgroundColor: priceColor, color: "#111"}}
                                    >
                                        {revenues?.[column.key]?.value === null
                                            ? "-"
                                            : formatMillionsRounded(revenues[column.key].value) || "-"}
                                    </TableCell>
                                ))}
                            </TableRow>
                            {marginRows.map(row => {
                                const value = ttmValue(row.key);
                                return (
                                    <TableRow key={row.key}>
                                        <TableCell sx={{border, backgroundColor: "#fff", color: "#111", padding: 0}}>
                                            <Box sx={{
                                                color: "text.secondary",
                                                fontSize: 10,
                                                lineHeight: 1.2,
                                                padding: "2px 5px 0",
                                                whiteSpace: "nowrap",
                                                overflow: "hidden",
                                                textOverflow: "ellipsis",
                                            }}>
                                                {row.expenseLabel}
                                            </Box>
                                            <ProjectionInput
                                                value={expenseAdjustments[row.key]}
                                                onChange={event => setExpenseAdjustments(adjustments => ({
                                                    ...adjustments,
                                                    [row.key]: event.target.value,
                                                }))}
                                                onStep={direction => stepExpenseAdjustment(row.key, direction)}
                                                label={`${row.expenseLabel} adjustment (%)`}
                                            />
                                        </TableCell>
                                        <TableCell
                                            align="right"
                                            aria-label={`ttm ${row.label.toLowerCase()}`}
                                            sx={{border, ...financialCellSx, backgroundColor: priceColor, color: "#111"}}
                                        >
                                            <FinancialCell name={row.shortLabel}>
                                                {value === null ? "-" : formatMillionsRounded(value) || "-"}
                                            </FinancialCell>
                                        </TableCell>
                                        <TableCell
                                            align="right"
                                            aria-label={`ttm ${row.label.toLowerCase()} margin`}
                                            sx={{border, backgroundColor: ratioColor, color: "#111"}}
                                        >
                                            {formatMargin(marginOf(value, baselineRevenue))}
                                        </TableCell>
                                        {revenueColumns.slice(1).map(column => (
                                            <TableCell
                                                key={column.key}
                                                align="right"
                                                aria-label={`${column.label} ${row.label.toLowerCase()} margin`}
                                                sx={{border, backgroundColor: ratioColor, color: "#111"}}
                                            >
                                                {formatMargin(marginOf(
                                                    projectionsByColumn[column.key]?.[row.key] ?? null,
                                                    revenues?.[column.key]?.value ?? null,
                                                ))}
                                            </TableCell>
                                        ))}
                                    </TableRow>
                                );
                            })}
                        </TableBody>
                    </Table>
                </TableContainer>
                <TableContainer sx={{marginTop: "5px"}}>
                    <Table
                        size="small"
                        aria-label="revenue and price projections"
                        sx={{
                            tableLayout: "fixed",
                            width: "auto",
                        }}
                    >
                        <colgroup>
                            <col style={{width: columnWidth}}/>
                            <col style={{width: columnWidth}}/>
                            {revenueColumns.map(column => (
                                <col key={column.key} style={{width: columnWidth}}/>
                            ))}
                        </colgroup>
                        <TableHead>
                            <TableRow>
                                <TableCell sx={{border}}/>
                                <TableCell align="center" sx={{border, backgroundColor: headerColor, color: "#111"}}>
                                    Price
                                </TableCell>
                                <TableCell colSpan={1} sx={{border, backgroundColor: headerColor, color: "#111"}}>
                                    P/S (TTM)
                                </TableCell>
                                <TableCell colSpan={4} sx={{border, backgroundColor: headerColor, color: "#111"}}>
                                    P/S (forward)
                                </TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {priceRows.map(row => {
                                const price = projectedPrice(targetPrice, row.factor);
                                return (
                                    <TableRow key={row.label}>
                                        <TableCell sx={{border, backgroundColor: priceColor, color: "#111", whiteSpace: "nowrap"}}>
                                            {row.label}
                                        </TableCell>
                                        <TableCell
                                            align="right"
                                            aria-label={`${row.label} price`}
                                            sx={{border, backgroundColor: row.target ? "#fff" : priceColor, color: "#111", padding: row.target ? 0 : undefined}}
                                        >
                                            {row.target
                                                ? <ProjectionInput
                                                    value={targetPrice}
                                                    onChange={event => setTargetPrice(event.target.value)}
                                                    onBlur={formatTargetPrice}
                                                    onStep={stepTargetPrice}
                                                    label="Target price"
                                                    min="0.01"
                                                />
                                                : formatDecimals(price, 0, 2) || "-"}
                                        </TableCell>
                                        {revenueColumns.map(column => (
                                            <TableCell
                                                key={column.key}
                                                align="right"
                                                aria-label={`${row.label} ${column.label} P/S`}
                                                sx={{
                                                    border,
                                                    backgroundColor: row.target ? targetRatioColor : ratioColor,
                                                    color: "#111",
                                                }}
                                            >
                                                {priceToSales(price, revenues?.[column.key]?.perShare)}
                                            </TableCell>
                                        ))}
                                    </TableRow>
                                );
                            })}
                        </TableBody>
                    </Table>
                </TableContainer>
                <TableContainer sx={{marginTop: "5px"}}>
                    <Table
                        size="small"
                        aria-label="price projections by P/S"
                        sx={{tableLayout: "fixed", width: "auto"}}
                    >
                        <colgroup>
                            <col style={{width: columnWidth}}/>
                            <col style={{width: columnWidth}}/>
                            {revenueColumns.map(column => (
                                <col key={column.key} style={{width: columnWidth}}/>
                            ))}
                        </colgroup>
                        <TableHead>
                            <TableRow>
                                <TableCell sx={{border}}/>
                                <TableCell align="center" sx={{border, backgroundColor: headerColor, color: "#111"}}>
                                    P/S
                                </TableCell>
                                <TableCell sx={{border, backgroundColor: headerColor, color: "#111"}}>
                                    Price (TTM)
                                </TableCell>
                                <TableCell colSpan={4} sx={{border, backgroundColor: headerColor, color: "#111"}}>
                                    Price (forward)
                                </TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {psRows.map(row => {
                                const ps = projectedPs(targetPs, row.adjustment);
                                return (
                                    <TableRow key={row.label}>
                                        <TableCell sx={{border, backgroundColor: priceColor, color: "#111", whiteSpace: "nowrap"}}>
                                            {row.label}
                                        </TableCell>
                                        <TableCell
                                            align="right"
                                            aria-label={`${row.label} P/S`}
                                            sx={{border, backgroundColor: row.target ? "#fff" : priceColor, color: "#111", padding: row.target ? 0 : undefined}}
                                        >
                                            {row.target
                                                ? <ProjectionInput
                                                    value={targetPs}
                                                    onChange={event => setTargetPs(event.target.value)}
                                                    onStep={stepTargetPs}
                                                    label="Target P/S"
                                                    min="1"
                                                />
                                                : formatDecimals(ps, 0, 2) || "-"}
                                        </TableCell>
                                        {revenueColumns.map(column => (
                                            <TableCell
                                                key={column.key}
                                                align="right"
                                                aria-label={`${row.label} ${column.label} price`}
                                                sx={{
                                                    border,
                                                    backgroundColor: row.target ? targetRatioColor : ratioColor,
                                                    color: "#111",
                                                }}
                                            >
                                                {salesToPrice(ps, revenues?.[column.key]?.perShare)}
                                            </TableCell>
                                        ))}
                                    </TableRow>
                                );
                            })}
                        </TableBody>
                    </Table>
                </TableContainer>
                {previousPriceRows.length > 0 &&
                    <TableContainer sx={{marginTop: "5px"}}>
                        <Table
                            size="small"
                            aria-label="historical price projections"
                            sx={{tableLayout: "fixed", width: "auto"}}
                        >
                            <colgroup>
                                <col style={{width: columnWidth}}/>
                                <col style={{width: columnWidth}}/>
                                {revenueColumns.map(column => (
                                    <col key={column.key} style={{width: columnWidth}}/>
                                ))}
                            </colgroup>
                            <TableHead>
                                <TableRow>
                                    <TableCell sx={{border}}/>
                                    <TableCell sx={{border, backgroundColor: headerColor, color: "#111"}}>
                                        P (Q-1)
                                    </TableCell>
                                    <TableCell sx={{border, backgroundColor: headerColor, color: "#111"}}>
                                        P/S (TTM)
                                    </TableCell>
                                    <TableCell colSpan={4} sx={{border, backgroundColor: headerColor, color: "#111"}}>
                                        Price (Forward with fixed P/S)
                                    </TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {previousPriceRows.map(row => {
                                    const ps = psFromPrice(row.price, revenues?.ttm?.perShare);
                                    return (
                                        <TableRow key={row.label}>
                                            <TableCell sx={{border, backgroundColor: priceColor, color: "#111"}}>
                                                {row.label}
                                            </TableCell>
                                            <TableCell
                                                align="right"
                                                aria-label={`${row.label} ttm price`}
                                                sx={{border, backgroundColor: priceColor, color: "#111"}}
                                            >
                                                {formatDecimals(row.price, 0, 2) || "-"}
                                            </TableCell>
                                            <TableCell
                                                align="right"
                                                aria-label={`${row.label} P/S`}
                                                sx={{border, backgroundColor: priceColor, color: "#111"}}
                                            >
                                                {ps === null ? "-" : formatDecimals(ps, 2, 2) || "-"}
                                            </TableCell>
                                            {revenueColumns.slice(1).map(column => (
                                                <TableCell
                                                    key={column.key}
                                                    align="right"
                                                    aria-label={`${row.label} ${column.label} price`}
                                                    sx={{border, backgroundColor: ratioColor, color: "#111"}}
                                                >
                                                    {salesToPrice(ps, revenues?.[column.key]?.perShare)}
                                                </TableCell>
                                            ))}
                                        </TableRow>
                                    );
                                })}
                            </TableBody>
                        </Table>
                    </TableContainer>
                }
                </Box>
                </Box>
                <Box
                    data-testid="projection-tables-bottom-fade"
                    sx={{
                        flex: "0 0 auto",
                        height: "8px",
                        marginX: -2,
                        background: "linear-gradient(to top, transparent, rgba(0, 0, 0, 0.16))",
                    }}
                />
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose}>Close</Button>
            </DialogActions>
        </Dialog>
        <Dialog
            open={openPersistConfirmation}
            onClose={() => !savingEstimate && setOpenPersistConfirmation(false)}
            maxWidth="sm"
            fullWidth
        >
            <DialogTitle>Persist Revenue Estimate</DialogTitle>
            <DialogContent>
                <Box>
                    Do you want to persist new revenue estimate values for {ticker} {periodName} as of {formatDate(persistDate)}?
                </Box>
                {saveError &&
                    <Alert severity="error" sx={{marginTop: 2}}>
                        <AlertTitle>{saveError.title}</AlertTitle>
                        {saveError.message}
                    </Alert>
                }
            </DialogContent>
            <DialogActions>
                <Button disabled={savingEstimate} onClick={() => setOpenPersistConfirmation(false)}>Cancel</Button>
                <Button disabled={savingEstimate || !canPersistEstimate} onClick={persistEstimate}>Add</Button>
            </DialogActions>
        </Dialog>
        </>
    );
};
