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
    InputBase,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TextField,
} from "@mui/material";
import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import ArrowDropUpIcon from "@mui/icons-material/ArrowDropUp";
import SaveOutlinedIcon from "@mui/icons-material/SaveOutlined";
import useMediaQuery from "@mui/material/useMediaQuery";
import axios from "axios";
import React, {useEffect, useRef, useState} from "react";
import {backend} from "../properties";
import {formatDate, formatDecimals, formatError, formatPercent, formatPeriodName} from "../service/FormattingService";
import {validateNumber} from "../service/ValidationService";

const priceRows = [
    {label: "t + 20%", factor: 1.20},
    {label: "t + 10%", factor: 1.10},
    {label: "t + 5%", factor: 1.05},
    {label: "target ~", factor: 1, target: true},
    {label: "t - 5%", factor: 0.95},
    {label: "t - 10%", factor: 0.90},
    {label: "t - 20%", factor: 0.80},
];

const peRows = [
    {label: "t + 15", adjustment: 15},
    {label: "t + 10", adjustment: 10},
    {label: "t + 5", adjustment: 5},
    {label: "target ~", adjustment: 0, target: true},
    {label: "t - 5", adjustment: -5},
    {label: "t - 10", adjustment: -10},
    {label: "t - 15", adjustment: -15},
];

const earningsColumns = [
    {key: "ttm", label: "ttm"},
    {key: "current", label: "current"},
    {key: "next1", label: "next 1"},
    {key: "next2", label: "next 2"},
    {key: "next3", label: "next 3"},
];

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

const peFromPrice = (price, earnings) => {
    const earningsValue = numberValue(earnings);
    if (price === null || earningsValue === null || earningsValue === 0) return null;
    return price / earningsValue;
};

const priceToEarnings = (price, earnings) => {
    const pe = peFromPrice(price, earnings);
    return pe === null ? "-" : formatDecimals(pe, 2, 2) || "-";
};

const projectedPe = (targetPe, adjustment) => {
    const pe = numberValue(targetPe);
    return pe === null ? null : pe + adjustment;
};

const earningsToPrice = (pe, earnings) => {
    const earningsValue = numberValue(earnings);
    if (pe === null || earningsValue === null) return "-";
    return formatDecimals(pe * earningsValue, 0, 2) || "-";
};

const inputNumber = value => String(Math.round(value * 100000000) / 100000000);
const estimateInputPattern = /^-?(?:\d+(?:\.\d*)?|\.\d*)?$/;
const persistedEstimateKeys = ["current", "next1", "next2", "next3"];

const currentDate = () => {
    const date = new Date();
    return [
        date.getFullYear(),
        String(date.getMonth() + 1).padStart(2, "0"),
        String(date.getDate()).padStart(2, "0"),
    ].join("-");
};

const ProjectionInput = ({value, onChange, onBlur, onStep, label, min}) => (
    <InputBase
        type="number"
        value={value}
        onChange={onChange}
        onBlur={onBlur}
        onKeyDown={event => {
            if (event.key === "ArrowUp" || event.key === "ArrowDown") {
                event.preventDefault();
                onStep(event.key === "ArrowUp" ? 1 : -1);
            }
        }}
        endAdornment={
            <Box
                className="projection-stepper"
                sx={{
                    display: "flex",
                    flexDirection: "column",
                    opacity: 0,
                    pointerEvents: "none",
                }}
            >
                <IconButton
                    aria-label={`Increase ${label.toLowerCase()}`}
                    onMouseDown={event => event.preventDefault()}
                    onClick={() => onStep(1)}
                    tabIndex={-1}
                    sx={{width: 18, height: 11, padding: 0, color: "#555"}}
                >
                    <ArrowDropUpIcon sx={{fontSize: 17}}/>
                </IconButton>
                <IconButton
                    aria-label={`Decrease ${label.toLowerCase()}`}
                    onMouseDown={event => event.preventDefault()}
                    onClick={() => onStep(-1)}
                    tabIndex={-1}
                    sx={{width: 18, height: 11, padding: 0, color: "#555"}}
                >
                    <ArrowDropDownIcon sx={{fontSize: 17}}/>
                </IconButton>
            </Box>
        }
        inputProps={{inputMode: "decimal", min, step: "any", "aria-label": label}}
        sx={{
            width: "100%",
            color: "#111",
            fontSize: "inherit",
            "& input": {padding: "4px 2px 4px 8px", textAlign: "right"},
            "& input::-webkit-outer-spin-button, & input::-webkit-inner-spin-button": {
                WebkitAppearance: "none",
                margin: 0,
            },
            "& input[type=number]": {MozAppearance: "textfield"},
            "&:focus-within .projection-stepper": {opacity: 1, pointerEvents: "auto"},
        }}
    />
);

export const EarningsProjectionsDialog = ({
    open,
    handleClose,
    triggerRefresh,
    ticker,
    currentPrice,
    latestPeriod,
    previousPeriod,
}) => {
    const isNarrowScreen = useMediaQuery("(max-width:599.95px)");
    const [targetPrice, setTargetPrice] = useState("");
    const [targetPe, setTargetPe] = useState("30");
    const [forecastAdjustment, setForecastAdjustment] = useState("0");
    const [estimateValues, setEstimateValues] = useState({});
    const [persistedEstimateValues, setPersistedEstimateValues] = useState({});
    const [openPersistConfirmation, setOpenPersistConfirmation] = useState(false);
    const [persistDate, setPersistDate] = useState("");
    const [savingEstimate, setSavingEstimate] = useState(false);
    const [saveError, setSaveError] = useState(null);
    const [tableScrollFades, setTableScrollFades] = useState({top: false, bottom: false});
    const tablesScrollRef = useRef(null);
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

    const updateTableScrollFades = () => {
        const element = tablesScrollRef.current;
        if (!element) return;
        setTableScrollFades({
            top: element.scrollTop > 1,
            bottom: element.scrollTop + element.clientHeight < element.scrollHeight - 1,
        });
    };

    useEffect(() => {
        if (open) {
            const price = numberValue(currentPrice);
            setTargetPrice(price === null ? "" : price.toFixed(2));
            setTargetPe("30");
            setForecastAdjustment("0");
            const values = Object.fromEntries(estimateFields.map(field => {
                const value = latestPeriod?.estimate?.[field.key];
                return [field.key, numberValue(value) === null ? "" : String(value)];
            }));
            setEstimateValues(values);
            setPersistedEstimateValues(Object.fromEntries(persistedEstimateKeys.map(key => [key, values[key]])));
            setOpenPersistConfirmation(false);
            setSaveError(null);
        }
    }, [open, currentPrice, latestPeriod]);

    useEffect(() => {
        if (!open || !tablesScrollRef.current) return undefined;

        const element = tablesScrollRef.current;
        const frame = window.requestAnimationFrame(updateTableScrollFades);
        const transitionFallback = window.setTimeout(updateTableScrollFades, 300);
        window.addEventListener("resize", updateTableScrollFades);
        const resizeObserver = typeof ResizeObserver === "undefined"
            ? null
            : new ResizeObserver(updateTableScrollFades);
        resizeObserver?.observe(element);
        if (element.firstElementChild) resizeObserver?.observe(element.firstElementChild);

        return () => {
            window.cancelAnimationFrame(frame);
            window.clearTimeout(transitionFallback);
            window.removeEventListener("resize", updateTableScrollFades);
            resizeObserver?.disconnect();
        };
        // The fade state also refreshes from the dialog transition and scroll events.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open, previousPriceRows.length]);

    const baseEstimateSequence = estimateFields.map(field => numberValue(estimateValues[field.key]));
    const adjustment = numberValue(forecastAdjustment) ?? 0;
    const estimateSequence = baseEstimateSequence.map((value, index) => {
        if (value === null || index < 4) return value;
        return Math.round(value * (1 + adjustment / 100) * 100) / 100;
    });
    const rollingEarnings = (sequence, offset) => {
        const values = sequence.slice(offset, offset + 4);
        return values.length === 4 && !values.includes(null)
            ? values.reduce((sum, value) => sum + value, 0)
            : null;
    };
    const earnings = Object.fromEntries(earningsColumns.map((column, index) => [
        column.key,
        {value: rollingEarnings(estimateSequence, index)},
    ]));
    const baseRollingEarnings = earningsColumns.map((column, index) =>
        rollingEarnings(baseEstimateSequence, index));
    const rollingChanges = Object.fromEntries(persistedEstimateKeys.map((key, index) => {
        const previous = baseRollingEarnings[index];
        const current = baseRollingEarnings[index + 1];
        const change = previous === null || current === null || previous === 0
            ? null
            : (current / previous - 1) * 100;
        return [key, change];
    }));
    const previousFourEarnings = baseRollingEarnings[0];
    const nextFourEarnings = baseRollingEarnings[4];
    const yearOverYearChange = previousFourEarnings === null
        || nextFourEarnings === null
        || previousFourEarnings === 0
        ? null
        : (nextFourEarnings / previousFourEarnings - 1) * 100;
    const allPersistedValuesValid = persistedEstimateKeys.every(key =>
        validateNumber(estimateValues[key] ?? "", false, 6, 2, true) === "");
    const persistedValuesChanged = persistedEstimateKeys.some(key =>
        numberValue(estimateValues[key]) !== numberValue(persistedEstimateValues[key]));
    const canPersistEstimate = Boolean(latestPeriod?.id)
        && allPersistedValuesValid
        && persistedValuesChanged
        && !savingEstimate;

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

    const stepTargetPe = direction => {
        const pe = numberValue(targetPe);
        if (pe === null || pe < 15) {
            setTargetPe("16");
            return;
        }
        setTargetPe(inputNumber(Math.max(15, pe + direction * 5)));
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
        axios.post(`${backend}/estimate/${latestPeriod.id}`, {
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
                transition: {onEntered: updateTableScrollFades},
            }}
        >
            <DialogTitle>
                {ticker} - {periodName || "-"} - {isNarrowScreen ? "E&P Projections" : "Earnings and Prices Projections"}
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
                            gridTemplateColumns: {xs: "repeat(4, 60px)", sm: "repeat(8, 60px)"},
                            columnGap: "20px",
                            flex: "0 0 auto",
                        }}
                    >
                        {estimateFields.map(field => {
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
                                    display: field.key.startsWith("past") ? {xs: "none", sm: "inline-flex"} : undefined,
                                }}
                                FormHelperTextProps={{
                                    sx: {
                                        fontSize: showRollingChange && !missing ? 12 : 10,
                                        color: showRollingChange && !missing ? "text.primary" : undefined,
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
                </Box>
                <Box sx={{width: "130px", marginBottom: 2}}>
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
                <Box sx={{position: "relative", flex: "1 1 auto", minHeight: 0, overflow: "hidden"}}>
                <Box
                    ref={tablesScrollRef}
                    data-testid="projection-tables-scroll"
                    onScroll={updateTableScrollFades}
                    sx={{position: "absolute", inset: 0, overflowY: "auto"}}
                >
                <Box>
                <TableContainer>
                    <Table
                        size="small"
                        aria-label="earnings and price projections"
                        sx={{
                            tableLayout: "fixed",
                            width: "auto",
                        }}
                    >
                        <colgroup>
                            <col style={{width: columnWidth}}/>
                            <col style={{width: columnWidth}}/>
                            {earningsColumns.map(column => (
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
                                    P/E (TTM)
                                </TableCell>
                                <TableCell colSpan={4} sx={{border, backgroundColor: headerColor, color: "#111"}}>
                                    P/E (forward)
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
                                        {earningsColumns.map(column => (
                                            <TableCell
                                                key={column.key}
                                                align="right"
                                                aria-label={`${row.label} ${column.label} P/E`}
                                                sx={{
                                                    border,
                                                    backgroundColor: row.target ? targetRatioColor : ratioColor,
                                                    color: "#111",
                                                }}
                                            >
                                                {priceToEarnings(price, earnings?.[column.key]?.value)}
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
                        aria-label="price projections by P/E"
                        sx={{tableLayout: "fixed", width: "auto"}}
                    >
                        <colgroup>
                            <col style={{width: columnWidth}}/>
                            <col style={{width: columnWidth}}/>
                            {earningsColumns.map(column => (
                                <col key={column.key} style={{width: columnWidth}}/>
                            ))}
                        </colgroup>
                        <TableHead>
                            <TableRow>
                                <TableCell sx={{border}}/>
                                <TableCell align="center" sx={{border, backgroundColor: headerColor, color: "#111"}}>
                                    P/E
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
                            {peRows.map(row => {
                                const pe = projectedPe(targetPe, row.adjustment);
                                return (
                                    <TableRow key={row.label}>
                                        <TableCell sx={{border, backgroundColor: priceColor, color: "#111", whiteSpace: "nowrap"}}>
                                            {row.label}
                                        </TableCell>
                                        <TableCell
                                            align="right"
                                            aria-label={`${row.label} P/E`}
                                            sx={{border, backgroundColor: row.target ? "#fff" : priceColor, color: "#111", padding: row.target ? 0 : undefined}}
                                        >
                                            {row.target
                                                ? <ProjectionInput
                                                    value={targetPe}
                                                    onChange={event => setTargetPe(event.target.value)}
                                                    onStep={stepTargetPe}
                                                    label="Target P/E"
                                                    min="15"
                                                />
                                                : formatDecimals(pe, 0, 2) || "-"}
                                        </TableCell>
                                        {earningsColumns.map(column => (
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
                                                {earningsToPrice(pe, earnings?.[column.key]?.value)}
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
                                {earningsColumns.map(column => (
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
                                        P/E (TTM)
                                    </TableCell>
                                    <TableCell colSpan={4} sx={{border, backgroundColor: headerColor, color: "#111"}}>
                                        Price (Forward with fixed P/E)
                                    </TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {previousPriceRows.map(row => {
                                    const pe = peFromPrice(row.price, earnings?.ttm?.value);
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
                                                aria-label={`${row.label} P/E`}
                                                sx={{border, backgroundColor: priceColor, color: "#111"}}
                                            >
                                                {pe === null ? "-" : formatDecimals(pe, 2, 2) || "-"}
                                            </TableCell>
                                            {earningsColumns.slice(1).map(column => (
                                                <TableCell
                                                    key={column.key}
                                                    align="right"
                                                    aria-label={`${row.label} ${column.label} price`}
                                                    sx={{border, backgroundColor: ratioColor, color: "#111"}}
                                                >
                                                    {earningsToPrice(pe, earnings?.[column.key]?.value)}
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
                {tableScrollFades.top &&
                    <Box
                        data-testid="projection-tables-top-fade"
                        sx={{
                            position: "absolute",
                            zIndex: 1,
                            pointerEvents: "none",
                            top: 0,
                            left: 0,
                            right: "8px",
                            height: "24px",
                            background: theme => `linear-gradient(to bottom, ${theme.palette.background.paper}, transparent)`,
                        }}
                    />
                }
                {tableScrollFades.bottom &&
                    <Box
                        data-testid="projection-tables-bottom-fade"
                        sx={{
                            position: "absolute",
                            zIndex: 1,
                            pointerEvents: "none",
                            bottom: 0,
                            left: 0,
                            right: "8px",
                            height: "24px",
                            background: theme => `linear-gradient(to top, ${theme.palette.background.paper}, transparent)`,
                        }}
                    />
                }
                </Box>
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
            <DialogTitle>Persist Estimate</DialogTitle>
            <DialogContent>
                <Box>
                    Do you want to persist new estimate values for {ticker} {periodName} as of {formatDate(persistDate)}?
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
