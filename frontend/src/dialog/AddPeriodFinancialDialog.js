import {Alert, AlertTitle, Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography} from "@mui/material";
import React, {useEffect, useState} from "react";
import axios from "axios";
import {backend} from "../properties";
import {getFinancial, getQuote} from "../service/PolygonIoService";
import {formatError, formatPeriodName, formatPolygonIoFinancial} from "../service/FormattingService";
import {validateNumber} from "../service/ValidationService";
import {DialogTextField} from "./component/DialogTextField";
import {DialogDatePicker} from "./component/DialogDatePicker";

const AddPeriodFinancialDialog = props => {
    const {handleClose, open, period, company, triggerRefresh} = props

    const [alert, setAlert] = useState(null)
    const [financialImportInfo, setFinancialImportInfo] = useState("")
    const [priceImportInfo, setPriceImportInfo] = useState("")

    const [reportDate, setReportDate] = useState("")
    const [shares, setShares] = useState("")
    const [revenue, setRevenue] = useState("")
    const [grossProfit, setGrossProfit] = useState("")
    const [operatingIncome, setOperatingIncome] = useState("")
    const [netIncome, setNetIncome] = useState("")
    const [dividend, setDividend] = useState("")
    const [priceHigh, setPriceHigh] = useState("")
    const [priceLow, setPriceLow] = useState("")

    useEffect(() => {
        if (open) {
            setAlert(null)
            setFinancialImportInfo("")
            setPriceImportInfo("")
            setReportDate(period.reportDate ? period.reportDate : "")
            setShares("")
            setRevenue("")
            setGrossProfit("")
            setOperatingIncome("")
            setNetIncome("")
            setDividend("")
            setPriceHigh("")
            setPriceLow("")
            retrieveFinancials()
        }
        // eslint-disable-next-line
    }, [open])

    useEffect(() => {
        if (reportDate) {
            const toDate = new Date(reportDate)
            toDate.setDate(toDate.getDate() - 1)
            period.quoteToDate = toDate.toISOString().split("T")[0]
            if (!period.quoteFirstAfterLastReportDate) {
                const from = new Date(reportDate)
                from.setMonth(from.getMonth() - 3)
                period.quoteFromDate = from.toISOString().split("T")[0]
            } else {
                period.quoteFromDate = period.quoteFirstAfterLastReportDate
            }
            retrievePrices(company.ticker, period.quoteFromDate, period.quoteToDate)
        }
        // eslint-disable-next-line
    }, [reportDate])

    function createFinancial() {
        const financialData = {id: period.id, reportDate: reportDate, priceLow: priceLow, priceHigh: priceHigh,
            shares: shares, revenue: revenue, grossProfit: grossProfit, operatingIncome: operatingIncome, netIncome: netIncome, dividend: dividend}
        axios.put(backend + "/period/financial", financialData)
            .then((response) => {
                triggerRefresh()
                handleClose()
            }).catch((error) => {setAlert(formatError(error))})
    }

    async function retrieveFinancials() {
        const financial = await getFinancial(company.ticker, period.name.year, period.name.type);

        financial
            ? setFinancialImportInfo("found: " + financial.start_date + " => " + financial.end_date)
            : setFinancialImportInfo("not found")

        const formatted = formatPolygonIoFinancial(financial)
        setShares(formatted.shares)
        setRevenue(formatted.revenue)
        setGrossProfit(formatted.grossProfit)
        setOperatingIncome(formatted.operatingIncome)
        setNetIncome(formatted.netIncome)
    }

    async function retrievePrices(ticker, from, to) {
        const quote = await getQuote(ticker, from, to);
        if (quote) {
            setPriceImportInfo("found: " + from + " => " + to)
            setPriceHigh(quote.h.toString())
            setPriceLow(quote.l.toString())
        } else {
            setPriceImportInfo("not found")
        }
    }

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            PaperProps={{component: 'form', onSubmit: (event) => {event.preventDefault();createFinancial()},}}
        >
            <DialogTitle>Add Financial for {company.ticker} {period ? formatPeriodName(period.name) : ""}</DialogTitle>
            <DialogContent>
                <Typography>{financialImportInfo}</Typography>
                <DialogTextField
                    id="company-financial-shares"
                    value={shares}
                    label="Shares (in Millions)"
                    onChange={(e) => {setShares(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(shares, false, 8, 2, false)}
                />
                <DialogTextField
                    id="company-financial-revenue"
                    value={revenue}
                    label="Revenue (in Millions)"
                    onChange={(e) => {setRevenue(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(revenue, false, 8, 2, false)}
                />
                <DialogTextField
                    id="company-financial-gross-profit"
                    value={grossProfit}
                    label="Gross Profit (in Millions)"
                    onChange={(e) => {setGrossProfit(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(grossProfit, false, 8, 2, true)}
                />
                <DialogTextField
                    id="company-financial-operating-income"
                    value={operatingIncome}
                    label="Operating Income (in Millions)"
                    onChange={(e) => {setOperatingIncome(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(operatingIncome, false, 8, 2, true)}
                />
                <DialogTextField
                    id="company-financial-net-income"
                    value={netIncome}
                    label="Net Income (in Millions)"
                    onChange={(e) => {setNetIncome(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(netIncome, false, 8, 2, true)}
                />
                <DialogTextField
                    id="company-financial-dividend"
                    value={dividend}
                    label="Dividend (in Millions)"
                    onChange={(e) => {setDividend(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(dividend, false, 8, 2, false)}
                />
                <DialogDatePicker
                    id="trader-period-report-date"
                    value={reportDate}
                    onChange={(e) => {setReportDate(e.target.value);setAlert(null);}}
                />
                <Typography>{priceImportInfo}</Typography>
                <DialogTextField
                    id="trader-period-price-high"
                    value={priceHigh}
                    label="Highest Price"
                    onChange={(e) => {setPriceHigh(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(priceHigh, false, 10, 4, false)}
                />
                <DialogTextField
                    id="trader-period-price-low"
                    value={priceLow}
                    label="Lowest Price"
                    onChange={(e) => {setPriceLow(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(priceLow, false, 10, 4, false)}
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
export default AddPeriodFinancialDialog