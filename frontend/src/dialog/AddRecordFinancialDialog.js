import {Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField, Typography} from "@mui/material";
import {handleError, validateNumber, validateQuarter} from "../utils";
import React, {useEffect, useState} from "react";
import axios from "axios";
import {backend} from "../properties";
import {getFinancial} from "../service/PolygonIoService";
import FindReplaceIcon from '@mui/icons-material/FindReplace';

const AddRecordFinancialDialog = props => {
    const {handleClose, open} = props

    const [alert, setAlert] = useState(null)
    const [importInfo, setImportInfo] = useState("")
    const [quarter, setQuarter] = useState("")
    const [revenue, setRevenue] = useState("")
    const [cogs, setCogs] = useState("")
    const [opExp, setOpExp] = useState("")
    const [netIncome, setNetIncome] = useState("")

    useEffect(() => {
        if (open) {
            setAlert(null)
            setImportInfo("")
            setQuarter("")
            setRevenue("")
            setCogs("")
            setOpExp("")
            setNetIncome("")
        }
        // eslint-disable-next-line
    }, [open])

    function createFinancial() {
        const financialData = {companyId: props.companySelectorValue.id, quarter: quarter, revenue: revenue,
            costGoodsSold: cogs, operatingExpenses: opExp, netIncome: netIncome}
        axios.post(backend + "/financial", financialData)
            .then((response) => {
                props.triggerRefresh()
                handleClose()
            }).catch((error) => {setAlert(handleError(error))})
    }

    async function retrieveFinancial() {
        const year = "20" + quarter.substring(0,2)
        const period = quarter.substring(2,4)

        const financial = await getFinancial(props.companySelectorValue.ticker, year, period);

        if (financial) {
            setImportInfo(financial.start_date + " => " + financial.end_date)
            const revenues = financial.financials.income_statement.revenues.value.toString()
            setRevenue(revenues.substring(0, revenues.length - 6))
            const cogs = financial.financials.income_statement.cost_of_revenue.value.toString()
            setCogs(cogs.substring(0, cogs.length - 6))
            const opExp = financial.financials.income_statement.operating_expenses.value.toString()
            setOpExp(opExp.substring(0, opExp.length - 6))
            const ni = financial.financials.income_statement.net_income_loss.value.toString()
            setNetIncome(ni.substring(0, ni.length - 6))
        } else {
            setImportInfo("not found")
            setRevenue("")
            setCogs("")
            setOpExp("")
            setNetIncome("")
        }
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
                           onChange={(e) => {setQuarter(e.target.value);setAlert(null);setImportInfo("");}}
                           error={validateQuarter(quarter) !== ""}
                           helperText={validateQuarter(quarter)}
                />
                {validateQuarter(quarter) === "" && <>
                    {importInfo ? <Typography>{importInfo}</Typography>
                        : <Button onClick={retrieveFinancial} startIcon={<FindReplaceIcon/>}>Retrieve</Button>}
                </>}
                <TextField required margin="dense" fullWidth variant="standard" id="company-financial-revenue"
                           value={revenue}
                           label="Revenue (in Millions)"
                           onChange={(e) => {setRevenue(e.target.value);setAlert(null);}}
                           error={validateNumber(revenue, false, 8, 2) !== ""}
                           helperText={validateNumber(revenue, false, 8, 2)}
                />
                <TextField required margin="dense" fullWidth variant="standard" id="company-financial-cogs"
                           value={cogs}
                           label="Cost of Goods Sold (in Millions)"
                           onChange={(e) => {setCogs(e.target.value);setAlert(null);}}
                           error={validateNumber(cogs, false, 8, 2) !== ""}
                           helperText={validateNumber(cogs, false, 8, 2)}
                />
                <TextField required margin="dense" fullWidth variant="standard" id="company-financial-op-exp"
                           value={opExp}
                           label="Operating Expenses (in Millions)"
                           onChange={(e) => {setOpExp(e.target.value);setAlert(null);}}
                           error={validateNumber(opExp, false, 8, 2) !== ""}
                           helperText={validateNumber(opExp, false, 8, 2)}
                />
                <TextField required margin="dense" fullWidth variant="standard" id="company-financial-netIncome"
                           value={netIncome}
                           label="Net Income (in Millions)"
                           onChange={(e) => {setNetIncome(e.target.value);setAlert(null);}}
                           error={validateNumber(netIncome, false, 8, 2) !== ""}
                           helperText={validateNumber(netIncome, false, 8, 2)}
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
export default AddRecordFinancialDialog