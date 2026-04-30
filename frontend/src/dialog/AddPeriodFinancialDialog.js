import {
    Alert,
    AlertTitle,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Typography
} from "@mui/material";
import React, {useEffect, useState} from "react";
import axios from "axios";
import {backend} from "../properties";
import {getFinancial, getQuote} from "../service/PolygonIoService";
import {formatError, formatPeriodName} from "../service/FormattingService";
import {validateNumber} from "../service/ValidationService";
import {DialogTextField} from "./component/DialogTextField";

const AddPeriodFinancialDialog = props => {
    const {handleClose, open, period} = props

    const [alert, setAlert] = useState(null)
    const [financialImportInfo, setFinancialImportInfo] = useState("")
    const [priceImportInfo, setPriceImportInfo] = useState("")

    const [reportDate, setReportDate] = useState("")
    const [shares, setShares] = useState("")
    const [revenue, setRevenue] = useState("")
    const [grossProfit, setGrossProfit] = useState("")
    const [operIncome, setOperIncome] = useState("")
    const [netIncome, setNetIncome] = useState("")
    const [dividend, setDividend] = useState("")
    const [priceH, setPriceH] = useState("")
    const [priceL, setPriceL] = useState("")

    useEffect(() => {
        if (open) {
            setAlert(null)
            setFinancialImportInfo("")
            setPriceImportInfo("")
            setReportDate(period.reportDate ? period.reportDate : "")
            setShares("")
            setRevenue("")
            setGrossProfit("")
            setOperIncome("")
            setNetIncome("")
            setDividend("")
            setPriceH("")
            setPriceL("")
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
            retrievePrices(props.companySelectorValue.ticker, period.quoteFromDate, period.quoteToDate)
        }
        // eslint-disable-next-line
    }, [reportDate])

    function createFinancial() {
        const financialData = {id: period.id, reportDate: reportDate, priceLow: priceL, priceHigh: priceH,
            shares: shares, revenue: revenue, grossProfit: grossProfit, operatingIncome: operIncome, netIncome: netIncome, dividend: dividend}
        axios.put(backend + "/period/financial", financialData)
            .then((response) => {
                props.triggerRefresh()
                handleClose()
            }).catch((error) => {setAlert(formatError(error))})
    }

    async function retrieveFinancials() {
        const financial = await getFinancial(props.companySelectorValue.ticker, period.name.year, period.name.type);

        if (financial) {
            setFinancialImportInfo("found: " + financial.start_date + " => " + financial.end_date)
            const shares = financial.financials.income_statement.basic_average_shares.value.toString()
            setShares((Number(shares) / 1_000_000).toString())
            const revenues = financial.financials.income_statement.revenues.value.toString()
            setRevenue((Number(revenues) / 1_000_000).toString())
            const grossProfit = financial.financials.income_statement.gross_profit.value.toString()
            setGrossProfit((Number(grossProfit) / 1_000_000).toString())
            const operIncome = financial.financials.income_statement.operating_income_loss.value.toString()
            setOperIncome((Number(operIncome) / 1_000_000).toString())
            const netIncome = financial.financials.income_statement.net_income_loss.value.toString()
            setNetIncome((Number(netIncome) / 1_000_000).toString())
        } else {
            setFinancialImportInfo("not found")
            setShares("")
            setRevenue("")
            setGrossProfit("")
            setOperIncome("")
            setNetIncome("")
        }
    }

    async function retrievePrices(ticker, from, to) {
        const quote = await getQuote(ticker, from, to);
        if (quote) {
            setPriceImportInfo("found: " + from + " => " + to)
            setPriceH(quote.h.toString())
            setPriceL(quote.l.toString())
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
            <DialogTitle>Add Financial for {props.companySelectorValue.ticker} {period ? formatPeriodName(period.name) : ""}</DialogTitle>
            <DialogContent>
                <Typography>{financialImportInfo}</Typography>
                <DialogTextField
                    id="company-financial-shares"
                    value={shares}
                    label="Shares (in Millions)"
                    onChange={(e) => {setShares(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(shares, false, 8, 2)}
                />
                <DialogTextField
                    id="company-financial-revenue"
                    value={revenue}
                    label="Revenue (in Millions)"
                    onChange={(e) => {setRevenue(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(revenue, false, 8, 2)}
                />
                <DialogTextField
                    id="company-financial-cogs"
                    value={grossProfit}
                    label="Cost of Goods Sold (in Millions)"
                    onChange={(e) => {setGrossProfit(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(grossProfit, false, 8, 2)}
                />
                <DialogTextField
                    id="company-financial-op-exp"
                    value={operIncome}
                    label="Operating Expenses (in Millions)"
                    onChange={(e) => {setOperIncome(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(operIncome, false, 8, 2)}
                />
                <DialogTextField
                    id="company-financial-netIncome"
                    value={netIncome}
                    label="Net Income (in Millions)"
                    onChange={(e) => {setNetIncome(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(netIncome, false, 8, 2)}
                />
                <DialogTextField
                    id="company-financial-dividend"
                    value={dividend}
                    label="Dividend (in Millions)"
                    onChange={(e) => {setDividend(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(dividend, false, 8, 2)}
                />
                <DialogTextField
                    id="trader-period-report-date"
                    type="date"
                    value={reportDate}
                    onChange={(e) => {setReportDate(e.target.value);setAlert(null);}}
                    validate={() => reportDate === "" ? "not blank" : ""}
                />
                <Typography>{priceImportInfo}</Typography>
                <DialogTextField
                    id="trader-period-price-h"
                    value={priceH}
                    label="Highest Price"
                    onChange={(e) => {setPriceH(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(priceH, false, 10, 4)}
                />
                <DialogTextField
                    id="trader-period-price-l"
                    value={priceL}
                    label="Lowest Price"
                    onChange={(e) => {setPriceL(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(priceL, false, 10, 4)}
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