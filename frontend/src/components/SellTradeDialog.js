import {
    Alert,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    MenuItem,
    Select, Table, TableBody, TableCell, TableHead, TableRow,
    TextField
} from "@mui/material";
import {handleError, validateNumber} from "../utils";
import React, {useEffect, useState} from "react";
import {domain} from "../properties";
import axios from "axios";


const SellTradeDialog = props => {
    const {handleClose, open} = props

    const [alert, setAlert] = useState(null);
    const [date, setDate] = useState("");
    const [price, setPrice] = useState("");
    const [fees, setFees] = useState("");
    const [company, setCompany] = useState("");
    const [trades, setTrades] = useState([]);

    useEffect(() => {
        if (open) {
            setAlert(null)
            setDate("")
            setPrice("")
            setFees("")
            selectCompany(props.companySelectorValue)
            setTrades([])
        }
        // eslint-disable-next-line
    }, [open]);


    function sellTrade() {
        let totalQuantity = 0
        const tradesToSell = []
        trades.forEach(trade => {
            if (validateQuantity(trade) === "") {
                const quantity = Number(trade.sellQuantity)
                if (quantity > 0) {
                    totalQuantity += quantity
                    tradesToSell.push({tradeId: trade.id, quantity: quantity})
                }
            }
        })

        axios.put(domain + "/trade", {date: date, price: price, fees: fees, trades: tradesToSell})
            .then((response) => {
                const title = "sold " + totalQuantity + "@" + price + company.currency
                const recordData = {companyId: company.id, title: title, date: date, price: price}
                axios.post(domain + "/record", recordData)
                    .then((response) => {
                        handleClose()
                    }).catch((error) => {setAlert(handleError(error))})
            }).catch((error) => {setAlert(handleError(error))})
    }

    function selectCompany(company) {
        if (company){
            axios.get(domain + "/trade?active=true&companyId=" + company.id)
                .then((response) => {
                    setTrades(response.data.trades)
                }).catch((error) => {setAlert(handleError(error))})
        }
        setCompany(company)
    }

    function validateQuantity(trade) {
        const numberInvalid = validateNumber(trade.sellQuantity ? trade.sellQuantity : "", true, 8, 4)
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
            PaperProps={{component: 'form', onSubmit: (event) => {event.preventDefault();sellTrade()},}}
        >
            <DialogTitle>Sell Trade</DialogTitle>
            <DialogContent>
                <TextField required margin="dense" fullWidth variant="standard"id="trader-sell-trade-date"
                           type="date"
                           value={date}
                           onChange={(e) => setDate(e.target.value)}
                           error={date === ""}
                />
                <TextField required margin="dense" fullWidth variant="standard"id="trader-sell-trade-price"
                           value={price}
                           label="Price"
                           onChange={(e) => setPrice(e.target.value)}
                           error={validateNumber(price, false, 10, 4) !== ""}
                           helperText={validateNumber(price, false, 10, 4) }
                />
                <TextField required margin="dense" fullWidth variant="standard"id="trader-sell-trade-fees"
                           value={fees}
                           label="Fees"
                           onChange={(e) => setFees(e.target.value)}
                           error={validateNumber(fees, false, 5, 2) !== ""}
                           helperText={validateNumber(fees, false, 5, 2) }
                />
                <Select required margin="dense" fullWidth variant="standard" displayEmpty
                        value={company}
                        error={company === ""}
                        onChange={event => selectCompany(event.target.value)}
                        sx={{marginTop: "15px"}}
                >
                    <MenuItem value=""></MenuItem>
                    {props.companies.map((company, index) => (
                        <MenuItem key={index} value={company} >{(company.ticker === undefined) ? company : company.ticker}</MenuItem>
                    ))}
                </Select>
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
                                    <TextField margin="dense" fullWidth variant="standard" id="trader-sell-trade-quantity"
                                               value={trade.sellQuantity ? trade.sellQuantity : ""}
                                               onChange={(e) => {const newTrades = [...trades];newTrades[index].sellQuantity = e.target.value; setTrades([...newTrades]);}}
                                               error={validateQuantity(trade) !== ""}
                                               helperText={validateQuantity(trade)}
                                    />
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </DialogContent>
            {alert &&
                <Alert severity="error" variant="filled">
                    {alert}
                </Alert>
            }
            <DialogActions>
                <Button onClick={() => handleClose()}>Cancel</Button>
                <Button type="submit">Sell</Button>
            </DialogActions>
        </Dialog>
    )
}
export default SellTradeDialog;