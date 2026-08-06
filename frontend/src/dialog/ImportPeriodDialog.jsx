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
import {DialogTextField} from "./component/DialogTextField";
import {DialogDatePicker} from "./component/DialogDatePicker";
import {formatError} from "../service/FormattingService";
import {backend} from "../properties";
import axios from "axios";
import {
    EMPTY_PERIOD_FINANCIAL_VALUES,
    PeriodFinancialFields,
} from "./component/PeriodFinancialFields";

export const ImportPeriodDialog = props => {
    const {company, periods = [], open, handleClose, triggerRefresh} = props

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
            ...EMPTY_PERIOD_FINANCIAL_VALUES,
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
                setPeriod(editablePeriod({
                    ...response.data,
                    name: response.data?.name ?? candidate.name,
                }))
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

                {!period && !loading && periods.length === 0 && !alert &&
                    <Box sx={{color: "text.secondary"}}>No periods available for import.</Box>
                }

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
                                        <AlertTitle>Some external data could not be loaded</AlertTitle>
                                        {warnings.map(warning => <Box key={warning}>{warning}</Box>)}
                                    </Alert>
                                }

                                <PeriodFinancialFields
                                    values={period}
                                    setValues={setPeriod}
                                    suggestions={suggestions}
                                    setSuggestions={setSuggestions}
                                    clearAlert={() => setAlert(null)}
                                />
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
