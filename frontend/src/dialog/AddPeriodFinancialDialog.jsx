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
} from "@mui/material";
import React, {useEffect, useState} from "react";
import axios from "axios";
import {backend} from "../properties";
import {formatError, formatPeriodName} from "../service/FormattingService";
import {validateDate} from "../service/ValidationService";
import {DialogDatePicker} from "./component/DialogDatePicker";
import {
    EMPTY_PERIOD_FINANCIAL_VALUES,
    PeriodFinancialFields,
    toNullableFinancialValues,
} from "./component/PeriodFinancialFields";

const EMPTY_FINANCIAL = {
    ...EMPTY_PERIOD_FINANCIAL_VALUES,
    reportDate: "",
}

export const AddPeriodFinancialDialog = props => {
    const {handleClose, open, period, company, triggerRefresh, edit = false} = props

    const [financial, setFinancial] = useState(EMPTY_FINANCIAL)
    const [suggestions, setSuggestions] = useState({firebase: {}, polygon: {}})
    const [warnings, setWarnings] = useState([])
    const [alert, setAlert] = useState(null)
    const [loading, setLoading] = useState(false)

    useEffect(() => {
        if (!open || !period) return

        if (edit) {
            setFinancial(financialFromPeriod(period))
            setSuggestions({firebase: {}, polygon: {}})
            setWarnings([])
            setAlert(null)
            setLoading(false)
            return
        }

        setFinancial(EMPTY_FINANCIAL)
        setSuggestions({firebase: {}, polygon: {}})
        setWarnings([])
        setAlert(null)
        setLoading(true)

        const quarterId = formatPeriodName(period.name)
        axios.get(`${backend}/research/${company.id}/import/period/${quarterId}`)
            .then(response => {
                const data = response.data
                setSuggestions({
                    firebase: data.firebase ?? {},
                    polygon: data.polygon ?? {},
                })
                setWarnings(data.warnings ?? [])
                setLoading(false)
            })
            .catch(error => {
                setAlert(formatError(error))
                setLoading(false)
            })
        // eslint-disable-next-line
    }, [open, period, edit])

    function createFinancial() {
        axios.put(backend + "/period/financial", {
            id: period.id,
            ...toNullableFinancialValues(financial),
        })
            .then(() => {
                triggerRefresh()
                handleClose()
            })
            .catch(error => setAlert(formatError(error)))
    }

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            slotProps={{paper: {component: 'form', onSubmit: event => {event.preventDefault();createFinancial()},}}}
        >
            <DialogTitle>{edit ? "Edit Period" : "Add Financial"} for {company.ticker} {period ? formatPeriodName(period.name) : ""}</DialogTitle>
            <DialogContent sx={{display: "flex", flexDirection: "column", gap: 1}}>
                {loading &&
                    <Box sx={{display: "flex", justifyContent: "center", alignItems: "center", minHeight: 180}}>
                        <CircularProgress />
                    </Box>
                }

                {!loading &&
                    <>
                        <DialogDatePicker
                            id="trader-period-report-date"
                            value={financial.reportDate}
                            label="Report Date"
                            onChange={event => {
                                setFinancial(previous => ({...previous, reportDate: event.target.value}))
                                setAlert(null)
                            }}
                            validate={() => validateDate(financial.reportDate, false, true)}
                        />

                        {warnings.length > 0 &&
                            <Alert severity="warning">
                                <AlertTitle>Some external data could not be loaded</AlertTitle>
                                {warnings.map(warning => <Box key={warning}>{warning}</Box>)}
                            </Alert>
                        }

                        <PeriodFinancialFields
                            values={financial}
                            setValues={setFinancial}
                            suggestions={suggestions}
                            setSuggestions={setSuggestions}
                            clearAlert={() => setAlert(null)}
                            showSuggestions={!edit}
                        />
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
                <Button type="submit" disabled={loading}>{edit ? "Update" : "Create"}</Button>
            </DialogActions>
        </Dialog>
    )
}

function financialFromPeriod(period) {
    const financial = period.financial ?? {}
    const value = (input) => input === null || input === undefined ? "" : String(input)

    return {
        reportDate: value(period.reportDate),
        shares: value(period.shares),
        revenue: value(financial.revenue?.value),
        grossProfit: value(financial.grossProfit?.value),
        operatingIncome: value(financial.operatingIncome?.value),
        netIncome: value(financial.netIncome?.value),
        dividend: value(financial.dividend),
        adjustedEps: value(financial.adjustedEps),
        priceHigh: value(period.priceHigh),
        priceLow: value(period.priceLow),
        capex: value(financial.capex?.value),
        freeCashFlow: value(financial.freeCashFlow?.value),
    }
}
