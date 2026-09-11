import React, {useState} from "react";
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
    FormControl,
    InputLabel,
    MenuItem,
    Paper,
    Select,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TextField,
    Typography,
} from "@mui/material";
import axios from "axios";
import {backend} from "../properties";
import {formatDate, formatDecimals, formatError} from "../service/FormattingService";

function splitQuantity(quantity, from, to) {
    return quantity / from * to
}

function splitPrice(price, from, to) {
    return price * from / to
}

function SplitValue({previous, next}) {
    if (!next) return previous

    return (
        <Box component="span" sx={{display: "inline-flex", alignItems: "center", justifyContent: "flex-end", gap: "6px"}}>
            <Box component="span" sx={{color: "text.secondary"}}>{previous}</Box>
            <Box component="span" sx={{color: "text.disabled"}}>&#8594;</Box>
            <Box component="span" sx={{fontWeight: 600}}>{next}</Box>
        </Box>
    )
}

export const StockSplit = props => {
    const [companyId, setCompanyId] = useState("")
    const [ratioFrom, setRatioFrom] = useState("")
    const [ratioTo, setRatioTo] = useState("")
    const [splitDate, setSplitDate] = useState("")
    const [openTrades, setOpenTrades] = useState([])
    const [closedTrades, setClosedTrades] = useState([])
    const [loading, setLoading] = useState(false)
    const [alert, setAlert] = useState(null)
    const [lookupWarning, setLookupWarning] = useState(null)
    const [confirming, setConfirming] = useState(false)
    const [applying, setApplying] = useState(false)
    const [applied, setApplied] = useState(null)

    const companies = props.companyLists?.all ?? []
    const selectedCompany = companies.find(item => item.id === companyId) ?? null
    const from = Number(ratioFrom)
    const to = Number(ratioTo)
    const ratioValid = ratioFrom !== "" && ratioTo !== "" && from > 0 && to > 0
    const previewReady = ratioValid && splitDate !== ""
    const affectedClosedTrades = splitDate === ""
        ? []
        : closedTrades.filter(trade => trade.sellDate && trade.sellDate >= splitDate)

    function selectCompany(id) {
        setCompanyId(id)
        const company = companies.find(item => item.id === id)
        if (!company) return

        setOpenTrades([])
        setClosedTrades([])
        setAlert(null)
        setLookupWarning(null)
        setApplied(null)
        setRatioFrom("")
        setRatioTo("")
        setSplitDate("")
        lookUpSplits(company.ticker)
        setLoading(true)
        Promise.all([
            axios.get(`${backend}/trade/`, {params: {active: true, companyId: company.id}}),
            axios.get(`${backend}/trade/`, {params: {active: false, companyId: company.id}}),
        ])
            .then(([open, closed]) => {
                setOpenTrades(open.data?.trades ?? [])
                setClosedTrades(closed.data?.trades ?? [])
            })
            .catch(error => setAlert(formatError(error)))
            .finally(() => setLoading(false))
    }

    function lookUpSplits(ticker) {
        axios.get(`${backend}/company/polygon/splits`, {params: {ticker}})
            .then(response => {
                const splits = response.data ?? []
                if (splits.length === 0) {
                    setLookupWarning({
                        title: `No split reported for ${ticker} in the last 12 months, fill it in manually.`,
                        splits: [],
                    })
                    return
                }

                const latest = splits[0]
                setRatioFrom(String(latest.splitFrom))
                setRatioTo(String(latest.splitTo))
                setSplitDate(latest.executionDate)

                if (splits.length > 1) {
                    setLookupWarning({
                        title: `${splits.length} splits reported for ${ticker} in the last 12 months,`
                            + " the latest one was filled in. Check which one you meant.",
                        splits,
                    })
                }
            })
            .catch(() => setLookupWarning({
                title: `Could not load reported splits for ${ticker}, fill it in manually.`,
                splits: [],
            }))
    }

    function applySplit() {
        setConfirming(false)
        setApplying(true)
        setAlert(null)
        axios.post(`${backend}/split/`, {
            companyId: selectedCompany.id,
            splitFrom: from,
            splitTo: to,
            date: splitDate,
        })
            .then(response => {
                setApplied(response.data ?? {})
                setRatioFrom("")
                setRatioTo("")
                setSplitDate("")
                return axios.get(`${backend}/trade/`, {params: {active: true, companyId: selectedCompany.id}})
                    .then(open => setOpenTrades(open.data?.trades ?? []))
            })
            .catch(error => setAlert(formatError(error)))
            .finally(() => setApplying(false))
    }

    function describeSplit(split) {
        return `${split.splitFrom}:${split.splitTo} on ${formatDate(split.executionDate)}`
    }

    function describeTrade(trade) {
        return `${formatDecimals(trade.purchaseQuantity, 0, 4)} @ ${formatDecimals(trade.purchasePrice, 2, 2)}`
            + ` bought ${formatDate(trade.purchaseDate)}, sold ${formatDate(trade.sellDate)}`
    }

    return (
        <Box sx={{maxWidth: 640, margin: "20px auto", padding: "0 8px"}}>
            <Typography variant="h6" sx={{marginBottom: "16px"}}>Stock Split</Typography>

            {alert &&
                <Alert severity="error" variant="filled" sx={{marginBottom: "16px"}}>
                    <AlertTitle>{alert.title}</AlertTitle>{alert.message}
                </Alert>
            }

            <Box sx={{display: "flex", gap: 2, alignItems: "flex-end", marginBottom: "24px"}}>
                <FormControl sx={{minWidth: 220}} variant="standard">
                    <InputLabel id="split-company-label">Company</InputLabel>
                    <Select
                        labelId="split-company-label"
                        value={companyId}
                        onChange={event => selectCompany(event.target.value)}
                    >
                        {companies.map(company => (
                            <MenuItem key={company.id} value={company.id}>{company.ticker}</MenuItem>
                        ))}
                    </Select>
                </FormControl>
            </Box>

            {loading &&
                <Box sx={{display: "flex", justifyContent: "center", padding: "24px"}}>
                    <CircularProgress/>
                </Box>
            }

            {selectedCompany && !loading &&
                <>
                    <Box sx={{display: "flex", gap: 2, alignItems: "flex-end", flexWrap: "wrap", marginBottom: "24px"}}>
                        <TextField
                            label="Split from"
                            variant="standard"
                            value={ratioFrom}
                            onChange={event => setRatioFrom(event.target.value)}
                            error={ratioFrom !== "" && !(from > 0)}
                            helperText={ratioFrom !== "" && !(from > 0) ? "must be greater than zero" : " "}
                            sx={{width: 110}}
                        />
                        <Typography sx={{paddingBottom: "18px"}}>:</Typography>
                        <TextField
                            label="Split to"
                            variant="standard"
                            value={ratioTo}
                            onChange={event => setRatioTo(event.target.value)}
                            error={ratioTo !== "" && !(to > 0)}
                            helperText={ratioTo !== "" && !(to > 0) ? "must be greater than zero" : " "}
                            sx={{width: 110}}
                        />
                        <TextField
                            type="date"
                            label="Split date"
                            variant="standard"
                            value={splitDate}
                            onChange={event => setSplitDate(event.target.value)}
                            slotProps={{inputLabel: {shrink: true}}}
                            helperText=" "
                            sx={{width: 180}}
                        />
                    </Box>

                    {lookupWarning &&
                        <Alert severity="warning" sx={{marginBottom: "24px"}}>
                            <AlertTitle sx={{marginBottom: lookupWarning.splits.length > 0 ? undefined : 0}}>
                                {lookupWarning.title}
                            </AlertTitle>
                            {lookupWarning.splits.map(split => (
                                <Box key={split.executionDate}>{describeSplit(split)}</Box>
                            ))}
                        </Alert>
                    }

                    {affectedClosedTrades.length > 0 &&
                        <Alert severity="warning" sx={{marginBottom: "24px"}}>
                            <AlertTitle>
                                Closed trades on or after the split date, handle these manually
                            </AlertTitle>
                            {affectedClosedTrades.map(trade => (
                                <Box key={trade.id}>{describeTrade(trade)}</Box>
                            ))}
                        </Alert>
                    }

                    <Typography color="text.secondary" sx={{marginBottom: "8px"}}>
                        Open trades for {selectedCompany.ticker}
                    </Typography>

                    {openTrades.length === 0 &&
                        <Typography color="text.secondary">No open trades for this company.</Typography>
                    }

                    {openTrades.length > 0 &&
                        <TableContainer component={Paper} sx={{width: "100%"}}>
                            <Table size="small">
                                <TableHead>
                                    <TableRow>
                                        <TableCell>Purchased</TableCell>
                                        <TableCell align="right">Quantity</TableCell>
                                        <TableCell align="right">Price</TableCell>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {openTrades.map(trade => (
                                        <TableRow key={trade.id}>
                                            <TableCell>{formatDate(trade.purchaseDate)}</TableCell>
                                            <TableCell align="right">
                                                <SplitValue
                                                    previous={formatDecimals(trade.purchaseQuantity, 0, 4)}
                                                    next={previewReady
                                                        && formatDecimals(splitQuantity(trade.purchaseQuantity, from, to), 0, 4)}
                                                />
                                            </TableCell>
                                            <TableCell align="right">
                                                <SplitValue
                                                    previous={formatDecimals(trade.purchasePrice, 2, 2)}
                                                    next={previewReady
                                                        && formatDecimals(splitPrice(trade.purchasePrice, from, to), 2, 4)}
                                                />
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        </TableContainer>
                    }

                    {!previewReady && openTrades.length > 0 &&
                        <Typography color="text.secondary" sx={{marginTop: "8px", fontSize: 13}}>
                            Fill in the split ratio and date to preview the changes.
                        </Typography>
                    }

                    <Box sx={{display: "flex", justifyContent: "flex-end", marginTop: "16px"}}>
                        <Button
                            variant="contained"
                            disabled={applying || !previewReady || openTrades.length === 0}
                            onClick={() => setConfirming(true)}
                        >
                            Apply split
                        </Button>
                    </Box>
                </>
            }

            {applied &&
                <Alert severity="success" sx={{marginTop: "16px"}}>
                    <AlertTitle sx={{marginBottom: applied.warnings?.length ? undefined : 0}}>
                        {applied.updatedTrades} trade{applied.updatedTrades === 1 ? "" : "s"} updated
                        {applied.recordCreated ? " and a record was created." : "."}
                    </AlertTitle>
                    {(applied.warnings ?? []).map(warning => <Box key={warning}>{warning}</Box>)}
                </Alert>
            }

            <Dialog open={confirming} onClose={() => setConfirming(false)}>
                <DialogTitle>Apply split</DialogTitle>
                <DialogContent>
                    <Typography>
                        Rewrite {openTrades.length} open {selectedCompany?.ticker} trade
                        {openTrades.length === 1 ? "" : "s"} for a {ratioFrom}:{ratioTo} split
                        on {splitDate && formatDate(splitDate)}? A record will be created for the company.
                    </Typography>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setConfirming(false)}>Cancel</Button>
                    <Button variant="contained" onClick={applySplit}>Apply</Button>
                </DialogActions>
            </Dialog>
        </Box>
    )
}
