import {Alert, AlertTitle, Button, Dialog, DialogActions, DialogContent, DialogTitle} from "@mui/material";
import React, {useEffect, useState} from "react";
import {backend} from "../properties";
import axios from "axios";
import {formatError} from "../service/FormattingService";
import {DialogTextField} from "./component/DialogTextField";
import {DialogDatePicker} from "./component/DialogDatePicker";
import {endingMonthWarning, nextPeriod, periodNameError, periodNameWarning} from "../service/PeriodService";


export const AddPeriodDialog = props => {
    const {companyId, open, handleClose, periods = []} = props
    const latestPeriod = periods[0]
    const [alert, setAlert] = useState(null)
    const [name, setName] = useState("")
    const [endingMonth, setEndingMonth] = useState("")
    const [submitting, setSubmitting] = useState(false)

    useEffect(() => {
        if (open) {
            const following = nextPeriod(latestPeriod)
            setName(following?.name ?? "")
            setEndingMonth(following?.endingMonth ?? "")
            setSubmitting(false)
        }
        // eslint-disable-next-line
    }, [open, latestPeriod])

    function nameError() {
        return periodNameError(name, periods)
    }

    function createPeriod() {
        if (submitting) return
        setSubmitting(true)
        const data = {companyId: companyId, name: name, endingMonth: endingMonth}
        axios.post(backend + "/period", data)
            .then((response) => {
                props.triggerRefresh()
                handleClose()
            })
            .catch((error) => {setAlert(formatError(error))})
            .finally(() => setSubmitting(false))
    }

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            slotProps={{paper: {component: 'form', onSubmit: (event) => {event.preventDefault();createPeriod()},}}}
        >
            <DialogTitle>Add Period</DialogTitle>
            <DialogContent>
                <DialogTextField
                    id="trader-period-name"
                    value={name}
                    label="Name"
                    onChange={(e) => {setName(e.target.value);setAlert(null);}}
                    validate={nameError}
                    warning={periodNameWarning(name, latestPeriod)}
                />
                <DialogDatePicker
                    id="trader-period-end-month"
                    type="month"
                    value={endingMonth}
                    label="Ending Month"
                    onChange={(e) => {setEndingMonth(e.target.value);setAlert(null);}}
                    warning={endingMonthWarning(name, endingMonth, latestPeriod)}
                />
            </DialogContent>
            {alert &&
                <Alert severity="error" variant="filled">
                    <AlertTitle>{alert.title}</AlertTitle>{alert.message}
                </Alert>
            }
            <DialogActions>
                <Button onClick={handleClose}>Cancel</Button>
                <Button type="submit" disabled={submitting}>Create</Button>
            </DialogActions>
        </Dialog>
    )
}
