import {Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField} from "@mui/material";
import {handleError, validateNumber} from "../utils";
import React, {useEffect, useState} from "react";
import axios from "axios";
import {domain} from "../properties";


function validateQuarter(value) {
    if (typeof value != "string") return "not a string"
    if (!value) return "non empty"
    if (value.length !== 4) return "invalid format, not YYQQ (e.g. 24Q1)"
    if (isNaN(value.substring(0,2))) return "invalid format, not YYQQ (e.g. 24Q1)"
    if (value.substring(2,3) !== "Q") return "invalid format, not YYQQ (e.g. 24Q1)"
    if (isNaN(value.substring(3,4))) return "invalid format, not YYQQ (e.g. 24Q1)"
    return ""
}

const AddFinancialDialog = props => {
    const {handleClose, open} = props

    const [alert, setAlert] = useState(null)
    const [revenue, setRevenue] = useState("")
    const [netIncome, setNetIncome] = useState("")
    const [eps, setEps] = useState("")
    const [quarter, setQuarter] = useState("")

    useEffect(() => {
        if (open) {
            setAlert(null)
            setRevenue("")
            setNetIncome("")
            setEps("")
            setQuarter("")
        }
        // eslint-disable-next-line
    }, [open])

    function createFinancial() {
        const financialData = {companyId: props.companySelectorValue.id, revenue: revenue, netIncome: netIncome, eps: eps, quarter: quarter}
        axios.post(domain + "/financial", financialData)
            .then((response) => {
                props.triggerRefresh()
                handleClose()
            }).catch((error) => {setAlert(handleError(error))})
    }

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            PaperProps={{component: 'form', onSubmit: (event) => {event.preventDefault();createFinancial()},}}
        >
            <DialogTitle>Add Financial for {props.companySelectorValue.ticker}</DialogTitle>
            <DialogContent>
                <TextField required margin="dense" fullWidth variant="standard" id="company-financial-quarter"
                           value={quarter}
                           label="Quarter"
                           onChange={(e) => {setQuarter(e.target.value);setAlert(null);}}
                           error={validateQuarter(quarter) !== ""}
                           helperText={validateQuarter(quarter)}
                />
                <TextField required margin="dense" fullWidth variant="standard" id="company-financial-revenue"
                           value={revenue}
                           label="Revenue (in Millions)"
                           onChange={(e) => {setRevenue(e.target.value);setAlert(null);}}
                           error={validateNumber(revenue, false, 8, 2) !== ""}
                           helperText={validateNumber(revenue, false, 8, 2)}
                />
                <TextField required margin="dense" fullWidth variant="standard" id="company-financial-netIncome"
                           value={netIncome}
                           label="Net Income (in Millions)"
                           onChange={(e) => {setNetIncome(e.target.value);setAlert(null);}}
                           error={validateNumber(netIncome, false, 8, 2) !== ""}
                           helperText={validateNumber(netIncome, false, 8, 2)}
                />
                <TextField required margin="dense" fullWidth variant="standard" id="company-financial-eps"
                           value={eps}
                           label="EPS"
                           onChange={(e) => {setEps(e.target.value);setAlert(null);}}
                           error={validateNumber(eps, false, 4, 2) !== ""}
                           helperText={validateNumber(eps, false, 4, 2)}
                />
            </DialogContent>
            {alert &&
                <Alert severity="error" variant="filled">
                    {alert}
                </Alert>
            }
            <DialogActions>
                <Button onClick={handleClose}>Cancel</Button>
                <Button type="submit">Create</Button>
            </DialogActions>
        </Dialog>
    )
}
export default AddFinancialDialog