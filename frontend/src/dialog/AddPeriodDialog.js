import {Alert, AlertTitle, Button, Dialog, DialogActions, DialogContent, DialogTitle} from "@mui/material";
import React, {useEffect, useState} from "react";
import {backend} from "../properties";
import axios from "axios";
import {formatError} from "../service/FormattingService";
import {DialogTextField} from "./component/DialogTextField";


const AddPeriodDialog = props => {
    const {companyId, open, handleClose} = props
    const [alert, setAlert] = useState(null)
    const [name, setName] = useState("")
    const [endingMonth, setEndingMonth] = useState("")

    useEffect(() => {
        if (open) {
            setName("")
            setEndingMonth("")
        }
        // eslint-disable-next-line
    }, [open])

    function createPeriod() {
        const data = {companyId: companyId, name: name, endingMonth: endingMonth}
        axios.post(backend + "/period", data)
            .then((response) => {
                props.triggerRefresh()
                handleClose()
            }).catch((error) => {setAlert(formatError(error))})
    }

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            PaperProps={{component: 'form', onSubmit: (event) => {event.preventDefault();createPeriod()},}}
        >
            <DialogTitle>Add Period</DialogTitle>
            <DialogContent>
                <DialogTextField
                    id="trader-period-name"
                    value={name}
                    label="Name"
                    onChange={(e) => {setName(e.target.value);setAlert(null);}}
                    validate={() => name.length !== 4 ? "exactly 4 symbols, e.g. 25FY, 25Q1, ..." : ""}
                />
                <DialogTextField
                    id="trader-period-end-month"
                    type="month"
                    value={endingMonth}
                    label="Ending Month"
                    onChange={(e) => {setEndingMonth(e.target.value);setAlert(null);}}
                    validate={() => endingMonth === "" ? "not blank" : ""}
                />
            </DialogContent>
            {alert &&
                <Alert severity="error" variant="filled">
                    <AlertTitle>{alert.title}</AlertTitle>{alert.message}
                </Alert>
            }
            <DialogActions>
                <Button onClick={handleClose}>Cancel</Button>
                <Button type="submit">Create</Button>
            </DialogActions>
        </Dialog>
    )
}
export default AddPeriodDialog