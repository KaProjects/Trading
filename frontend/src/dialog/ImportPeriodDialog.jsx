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
    Stack
} from "@mui/material";
import React, {useEffect, useState} from "react";
import {DialogTextField} from "./component/DialogTextField";
import {DialogDatePicker} from "./component/DialogDatePicker";
import {validateNumber} from "../service/ValidationService";
import {getFinancial, getQuote} from "../service/PolygonIoService";
import {formatError, formatPolygonIoFinancial} from "../service/FormattingService";
import {backend} from "../properties";
import axios from "axios";


export const ImportPeriodDialog = props => {
    const {company, periods, open, handleClose, triggerRefresh} = props

    const [period, setPeriod] = useState(null)
    const [suggestion, setSuggestion] = useState(null)

    const [alert, setAlert] = useState(null)
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (open) {
            setPeriod(null)
            setAlert(null)
        }
        // eslint-disable-next-line
    }, [open])

    function createPeriod() {
        const data = {...period}
        data.companyId = company.id
        axios.post(backend + "/period/import", data)
            .then((response) => {
                triggerRefresh()
                handleClose()
            }).catch((error) => {setAlert(formatError(error))})
    }

    async function selectPeriod(period) {
        if (period.isReported) {
            setLoading(true)
            const financial = await getFinancial(company.ticker, "20" + period.name.substring(0,2), period.name.substring(2,4));
            const suggestion = formatPolygonIoFinancial(financial)

            const quote = await getQuote(company.ticker, period.previousReportDate, period.reportDate);
            suggestion.priceHigh = quote ? quote.h.toString() : ""
            suggestion.priceLow = quote ? quote.l.toString() : ""

            setSuggestion(suggestion)
        }
        setPeriod(period)
        setLoading(false)
    }

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            PaperProps={{component: 'form', onSubmit: (event) => {event.preventDefault();createPeriod()},}}
        >
            <DialogTitle>Add Period {period && period.name}</DialogTitle>
            <DialogContent sx={{display: "flex", flexDirection: "column", gap: 2}}>
                {loading &&
                    <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", height: "100%" }}>
                        <CircularProgress />
                    </Box>
                }
                {(!period && !loading) && periods.map((period, index) => (
                    <Button key={index} onClick={() => selectPeriod(period)}>
                        {`${period.name.toString()}${period.isReported ? "" : "*"}`}
                    </Button>
                ))}
                {(period && !loading) &&
                    <>
                        <DialogTextField
                            id="trader-period-name"
                            value={period.name}
                            label="Name"
                            onChange={(e) => {setPeriod(prev => ({ ...prev, name: e.target.value }));setAlert(null);}}
                            validate={() => period.name.length !== 4 ? "exactly 4 symbols, e.g. 25FY, 25Q1, ..." : ""}
                        />
                        <DialogDatePicker
                            id="trader-period-end-month"
                            type="month"
                            value={period.endingMonth}
                            label="Ending Month"
                            onChange={(e) => {setPeriod(prev => ({ ...prev, endingMonth: e.target.value }));setAlert(null);}}
                        />
                        {period.isReported &&
                            <>
                                <Stack direction="row" spacing={2}>
                                    <DialogTextField
                                        id="company-financial-shares"
                                        value={period.shares}
                                        label="Shares (in Millions)"
                                        onChange={(e) => {setPeriod(prev => ({ ...prev, shares: e.target.value }));setAlert(null);}}
                                        validate={() => validateNumber(period.shares, false, 8, 2, false)}
                                    />
                                    {suggestion?.shares &&
                                        <Button
                                            sx={{ minWidth: 120 }}
                                            onClick={(e) => {
                                                setPeriod(prev => ({ ...prev, shares: suggestion.shares }));
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
                                        value={period.revenue}
                                        label="Revenue (in Millions)"
                                        onChange={(e) => {setPeriod(prev => ({ ...prev, revenue: e.target.value }));setAlert(null);}}
                                        validate={() => validateNumber(period.revenue, false, 8, 2, false)}
                                    />
                                    {suggestion?.revenue &&
                                        <Button
                                            sx={{ minWidth: 120 }}
                                            onClick={(e) => {
                                                setPeriod(prev => ({ ...prev, revenue: suggestion.revenue }));
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
                                        value={period.grossProfit}
                                        label="Gross Profit (in Millions)"
                                        onChange={(e) => {setPeriod(prev => ({ ...prev, grossProfit: e.target.value }));setAlert(null);}}
                                        validate={() => validateNumber(period.grossProfit, false, 8, 2, true)}
                                    />
                                    {suggestion?.grossProfit &&
                                        <Button
                                            sx={{ minWidth: 120 }}
                                            onClick={(e) => {
                                                setPeriod(prev => ({ ...prev, grossProfit: suggestion.grossProfit }));
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
                                        value={period.operatingIncome}
                                        label="Operating Income (in Millions)"
                                        onChange={(e) => {setPeriod(prev => ({ ...prev, operatingIncome: e.target.value }));setAlert(null);}}
                                        validate={() => validateNumber(period.operatingIncome, false, 8, 2, true)}
                                    />
                                    {suggestion?.operatingIncome &&
                                        <Button
                                            sx={{ minWidth: 120 }}
                                            onClick={(e) => {
                                                setPeriod(prev => ({ ...prev, operatingIncome: suggestion.operatingIncome }));
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
                                        value={period.netIncome}
                                        label="Net Income (in Millions)"
                                        onChange={(e) => {setPeriod(prev => ({ ...prev, netIncome: e.target.value }));setAlert(null);}}
                                        validate={() => validateNumber(period.netIncome, false, 8, 2, true)}
                                    />
                                    {suggestion?.netIncome &&
                                        <Button
                                            sx={{ minWidth: 120 }}
                                            onClick={(e) => {
                                                setPeriod(prev => ({ ...prev, netIncome: suggestion.netIncome }));
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
                                        value={period.dividend}
                                        label="Dividend (in Millions)"
                                        onChange={(e) => {setPeriod(prev => ({ ...prev, dividend: e.target.value }));setAlert(null);}}
                                        validate={() => validateNumber(period.dividend, false, 8, 2, false)}
                                    />
                                    {suggestion?.dividend &&
                                        <Button
                                            sx={{ minWidth: 120 }}
                                            onClick={(e) => {
                                                setPeriod(prev => ({ ...prev, dividend: suggestion.dividend }));
                                                setSuggestion(prev => ({ ...prev, dividend: undefined }));
                                            }}
                                        >
                                            {`<< ${suggestion.dividend}`}
                                        </Button>
                                    }
                                </Stack>
                                <DialogDatePicker
                                    id="trader-period-report-date"
                                    value={period.reportDate}
                                    onChange={(e) => {setPeriod(prev => ({ ...prev, reportDate: e.target.value }));setAlert(null);}}
                                />
                                <Stack direction="row" spacing={2}>
                                    <DialogTextField
                                        id="trader-period-price-high"
                                        value={period.priceHigh}
                                        label="Highest Price"
                                        onChange={(e) => {setPeriod(prev => ({ ...prev, priceHigh: e.target.value }));setAlert(null);}}
                                        validate={() => validateNumber(period.priceHigh, false, 10, 4, false)}
                                    />
                                    {suggestion?.priceHigh &&
                                        <Button
                                            sx={{ minWidth: 120 }}
                                            onClick={(e) => {
                                                setPeriod(prev => ({ ...prev, priceHigh: suggestion.priceHigh }));
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
                                        value={period.priceLow}
                                        label="Lowest Price"
                                        onChange={(e) => {setPeriod(prev => ({ ...prev, priceLow: e.target.value }));setAlert(null);}}
                                        validate={() => validateNumber(period.priceLow, false, 10, 4, false)}
                                    />
                                    {suggestion?.priceLow &&
                                        <Button
                                            sx={{ minWidth: 120 }}
                                            onClick={(e) => {
                                                setPeriod(prev => ({ ...prev, priceLow: suggestion.priceLow }));
                                                setSuggestion(prev => ({ ...prev, priceLow: undefined }));
                                            }}
                                        >
                                            {`<< ${suggestion.priceLow}`}
                                        </Button>
                                    }
                                </Stack>
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
                        <Button onClick={() => setPeriod(null)}>Back</Button>
                        <Button type="submit">Create</Button>
                    </>
                }
            </DialogActions>
        </Dialog>
    )
}
