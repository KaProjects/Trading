import {
    Alert,
    AlertTitle,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    FormControl,
    InputLabel,
    MenuItem,
    Select,
    Typography
} from "@mui/material";
import React, {useEffect, useState} from "react";
import axios from "axios";
import {backend} from "../properties";
import {formatError} from "../service/FormattingService";
import {validateNumber} from "../service/ValidationService";
import {DialogDatePicker} from "./component/DialogDatePicker";
import {DialogTextField} from "./component/DialogTextField";

const inputValue = value => value == null ? "" : String(value)

export const EditTradeDialog = props => {
    const trade = props.openEditTrade
    const active = trade ? (trade.active ?? trade.sellDate == null) : true
    const handleClose = () => props.setOpenEditTrade(null)

    const [alert, setAlert] = useState(null)
    const [purchaseDate, setPurchaseDate] = useState("")
    const [quantity, setQuantity] = useState("")
    const [purchasePrice, setPurchasePrice] = useState("")
    const [purchaseFees, setPurchaseFees] = useState("")
    const [portfolio, setPortfolio] = useState("")
    const [sellDate, setSellDate] = useState("")
    const [sellPrice, setSellPrice] = useState("")
    const [sellFees, setSellFees] = useState("")

    useEffect(() => {
        if (!trade) return

        setAlert(null)
        setPurchaseDate(inputValue(trade.purchaseDate))
        setQuantity(inputValue(trade.purchaseQuantity))
        setPurchasePrice(inputValue(trade.purchasePrice))
        setPurchaseFees(inputValue(trade.purchaseFees))
        setPortfolio(trade.portfolio?.key ?? "")
        setSellDate(active ? "" : inputValue(trade.sellDate))
        setSellPrice(active ? "" : inputValue(trade.sellPrice))
        setSellFees(active ? "" : inputValue(trade.sellFees))
    }, [trade, active])

    function updateTrade() {
        const tradeData = {
            purchaseDate,
            quantity,
            purchasePrice,
            purchaseFees,
            portfolio: portfolio || null,
            sellDate: active ? null : sellDate,
            sellPrice: active ? null : sellPrice,
            sellFees: active ? null : sellFees,
        }

        axios.put(backend + "/trade/" + trade.id, tradeData)
            .then(() => {
                props.triggerRefresh()
                handleClose()
            })
            .catch(error => setAlert(formatError(error)))
    }

    return (
        <Dialog
            open={!!trade}
            onClose={handleClose}
            slotProps={{paper: {component: "form", onSubmit: event => {event.preventDefault();updateTrade()},}}}
        >
            <DialogTitle>Edit {trade?.company?.ticker} Trade</DialogTitle>
            <DialogContent>
                <Typography variant="subtitle2" sx={{marginTop: "8px"}}>Purchase</Typography>
                <DialogDatePicker
                    id="trader-edit-trade-purchase-date"
                    label="Purchase date"
                    value={purchaseDate}
                    onChange={event => {setPurchaseDate(event.target.value);setAlert(null);}}
                />
                <DialogTextField
                    id="trader-edit-trade-quantity"
                    label="Quantity"
                    value={quantity}
                    onChange={event => {setQuantity(event.target.value);setAlert(null);}}
                    validate={() => validateNumber(quantity, false, 8, 4, false)}
                />
                <DialogTextField
                    id="trader-edit-trade-purchase-price"
                    label="Purchase price"
                    value={purchasePrice}
                    onChange={event => {setPurchasePrice(event.target.value);setAlert(null);}}
                    validate={() => validateNumber(purchasePrice, false, 10, 4, false)}
                />
                <DialogTextField
                    id="trader-edit-trade-purchase-fees"
                    label="Purchase fees"
                    value={purchaseFees}
                    onChange={event => {setPurchaseFees(event.target.value);setAlert(null);}}
                    validate={() => validateNumber(purchaseFees, false, 5, 2, false)}
                />
                <FormControl fullWidth variant="standard" sx={{marginTop: "20px"}}>
                    <InputLabel id="trader-edit-trade-portfolio-label">Portfolio</InputLabel>
                    <Select
                        labelId="trader-edit-trade-portfolio-label"
                        value={portfolio}
                        onChange={event => {setPortfolio(event.target.value);setAlert(null);}}
                    >
                        <MenuItem value=""></MenuItem>
                        {(props.portfolios ?? []).map(item => (
                            <MenuItem key={item.key} value={item.key}>{item.name}</MenuItem>
                        ))}
                    </Select>
                </FormControl>
                {!active &&
                    <>
                        <Divider sx={{marginTop: "16px"}}/>
                        <Typography variant="subtitle2" sx={{marginTop: "12px"}}>Sale</Typography>
                        <DialogDatePicker
                            id="trader-edit-trade-sell-date"
                            label="Sell date"
                            value={sellDate}
                            onChange={event => {setSellDate(event.target.value);setAlert(null);}}
                        />
                        <DialogTextField
                            id="trader-edit-trade-sell-price"
                            label="Sell price"
                            value={sellPrice}
                            onChange={event => {setSellPrice(event.target.value);setAlert(null);}}
                            validate={() => validateNumber(sellPrice, false, 10, 4, false)}
                        />
                        <DialogTextField
                            id="trader-edit-trade-sell-fees"
                            label="Sell fees"
                            value={sellFees}
                            onChange={event => {setSellFees(event.target.value);setAlert(null);}}
                            validate={() => validateNumber(sellFees, false, 5, 2, false)}
                        />
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
                <Button type="submit">Save</Button>
            </DialogActions>
        </Dialog>
    )
}
