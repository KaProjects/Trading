import {
    Alert,
    AlertTitle,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    FormHelperText,
    InputLabel,
    MenuItem,
    Select,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow
} from "@mui/material";
import React, {useEffect, useState} from "react";
import {backend} from "../properties";
import axios from "axios";
import {validateNumber} from "../service/ValidationService";
import {formatError} from "../service/FormattingService";
import {DialogTextField} from "./component/DialogTextField";
import {DialogDatePicker} from "./component/DialogDatePicker";


export const SellTradeDialog = props => {
    const open = props.openSellTrade
    const handleClose = () => props.setOpenSellTrade(false)

    const [alert, setAlert] = useState(null)
    const [date, setDate] = useState("")
    const [price, setPrice] = useState("")
    const [fees, setFees] = useState("")
    const [company, setCompany] = useState("")
    const [trades, setTrades] = useState([])

    useEffect(() => {
        if (open) {
            setAlert(null)
            setDate("")
            setPrice("")
            setFees("")
            const selectedOwnedCompany = (props.companyLists?.owned ?? [])
                .find(company => company.id === props.companySelectorValue?.id) ?? ""
            selectCompany(selectedOwnedCompany)
            setTrades([])
        }
        // eslint-disable-next-line
    }, [open])


    function sellTrade() {
        const tradesToSell = []
        trades.forEach(trade => {
            if (validateSellQuantity(trade) === "") {
                const quantity = Number(trade.sellQuantity)
                if (quantity > 0) {
                    tradesToSell.push({tradeId: trade.id, quantity: quantity})
                }
            }
        })

        axios.put(backend + "/trade", {companyId: company.id, date: date, price: price, fees: fees, trades: tradesToSell})
            .then((response) => {
                props.triggerRefresh()
                handleClose()
            }).catch((error) => {setAlert(formatError(error))})
    }

    function selectCompany(company) {
        if (company){
            axios.get(backend + "/trade?active=true&companyId=" + company.id)
                .then((response) => {
                    setTrades(response.data.trades.map(trade => ({
                        ...trade,
                        sellQuantity: trade.sellQuantity == null ? "" : String(trade.sellQuantity),
                    })))
                }).catch((error) => {setAlert(formatError(error))})
        } else {
            setTrades([])
        }
        setCompany(company)
    }

    function validateSellQuantity(trade) {
        const numberInvalid = validateNumber(trade.sellQuantity ? trade.sellQuantity : "", true, 8, 4, false)
        if (numberInvalid){
            return numberInvalid
        } else {
            return Number(trade.purchaseQuantity) < Number(trade.sellQuantity) ? "bigger than owned quantity" : ""
        }
    }

    return (
        <Dialog
            open={open}
            onClose={() => handleClose()}
            slotProps={{paper: {component: 'form', onSubmit: (event) => {event.preventDefault();sellTrade()},}}}
        >
            <DialogTitle>Sell Trade</DialogTitle>
            <DialogContent>
                <DialogDatePicker
                    id="trader-sell-trade-date"
                    value={date}
                    onChange={(e) => {setDate(e.target.value);setAlert(null);}}
                />
                <FormControl required error={!company} fullWidth variant="standard" sx={{marginTop: "15px"}}>
                    <InputLabel id="trader-sell-trade-company-label">Company</InputLabel>
                    <Select
                        labelId="trader-sell-trade-company-label"
                        value={company?.id ?? ""}
                        required
                        displayEmpty
                        onChange={event => {
                            const selectedCompany = (props.companyLists?.owned ?? [])
                                .find(company => company.id === event.target.value) ?? ""
                            selectCompany(selectedCompany)
                            setAlert(null)
                        }}
                    >
                        <MenuItem value=""></MenuItem>
                        {(props.companyLists?.owned ?? []).map(company => (
                            <MenuItem key={company.id} value={company.id}>{company.ticker}</MenuItem>
                        ))}
                    </Select>
                    <FormHelperText>{company ? "" : "not filled"}</FormHelperText>
                </FormControl>
                <DialogTextField
                    id="trader-sell-trade-price"
                    value={price}
                    label="Price"
                    onChange={(e) => {setPrice(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(price, false, 10, 4, false)}
                />
                <DialogTextField
                    id="trader-sell-trade-fees"
                    value={fees}
                    label="Fees"
                    onChange={(e) => {setFees(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(fees, false, 5, 2, false)}
                />
                <Table size="small" aria-label="a dense table" stickyHeader sx={{marginBottom: "20px"}}>
                    <TableHead>
                        <TableRow>
                            <TableCell>Date</TableCell>
                            <TableCell>Quantity</TableCell>
                            <TableCell>Price</TableCell>
                            <TableCell>Fees</TableCell>
                            <TableCell>Total</TableCell>
                            <TableCell>Sell</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {trades.map((trade, index) => (
                            <TableRow key={index}>
                                <TableCell>{trade.purchaseDate}</TableCell>
                                <TableCell>{trade.purchaseQuantity}</TableCell>
                                <TableCell>{trade.purchasePrice}{trade.currency}</TableCell>
                                <TableCell>{trade.purchaseFees}{trade.currency}</TableCell>
                                <TableCell>{trade.purchaseTotal}{trade.currency}</TableCell>
                                <TableCell>
                                    <DialogTextField
                                        required={false}
                                        id="trader-sell-trade-quantity"
                                        value={trade.sellQuantity ? trade.sellQuantity : ""}
                                        onChange={(e) => {
                                            setTrades(prev => prev.map((trade, i) =>
                                                i === index ? {...trade, sellQuantity: e.target.value} : trade
                                            ));
                                            setAlert(null);
                                        }}
                                        validate={() => validateSellQuantity(trade)}
                                    />
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </DialogContent>
            {alert &&
                <Alert severity="error" variant="filled">
                    <AlertTitle>{alert.title}</AlertTitle>{alert.message}
                </Alert>
            }
            <DialogActions>
                <Button onClick={() => handleClose()}>Cancel</Button>
                <Button type="submit">Sell</Button>
            </DialogActions>
        </Dialog>
    )
}
