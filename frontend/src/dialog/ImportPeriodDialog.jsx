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
    Stack,
    Tooltip,
    Typography
} from "@mui/material";
import React, {useEffect, useState} from "react";
import {DialogTextField} from "./component/DialogTextField";
import {DialogDatePicker} from "./component/DialogDatePicker";
import {validateNumber} from "../service/ValidationService";
import {formatError} from "../service/FormattingService";
import {backend} from "../properties";
import axios from "axios";

const EMPTY_VALUES = {
    shares: "",
    revenue: "",
    grossProfit: "",
    operatingIncome: "",
    netIncome: "",
    dividend: "",
    priceHigh: "",
    priceLow: "",
}

const SOURCE_STYLE = {
    firebase: {label: "Firebase", color: "success"},
    polygon: {label: "Polygon.io", color: "info"},
}

const SourceSuggestion = ({source, fieldLabel, value, apply}) => {
    const hasValue = value !== null && value !== undefined && value !== ""

    return (
        <Box sx={{minWidth: 120}}>
            {hasValue &&
                <Tooltip title={`Use ${SOURCE_STYLE[source].label} value for ${fieldLabel}`}>
                    <Button
                        fullWidth
                        color={SOURCE_STYLE[source].color}
                        onClick={apply}
                    >
                        {`<< ${value}`}
                    </Button>
                </Tooltip>
            }
        </Box>
    )
}

const FinancialField = ({field, period, setPeriod, suggestions, setSuggestions, clearAlert}) => {
    function applySuggestion(source) {
        const value = suggestions[source]?.[field.key]
        setPeriod(previous => ({...previous, [field.key]: value}))
        setSuggestions(previous => ({
            ...previous,
            [source]: {...previous[source], [field.key]: undefined},
        }))
    }

    return (
        <Stack direction="row" spacing={2} alignItems="center">
            <DialogTextField
                id={field.id}
                value={period[field.key]}
                label={field.label}
                sx={{minWidth: 230}}
                onChange={(event) => {
                    setPeriod(previous => ({
                        ...previous,
                        [field.key]: event.target.value,
                    }))
                    clearAlert()
                }}
                validate={() => validateNumber(
                    period[field.key],
                    false,
                    field.integerConstraint,
                    field.decimalConstraint,
                    field.allowNegative,
                )}
            />
            <SourceSuggestion
                source="firebase"
                fieldLabel={field.label}
                value={suggestions.firebase?.[field.key]}
                apply={() => applySuggestion("firebase")}
            />
            <SourceSuggestion
                source="polygon"
                fieldLabel={field.label}
                value={suggestions.polygon?.[field.key]}
                apply={() => applySuggestion("polygon")}
            />
        </Stack>
    )
}

const FINANCIAL_FIELDS = [
    {
        key: "shares",
        id: "company-financial-shares",
        label: "Shares (in Millions)",
        integerConstraint: 8,
        decimalConstraint: 2,
        allowNegative: false,
    },
    {
        key: "revenue",
        id: "company-financial-revenue",
        label: "Revenue (in Millions)",
        integerConstraint: 8,
        decimalConstraint: 2,
        allowNegative: false,
    },
    {
        key: "grossProfit",
        id: "company-financial-gross-profit",
        label: "Gross Profit (in Millions)",
        integerConstraint: 8,
        decimalConstraint: 2,
        allowNegative: true,
    },
    {
        key: "operatingIncome",
        id: "company-financial-operating-income",
        label: "Operating Income (in Millions)",
        integerConstraint: 8,
        decimalConstraint: 2,
        allowNegative: true,
    },
    {
        key: "netIncome",
        id: "company-financial-net-income",
        label: "Net Income (in Millions)",
        integerConstraint: 8,
        decimalConstraint: 2,
        allowNegative: true,
    },
    {
        key: "dividend",
        id: "company-financial-dividend",
        label: "Dividend (in Millions)",
        integerConstraint: 8,
        decimalConstraint: 2,
        allowNegative: false,
    },
    {
        key: "priceHigh",
        id: "trader-period-price-high",
        label: "Highest Price",
        integerConstraint: 10,
        decimalConstraint: 4,
        allowNegative: false,
    },
    {
        key: "priceLow",
        id: "trader-period-price-low",
        label: "Lowest Price",
        integerConstraint: 10,
        decimalConstraint: 4,
        allowNegative: false,
    },
]

export const ImportPeriodDialog = props => {
    const {company, periods, open, handleClose, triggerRefresh} = props

    const [period, setPeriod] = useState(null)
    const [suggestions, setSuggestions] = useState({firebase: {}, polygon: {}})
    const [warnings, setWarnings] = useState([])
    const [alert, setAlert] = useState(null)
    const [loading, setLoading] = useState(false)

    useEffect(() => {
        if (open) resetSelection()
        // eslint-disable-next-line
    }, [open])

    function resetSelection() {
        setPeriod(null)
        setSuggestions({firebase: {}, polygon: {}})
        setWarnings([])
        setAlert(null)
        setLoading(false)
    }

    function editablePeriod(data) {
        return {
            ...EMPTY_VALUES,
            name: data.name,
            endingMonth: data.endingMonth,
            reportDate: data.reportDate ?? "",
            isReported: data.isReported,
        }
    }

    function createPeriod() {
        const endpoint = period.isReported ? "/period/import" : "/period/import/unreported"
        const data = period.isReported
            ? {...period, companyId: company.id}
            : {companyId: company.id, name: period.name, endingMonth: period.endingMonth}

        axios.post(backend + endpoint, data)
            .then(() => {
                triggerRefresh()
                handleClose()
            })
            .catch(error => setAlert(formatError(error)))
    }

    function selectPeriod(candidate) {
        setAlert(null)
        setWarnings([])

        if (!candidate.isReported) {
            setPeriod(editablePeriod(candidate))
            setSuggestions({firebase: {}, polygon: {}})
            return
        }

        setLoading(true)
        axios.get(`${backend}/research/${company.id}/import/period/${candidate.name}`)
            .then(response => {
                setPeriod(editablePeriod(response.data))
                setSuggestions({
                    firebase: response.data.firebase ?? {},
                    polygon: response.data.polygon ?? {},
                })
                setWarnings(response.data.warnings ?? [])
                setLoading(false)
            })
            .catch(error => {
                setAlert(formatError(error))
                setLoading(false)
            })
    }

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            slotProps={{paper: {component: 'form', onSubmit: event => {event.preventDefault();createPeriod()},}}}
        >
            <DialogTitle>Import Period {period && period.name}</DialogTitle>
            <DialogContent sx={{display: "flex", flexDirection: "column", gap: period?.isReported ? 1 : 2}}>
                {loading &&
                    <Box sx={{display: "flex", justifyContent: "center", alignItems: "center", minHeight: 180}}>
                        <CircularProgress />
                    </Box>
                }

                {!period && !loading && periods.map(candidate => (
                    <Button key={candidate.name} onClick={() => selectPeriod(candidate)}>
                        {`${candidate.name}${candidate.isReported ? "" : "*"}`}
                    </Button>
                ))}

                {period && !loading &&
                    <>
                        <Box sx={period.isReported
                            ? {display: "flex", gap: 2, alignItems: "flex-start"}
                            : {display: "contents"}
                        }>
                            <DialogTextField
                                id="trader-period-name"
                                value={period.name}
                                label="Name"
                                onChange={event => {setPeriod(previous => ({...previous, name: event.target.value}));setAlert(null)}}
                                validate={() => period.name.length !== 4
                                    ? "exactly 4 symbols, e.g. 25FY, 25Q1, ..."
                                    : ""}
                            />
                            <DialogDatePicker
                                id="trader-period-end-month"
                                type="month"
                                value={period.endingMonth}
                                label="Ending Month"
                                onChange={event => {setPeriod(previous => ({...previous, endingMonth: event.target.value}));setAlert(null)}}
                            />
                            {period.isReported &&
                                <DialogDatePicker
                                    id="trader-period-report-date"
                                    value={period.reportDate}
                                    label="Report Date"
                                    onChange={event => {setPeriod(previous => ({...previous, reportDate: event.target.value}));setAlert(null)}}
                                />
                            }
                        </Box>

                        {period.isReported &&
                            <>
                                {warnings.length > 0 &&
                                    <Alert severity="warning">
                                        <AlertTitle>Some Polygon.io data could not be loaded</AlertTitle>
                                        {warnings.map(warning => <Box key={warning}>{warning}</Box>)}
                                    </Alert>
                                }

                                <Stack direction="row" alignItems="center">
                                    <Box sx={{flex: 1}} />
                                    {Object.entries(SOURCE_STYLE).map(([source, style]) => (
                                        <Tooltip key={source} title={`${style.label} suggestions`}>
                                            <Typography
                                                sx={{minWidth: 120, textAlign: "center", fontWeight: 600}}
                                                color={`${style.color}.main`}
                                            >
                                                {style.label}
                                            </Typography>
                                        </Tooltip>
                                    ))}
                                </Stack>

                                {FINANCIAL_FIELDS.map(field => (
                                    <FinancialField
                                        key={field.key}
                                        field={field}
                                        period={period}
                                        setPeriod={setPeriod}
                                        suggestions={suggestions}
                                        setSuggestions={setSuggestions}
                                        clearAlert={() => setAlert(null)}
                                    />
                                ))}
                            </>
                        }
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
                {period &&
                    <>
                        <Button onClick={resetSelection}>Back</Button>
                        <Button type="submit">Create</Button>
                    </>
                }
            </DialogActions>
        </Dialog>
    )
}
