import React, {useEffect, useState} from "react";
import axios from "axios";
import {backend} from "../properties";
import {
    Alert,
    AlertTitle,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
} from "@mui/material";
import {validateNumber} from "../service/ValidationService";
import {formatError} from "../service/FormattingService";
import {DialogTextField} from "./component/DialogTextField";
import {DialogDatePicker} from "./component/DialogDatePicker";
import {DialogCompanySelect} from "./component/DialogCompanySelect";


export const AddDividendDialog = props => {
    const open = props.openAddDividend
    const handleClose = () => props.setOpenAddDividend(false)

    const [alert, setAlert] = useState(null)
    const [date, setDate] = useState("")
    const [dividend, setDividend] = useState("")
    const [tax, setTax] = useState("")
    const [company, setCompany] = useState("")
    const [submitting, setSubmitting] = useState(false)

    useEffect(() => {
        if (open) {
            setAlert(null)
            setDate("")
            setDividend("")
            setTax("")
            setCompany(props.companySelectorValue)
            setSubmitting(false)
        }
        // eslint-disable-next-line
    }, [open])

    function createDividend() {
        if (submitting) return
        setSubmitting(true)
        const dividendData = {companyId: company.id, date: date, dividend: dividend, tax: tax}
        axios.post(backend + "/dividend", dividendData)
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
            slotProps={{paper: {component: 'form', onSubmit: (event) => {event.preventDefault();createDividend()},}}}
        >
            <DialogTitle>Add Dividend</DialogTitle>
            <DialogContent>
                <DialogDatePicker
                    id="trader-dividend-date"
                    value={date}
                    onChange={(e) => {setDate(e.target.value);setAlert(null);}}
                />
                <DialogCompanySelect
                    key={`add-dividend-company-${open}`}
                    id="trader-dividend-company"
                    companyLists={props.companyLists}
                    defaultCompanyList="owned"
                    value={company}
                    onChange={value => {setCompany(value);setAlert(null);}}
                />
                <DialogTextField
                    id="trader-dividend-dividend"
                    value={dividend}
                    label="Dividend"
                    onChange={(e) => {setDividend(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(dividend, false, 7, 2, false)}
                />
                <DialogTextField
                    id="trader-dividend-tax"
                    value={tax}
                    label="Tax"
                    onChange={(e) => {setTax(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(tax, false, 6, 2, false)}
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
