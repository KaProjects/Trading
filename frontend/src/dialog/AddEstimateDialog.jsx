import {
    Alert,
    AlertTitle,
    Box,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from "@mui/material";
import axios from "axios";
import React, {useEffect, useState} from "react";
import {backend} from "../properties";
import {formatDate, formatDecimals, formatError, formatPeriodName} from "../service/FormattingService";
import {validateDate, validateNumber} from "../service/ValidationService";
import {DialogDatePicker} from "./component/DialogDatePicker";
import {DialogTextField} from "./component/DialogTextField";

const EMPTY_ESTIMATE = {
    date: "",
    current: "",
    next1: "",
    next2: "",
    next3: "",
};

const ESTIMATE_FIELDS = [
    {key: "current", label: "Current", nullable: false},
    {key: "next1", label: "Next 1", nullable: true},
    {key: "next2", label: "Next 2", nullable: true},
    {key: "next3", label: "Next 3", nullable: true},
];

const GRID_STYLE = {
    display: "grid",
    gridTemplateColumns: "150px repeat(4, minmax(110px, 1fr))",
    columnGap: 2,
    rowGap: 0,
    alignItems: "start",
    minWidth: 650,
};

const INPUT_STYLE = {
    "& .MuiFormHelperText-root": {
        minHeight: "20px",
        marginTop: "2px",
    },
};

const formatValue = value => formatDecimals(value, 0, 2) || "-";
const importedValue = (data, key) => data?.[key]?.eps ?? "";
const roundedImportedValue = (data, key) => {
    const value = importedValue(data, key);
    return value === "" || Number.isNaN(Number(value)) ? "" : Number(value).toFixed(2);
};
const importedEstimate = (data, key) => {
    const value = roundedImportedValue(data, key);
    return value ? {value, date: formatDate(data?.[key]?.date) || "-"} : null;
};

const parseEstimates = value => {
    const input = value.trim();
    if (!input) return null;

    const hasNonCommaSeparator = /[\s|\/\\]/.test(input);
    const tokens = (hasNonCommaSeparator
        ? input.split(/[\s|\/\\]+/)
        : input.split(","))
        .map(token => token.replace(/^,+|,+$/g, ""))
        .filter(Boolean);
    if (tokens.length < 2 || tokens.length > 4) return null;

    const parsed = tokens.map(token => {
        if (!/^[+-]?\d+(?:[.,]\d+)?$/.test(token)) return null;
        const number = Number(token.replace(",", "."));
        return Number.isFinite(number) ? number.toFixed(2) : null;
    });
    return parsed.includes(null) ? null : parsed;
};

export const AddEstimateDialog = ({open, handleClose, triggerRefresh, company, period}) => {
    const [history, setHistory] = useState([]);
    const [imported, setImported] = useState({});
    const [estimate, setEstimate] = useState(EMPTY_ESTIMATE);
    const [loading, setLoading] = useState(false);
    const [alert, setAlert] = useState(null);
    const [estimatesToParse, setEstimatesToParse] = useState("");

    useEffect(() => {
        if (!open || !period || !company) return;

        setHistory([]);
        setImported({});
        setEstimate(EMPTY_ESTIMATE);
        setAlert(null);
        setEstimatesToParse("");
        setLoading(true);

        Promise.all([
            axios.get(`${backend}/estimate/${period.id}`),
            axios.get(`${backend}/research/${company.id}/import/estimate/${period.id}`),
        ])
            .then(([historyResponse, importResponse]) => {
                setHistory(historyResponse.data ?? []);
                setImported(importResponse.data ?? {});
                setLoading(false);
            })
            .catch(error => {
                setAlert(formatError(error));
                setLoading(false);
            });
    }, [open, period, company]);

    function update(key, value) {
        setEstimate(previous => ({...previous, [key]: value}));
        setAlert(null);
    }

    function setImportedValues() {
        setEstimate(previous => ({
            ...previous,
            current: roundedImportedValue(imported, "current"),
            next1: roundedImportedValue(imported, "next1"),
            next2: roundedImportedValue(imported, "next2"),
            next3: roundedImportedValue(imported, "next3"),
        }));
        setAlert(null);
    }

    function fieldError(field) {
        return validateNumber(estimate[field.key], field.nullable, 6, 2, true);
    }

    function useParsedValues() {
        const values = parseEstimates(estimatesToParse);
        if (!values) return;

        setEstimate(previous => ({
            ...previous,
            current: values[0],
            next1: values[1],
            next2: values[2] ?? "",
            next3: values[3] ?? "",
        }));
        setAlert(null);
    }

    function createEstimate() {
        const invalid = validateDate(estimate.date, true, true)
            || ESTIMATE_FIELDS.map(fieldError).find(error => error !== "");
        if (invalid) {
            setAlert({title: "Invalid estimate", message: "Please correct the highlighted fields."});
            return;
        }

        const nullable = value => value === "" ? null : value;
        axios.post(`${backend}/estimate/${period.id}`, {
            date: estimate.date,
            current: estimate.current,
            next1: nullable(estimate.next1),
            next2: nullable(estimate.next2),
            next3: nullable(estimate.next3),
        })
            .then(() => {
                triggerRefresh();
                handleClose();
            })
            .catch(error => setAlert(formatError(error)));
    }

    const hasImportedValues = roundedImportedValue(imported, "current") !== ""
        && roundedImportedValue(imported, "next1") !== "";
    const parsedValues = parseEstimates(estimatesToParse);

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            fullWidth
            maxWidth="md"
            slotProps={{paper: {component: "form", onSubmit: event => {event.preventDefault();createEstimate();}}}}
        >
            <DialogTitle>
                Add Estimate for {company?.ticker} {period ? formatPeriodName(period.name) : ""}
            </DialogTitle>
            <DialogContent sx={{display: "flex", flexDirection: "column", gap: 2}}>
                {loading &&
                    <Box sx={{display: "flex", justifyContent: "center", alignItems: "center", minHeight: 180}}>
                        <CircularProgress/>
                    </Box>
                }

                {!loading &&
                    <>
                        <Box sx={{overflowX: "auto"}}>
                            <Box sx={GRID_STYLE}>
                                <DialogDatePicker
                                    id="estimate-date"
                                    value={estimate.date}
                                    label="Snapshot date"
                                    onChange={event => update("date", event.target.value)}
                                    validate={() => validateDate(estimate.date, true, true)}
                                    sx={INPUT_STYLE}
                                />
                                {ESTIMATE_FIELDS.map(field => (
                                    <DialogTextField
                                        key={field.key}
                                        id={`estimate-${field.key}`}
                                        value={estimate[field.key]}
                                        label={field.label}
                                        required={!field.nullable}
                                        onChange={event => update(field.key, event.target.value)}
                                        validate={() => fieldError(field)}
                                        slotProps={{inputLabel: {shrink: true}}}
                                        sx={INPUT_STYLE}
                                    />
                                ))}
                            </Box>
                        </Box>

                        <Box sx={{
                            alignSelf: "flex-start",
                            display: "flex",
                            alignItems: "center",
                            gap: 1,
                        }}>
                            <Button
                                size="small"
                                variant="outlined"
                                color="info"
                                onClick={setImportedValues}
                                aria-label="Use external estimates"
                                disabled={!hasImportedValues}
                                sx={{minHeight: 24, paddingY: 0, textTransform: "none", whiteSpace: "nowrap"}}
                            >
                                ^ USE ^
                            </Button>
                            <Typography
                                data-testid="external-estimates"
                                sx={{fontSize: 16, color: "text.secondary"}}
                            >
                                External estimates: [ {ESTIMATE_FIELDS.map((field, index) => {
                                    const estimate = importedEstimate(imported, field.key);
                                    return (
                                        <React.Fragment key={field.key}>
                                            {index > 0 && " | "}
                                            {estimate
                                                ? <>
                                                    {estimate.value}{" "}
                                                    <Box component="span" sx={{fontSize: "0.67em"}}>
                                                        ({estimate.date})
                                                    </Box>
                                                </>
                                                : "-"}
                                        </React.Fragment>
                                    );
                                })} ]
                            </Typography>
                        </Box>

                        <Box sx={{
                            display: "grid",
                            gridTemplateColumns: "max-content 250px",
                            columnGap: 1,
                            alignItems: "center",
                        }}>
                            <Button
                                size="small"
                                variant="outlined"
                                color="info"
                                onClick={useParsedValues}
                                aria-label="Use parsed estimates"
                                disabled={!parsedValues}
                                sx={{
                                    minHeight: 24,
                                    paddingY: 0,
                                    textTransform: "none",
                                    whiteSpace: "nowrap",
                                    transform: "translateY(10px)",
                                }}
                            >
                                ^ USE ^
                            </Button>
                            <DialogTextField
                                id="estimate-parser"
                                value={estimatesToParse}
                                label="Try parse estimates"
                                required={false}
                                onChange={event => {
                                    setEstimatesToParse(event.target.value);
                                    setAlert(null);
                                }}
                                validate={() => parsedValues
                                    ? ""
                                    : "Enter 2 to 4 numbers separated by whitespace, comma, |, /, or \\."}
                                showValidationError={false}
                                helperText=""
                                sx={{
                                    width: "250px",
                                    margin: 0,
                                    "& .MuiFormHelperText-root": {display: "none"},
                                }}
                            />
                            <Typography sx={{
                                gridColumn: "2",
                                color: "text.secondary",
                                fontSize: 11,
                                whiteSpace: "nowrap",
                                marginTop: "2px",
                            }}>
                                2–4 numbers separated by space, comma, |, /, or \.
                            </Typography>
                        </Box>

                        <TableContainer sx={{maxHeight: 220}}>
                            <Table size="small" stickyHeader aria-label="estimate history">
                                <TableHead>
                                    <TableRow>
                                        {["Date", "Current", "Next 1", "Next 2", "Next 3"].map(header => (
                                            <TableCell key={header} align="center">{header}</TableCell>
                                        ))}
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {history.map(item => (
                                        <TableRow key={item.id}>
                                            <TableCell align="center">{formatDate(item.datetime?.substring(0, 10)) || "-"}</TableCell>
                                            <TableCell align="center">{formatValue(item.current)}</TableCell>
                                            <TableCell align="center">{formatValue(item.next1)}</TableCell>
                                            <TableCell align="center">{formatValue(item.next2)}</TableCell>
                                            <TableCell align="center">{formatValue(item.next3)}</TableCell>
                                        </TableRow>
                                    ))}
                                    {history.length === 0 &&
                                        <TableRow>
                                            <TableCell colSpan={5} align="center">No estimates yet</TableCell>
                                        </TableRow>
                                    }
                                </TableBody>
                            </Table>
                        </TableContainer>
                    </>
                }
            </DialogContent>

            {alert &&
                <Alert severity="error" variant="filled">
                    <AlertTitle>{alert.title}</AlertTitle>{alert.message}
                </Alert>
            }
            <DialogActions>
                <Button onClick={handleClose}>Cancel</Button>
                <Button type="submit" disabled={loading}>Add</Button>
            </DialogActions>
        </Dialog>
    );
};
