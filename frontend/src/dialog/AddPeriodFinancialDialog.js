import {Alert, AlertTitle, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack} from "@mui/material";
import React, {useEffect, useState} from "react";
import axios from "axios";
import {backend} from "../properties";
import {getFinancial, getQuote} from "../service/PolygonIoService";
import {formatError, formatPeriodName, formatPolygonIoFinancial, orBlank} from "../service/FormattingService";
import {validateDate, validateNumber} from "../service/ValidationService";
import {DialogTextField} from "./component/DialogTextField";
import {DialogDatePicker} from "./component/DialogDatePicker";

const AddPeriodFinancialDialog = props => {
    const {handleClose, open, period, company, triggerRefresh} = props

    const [alert, setAlert] = useState(null)

    const initFinancial = {
        shares: "", revenue: "", grossProfit: "", operatingIncome: "", netIncome: "",
        dividend: "", priceHigh: "", priceLow: "", previousReportDate: "", reportDate: ""
    }
    const [financial, setFinancial] = useState(initFinancial)
    const [suggestion, setSuggestion] = useState({})


    useEffect(() => {
        if (open) {
            setAlert(null)
            const newFinancial = {...initFinancial}
            if (period.cachedData) {
                newFinancial.shares = orBlank(period.cachedData.shares)
                newFinancial.revenue = orBlank(period.cachedData.revenue)
                newFinancial.grossProfit = orBlank(period.cachedData.grossProfit)
                newFinancial.operatingIncome = orBlank(period.cachedData.operatingIncome)
                newFinancial.netIncome = orBlank(period.cachedData.netIncome)
                newFinancial.dividend = orBlank(period.cachedData.dividend)
                newFinancial.priceHigh = orBlank(period.cachedData.priceHigh)
                newFinancial.priceLow = orBlank(period.cachedData.priceLow)
                newFinancial.reportDate = orBlank(period.cachedData.reportDate)
                newFinancial.previousReportDate = orBlank(period.cachedData.previousReportDate)
            } else {
                newFinancial.reportDate = orBlank(period.reportDate)
                newFinancial.previousReportDate = orBlank(period.previousReportDate)
            }
            setFinancial(newFinancial)
            updateFinancialSuggestions()
        }
        // eslint-disable-next-line
    }, [open])

    useEffect(() => {
        if (financial.reportDate) {
            let toDate = new Date(financial.reportDate)
            toDate.setDate(toDate.getDate() - 1)
            toDate = toDate.toISOString().split("T")[0]
            let fromDate = null
            if (!financial.previousReportDate) {
                fromDate = new Date(financial.reportDate)
                fromDate.setMonth(fromDate.getMonth() - 3)
                fromDate = fromDate.toISOString().split("T")[0]
            } else {
                fromDate = new Date(financial.previousReportDate)
                fromDate.setDate(fromDate.getDate() + 1)
                fromDate = fromDate.toISOString().split("T")[0]
            }
            updatePriceSuggestions(company.ticker, fromDate, toDate)
        }
        // eslint-disable-next-line
    }, [financial.reportDate])

    async function updateFinancialSuggestions() {
        const financial = await getFinancial(company.ticker, period.name.year, period.name.type);
        const suggestion = formatPolygonIoFinancial(financial)
        setSuggestion(prev => ({
            ...prev,
            shares: suggestion.shares,
            revenue: suggestion.revenue,
            grossProfit: suggestion.grossProfit,
            operatingIncome: suggestion.operatingIncome,
            netIncome: suggestion.netIncome
        }));
    }

    async function updatePriceSuggestions(ticker, from, to) {
        const quote = await getQuote(ticker, from, to);
        if (quote) {
            setSuggestion(prev => ({
                ...prev,
                priceHigh: quote.h.toString(),
                priceLow: quote.l.toString()
            }));
        }
    }

    function createFinancial() {
        const financialData = {
            id: period.id,
            reportDate: financial.reportDate,
            priceLow: financial.priceLow,
            priceHigh: financial.priceHigh,
            shares: financial.shares,
            revenue: financial.revenue,
            grossProfit: financial.grossProfit,
            operatingIncome: financial.operatingIncome,
            netIncome: financial.netIncome,
            dividend: financial.dividend
        }
        axios.put(backend + "/period/financial", financialData)
            .then((response) => {
                triggerRefresh()
                handleClose()
            }).catch((error) => {setAlert(formatError(error))})
    }

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            PaperProps={{component: 'form', onSubmit: (event) => {event.preventDefault();createFinancial()},}}
        >
            <DialogTitle>Add Financial for {company.ticker} {period ? formatPeriodName(period.name) : ""}</DialogTitle>
            <DialogContent>
                <Stack direction="row" spacing={2}>
                    <DialogTextField
                        id="company-financial-shares"
                        value={financial.shares}
                        label="Shares (in Millions)"
                        onChange={(e) => {setFinancial(prev => ({ ...prev, shares: e.target.value }));setAlert(null);}}
                        validate={() => validateNumber(financial.shares, false, 8, 2, false)}
                    />
                    {suggestion?.shares &&
                        <Button
                            sx={{ minWidth: 120 }}
                            onClick={(e) => {
                                setFinancial(prev => ({ ...prev, shares: suggestion.shares }));
                                setSuggestion(prev => ({ ...prev, shares: undefined }));
                            }}
                        >
                            {`<< ${suggestion.shares}`}
                        </Button>
                    }
                </Stack>
                <Stack direction="row" spacing={2}>
                    <DialogTextField
                        id="company-financial-revenue"
                        value={financial.revenue}
                        label="Revenue (in Millions)"
                        onChange={(e) => {setFinancial(prev => ({ ...prev, revenue: e.target.value }));setAlert(null);}}
                        validate={() => validateNumber(financial.revenue, false, 8, 2, false)}
                    />
                    {suggestion?.revenue &&
                        <Button
                            sx={{ minWidth: 120 }}
                            onClick={(e) => {
                                setFinancial(prev => ({ ...prev, revenue: suggestion.revenue }));
                                setSuggestion(prev => ({ ...prev, revenue: undefined }));
                            }}
                        >
                            {`<< ${suggestion.revenue}`}
                        </Button>
                    }
                </Stack>
                <Stack direction="row" spacing={2}>
                    <DialogTextField
                        id="company-financial-gross-profit"
                        value={financial.grossProfit}
                        label="Gross Profit (in Millions)"
                        onChange={(e) => {setFinancial(prev => ({ ...prev, grossProfit: e.target.value }));setAlert(null);}}
                        validate={() => validateNumber(financial.grossProfit, false, 8, 2, true)}
                    />
                    {suggestion?.grossProfit &&
                        <Button
                            sx={{ minWidth: 120 }}
                            onClick={(e) => {
                                setFinancial(prev => ({ ...prev, grossProfit: suggestion.grossProfit }));
                                setSuggestion(prev => ({ ...prev, grossProfit: undefined }));
                            }}
                        >
                            {`<< ${suggestion.grossProfit}`}
                        </Button>
                    }
                </Stack>
                <Stack direction="row" spacing={2}>
                    <DialogTextField
                        id="company-financial-operating-income"
                        value={financial.operatingIncome}
                        label="Operating Income (in Millions)"
                        onChange={(e) => {setFinancial(prev => ({ ...prev, operatingIncome: e.target.value }));setAlert(null);}}
                        validate={() => validateNumber(financial.operatingIncome, false, 8, 2, true)}
                    />
                    {suggestion?.operatingIncome &&
                        <Button
                            sx={{ minWidth: 120 }}
                            onClick={(e) => {
                                setFinancial(prev => ({ ...prev, operatingIncome: suggestion.operatingIncome }));
                                setSuggestion(prev => ({ ...prev, operatingIncome: undefined }));
                            }}
                        >
                            {`<< ${suggestion.operatingIncome}`}
                        </Button>
                    }
                </Stack>
                <Stack direction="row" spacing={2}>
                    <DialogTextField
                        id="company-financial-net-income"
                        value={financial.netIncome}
                        label="Net Income (in Millions)"
                        onChange={(e) => {setFinancial(prev => ({ ...prev, netIncome: e.target.value }));setAlert(null);}}
                        validate={() => validateNumber(financial.netIncome, false, 8, 2, true)}
                    />
                    {suggestion?.netIncome &&
                        <Button
                            sx={{ minWidth: 120 }}
                            onClick={(e) => {
                                setFinancial(prev => ({ ...prev, netIncome: suggestion.netIncome }));
                                setSuggestion(prev => ({ ...prev, netIncome: undefined }));
                            }}
                        >
                            {`<< ${suggestion.netIncome}`}
                        </Button>
                    }
                </Stack>
                <Stack direction="row" spacing={2}>
                    <DialogTextField
                        id="company-financial-dividend"
                        value={financial.dividend}
                        label="Dividend (in Millions)"
                        onChange={(e) => {setFinancial(prev => ({ ...prev, dividend: e.target.value }));setAlert(null);}}
                        validate={() => validateNumber(financial.dividend, false, 8, 2, false)}
                    />
                    {suggestion?.dividend &&
                        <Button
                            sx={{ minWidth: 120 }}
                            onClick={(e) => {
                                setFinancial(prev => ({ ...prev, dividend: suggestion.dividend }));
                                setSuggestion(prev => ({ ...prev, dividend: undefined }));
                            }}
                        >
                            {`<< ${suggestion.dividend}`}
                        </Button>
                    }
                </Stack>
                <DialogDatePicker
                    id="trader-period-report-date"
                    value={financial.reportDate}
                    onChange={(e) => {setFinancial(prev => ({ ...prev, reportDate: e.target.value }));setAlert(null);}}
                    validate={() => validateDate(financial.reportDate, false, true)}
                />
                <Stack direction="row" spacing={2}>
                    <DialogTextField
                        id="trader-period-price-high"
                        value={financial.priceHigh}
                        label="Highest Price"
                        onChange={(e) => {setFinancial(prev => ({ ...prev, priceHigh: e.target.value }));setAlert(null);}}
                        validate={() => validateNumber(financial.priceHigh, false, 10, 4, false)}
                    />
                    {suggestion?.priceHigh &&
                        <Button
                            sx={{ minWidth: 120 }}
                            onClick={(e) => {
                                setFinancial(prev => ({ ...prev, priceHigh: suggestion.priceHigh }));
                                setSuggestion(prev => ({ ...prev, priceHigh: undefined }));
                            }}
                        >
                            {`<< ${suggestion.priceHigh}`}
                        </Button>
                    }
                </Stack>
                <Stack direction="row" spacing={2}>
                    <DialogTextField
                        id="trader-period-price-low"
                        value={financial.priceLow}
                        label="Lowest Price"
                        onChange={(e) => {setFinancial(prev => ({ ...prev, priceLow: e.target.value }));setAlert(null);}}
                        validate={() => validateNumber(financial.priceLow, false, 10, 4, false)}
                    />
                    {suggestion?.priceLow &&
                        <Button
                            sx={{ minWidth: 120 }}
                            onClick={(e) => {
                                setFinancial(prev => ({ ...prev, priceLow: suggestion.priceLow }));
                                setSuggestion(prev => ({ ...prev, priceLow: undefined }));
                            }}
                        >
                            {`<< ${suggestion.priceLow}`}
                        </Button>
                    }
                </Stack>
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