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
import {formatError, formatPeriodName, orBlank} from "../service/FormattingService";
import {validateDate} from "../service/ValidationService";
import {DialogDatePicker} from "./component/DialogDatePicker";
import {
    EMPTY_PERIOD_FINANCIAL_VALUES,
    PeriodFinancialFields,
} from "./component/PeriodFinancialFields";

const EMPTY_FINANCIAL = {
    ...EMPTY_PERIOD_FINANCIAL_VALUES,
    reportDate: "",
}

export const AddPeriodFinancialDialog = props => {
    const {handleClose, open, period, company, triggerRefresh} = props

    const [financial, setFinancial] = useState(EMPTY_FINANCIAL)
    const [suggestions, setSuggestions] = useState({firebase: {}, polygon: {}})
    const [warnings, setWarnings] = useState([])
    const [alert, setAlert] = useState(null)
    const [loading, setLoading] = useState(false)

    useEffect(() => {
        if (!open || !period) return

        const cachedData = period.cachedData ?? {}
        setFinancial({
            ...EMPTY_FINANCIAL,
            reportDate: orBlank(cachedData.reportDate ?? period.reportDate),
        })
        setSuggestions({firebase: cachedData, polygon: {}})
        setWarnings([])
        setAlert(null)
        setLoading(true)

        const quarterId = formatPeriodName(period.name)
        axios.get(`${backend}/research/${company.id}/import/period/${quarterId}`)
            .then(response => {
                const data = response.data
                setFinancial(previous => ({
                    ...previous,
                    reportDate: orBlank(data.reportDate ?? previous.reportDate),
                }))
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
    }, [open])

    function createFinancial() {
        axios.put(backend + "/period/financial", {
            id: period.id,
            ...financial,
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
            <DialogTitle>Add Financial for {company.ticker} {period ? formatPeriodName(period.name) : ""}</DialogTitle>
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
                                <AlertTitle>Some Polygon.io data could not be loaded</AlertTitle>
                                {warnings.map(warning => <Box key={warning}>{warning}</Box>)}
                            </Alert>
                        }

                        <PeriodFinancialFields
                            values={financial}
                            setValues={setFinancial}
                            suggestions={suggestions}
                            setSuggestions={setSuggestions}
                            clearAlert={() => setAlert(null)}
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
                <Button type="submit" disabled={loading}>Create</Button>
            </DialogActions>
        </Dialog>
    )
}
