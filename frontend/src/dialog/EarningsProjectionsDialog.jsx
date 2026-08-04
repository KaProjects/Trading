import {
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
} from "@mui/material";
import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import ArrowDropUpIcon from "@mui/icons-material/ArrowDropUp";
import React, {useEffect, useState} from "react";
import {formatDecimals} from "../service/FormattingService";

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

export const EarningsProjectionsDialog = ({open, handleClose, ticker, currentPrice, earnings, previousPeriod}) => {
    const [targetPrice, setTargetPrice] = useState("");
    const [targetPe, setTargetPe] = useState("30");
    const previousPriceHigh = numberValue(previousPeriod?.priceHigh);
    const previousPriceLow = numberValue(previousPeriod?.priceLow);
    const previousPriceRows = previousPriceHigh !== null && previousPriceLow !== null
        ? [
            {label: "P (H, Q-1)", price: previousPriceHigh, separated: true},
            {label: "P (L, Q-1)", price: previousPriceLow},
        ]
        : [];

    useEffect(() => {
        if (open) {
            const price = numberValue(currentPrice);
            setTargetPrice(price === null ? "" : price.toFixed(2));
            setTargetPe("30");
        }
    }, [open, currentPrice]);

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

    return (
        <Dialog open={open} onClose={handleClose} fullWidth maxWidth="lg">
            <DialogTitle>{ticker} - Earnings and Price Projections</DialogTitle>
            <DialogContent sx={{padding: 2}}>
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
                                <TableCell align="center" sx={{border, backgroundColor: priceColor, color: "#111"}}>
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
                <TableContainer sx={{marginTop: 3}}>
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
                                <TableCell align="center" sx={{border, backgroundColor: priceColor, color: "#111"}}>
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
                            {previousPriceRows.map(row => {
                                const pe = peFromPrice(row.price, earnings?.ttm?.value);
                                const separator = row.separated ? {borderTop: "3px solid rgba(0, 0, 0, 0.55)"} : {};
                                return (
                                    <TableRow key={row.label}>
                                        <TableCell sx={{border, ...separator, backgroundColor: priceColor, color: "#111", whiteSpace: "nowrap"}}>
                                            {row.label}
                                        </TableCell>
                                        <TableCell
                                            align="right"
                                            aria-label={`${row.label} P/E`}
                                            sx={{border, ...separator, backgroundColor: ratioColor, color: "#111"}}
                                        >
                                            {pe === null ? "-" : formatDecimals(pe, 2, 2) || "-"}
                                        </TableCell>
                                        <TableCell
                                            align="right"
                                            aria-label={`${row.label} ttm price`}
                                            sx={{border, ...separator, backgroundColor: priceColor, color: "#111"}}
                                        >
                                            {formatDecimals(row.price, 0, 2) || "-"}
                                        </TableCell>
                                        {earningsColumns.slice(1).map(column => (
                                            <TableCell
                                                key={column.key}
                                                align="right"
                                                aria-label={`${row.label} ${column.label} price`}
                                                sx={{border, ...separator, backgroundColor: ratioColor, color: "#111"}}
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
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose}>Close</Button>
            </DialogActions>
        </Dialog>
    );
};
