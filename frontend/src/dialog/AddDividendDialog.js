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
    MenuItem,
    Select
} from "@mui/material";
import {validateNumber} from "../service/ValidationService";
import {formatError} from "../service/FormattingService";
import {DialogTextField} from "./component/DialogTextField";
import {DialogDatePicker} from "./component/DialogDatePicker";


const AddDividendDialog = props => {
    const open = props.openAddDividend
    const handleClose = () => props.setOpenAddDividend(false)

    const [alert, setAlert] = useState(null)
    const [date, setDate] = useState("")
    const [dividend, setDividend] = useState("")
    const [tax, setTax] = useState("")
    const [company, setCompany] = useState("")

    useEffect(() => {
        if (open) {
            setAlert(null)
            setDate("")
            setDividend("")
            setTax("")
            setCompany(props.companySelectorValue)
        }
        // eslint-disable-next-line
    }, [open])

    function createDividend() {
        const dividendData = {companyId: company.id, date: date, dividend: dividend, tax: tax}
        axios.post(backend + "/dividend", dividendData)
            .then((response) => {
                props.triggerRefresh()
                handleClose()
            }).catch((error) => {setAlert(formatError(error))})
    }

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            PaperProps={{component: 'form', onSubmit: (event) => {event.preventDefault();createDividend()},}}
        >
            <DialogTitle>Add Dividend</DialogTitle>
            <DialogContent>
                <DialogDatePicker
                    id="trader-dividend-date"
                    value={date}
                    onChange={(e) => {setDate(e.target.value);setAlert(null);}}
                />
                <Select required margin="dense" fullWidth variant="standard" displayEmpty
                        value={company}
                        error={company === ""}
                        onChange={event => {setCompany(event.target.value);setAlert(null);}}
                        sx={{marginTop: "20px"}}
                >
                    <MenuItem value=""></MenuItem>
                    {props.companies.map((company, index) => (
                        <MenuItem key={index} value={company} >{(company.ticker === undefined) ? company : company.ticker}</MenuItem>
                    ))}
                </Select>
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
                <Button type="submit">Create</Button>
            </DialogActions>
        </Dialog>
    )
}
export default AddDividendDialog