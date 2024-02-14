import {
    Alert,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    MenuItem,
    Select,
    TextField
} from "@mui/material";
import React, {useEffect, useState} from "react";
import {domain} from "../properties";
import axios from "axios";
import {handleError, validateNumber} from "../utils";


const AddTradeDialog = props => {
    const {handleClose, open} = props

    const [alert, setAlert] = useState(null)
    const [date, setDate] = useState("")
    const [price, setPrice] = useState("")
    const [quantity, setQuantity] = useState("")
    const [fees, setFees] = useState("")
    const [company, setCompany] = useState("")

    useEffect(() => {
        if (open) {
            setAlert(null)
            setDate("")
            setPrice("")
            setQuantity("")
            setFees("")
            setCompany(props.companySelectorValue)
        }
        // eslint-disable-next-line
    }, [open])

    function createTrade() {
        const tradeData = {companyId: company.id, date: date, price: price, quantity: quantity, fees: fees}
        axios.post(domain + "/trade", tradeData)
            .then((response) => {
                const title = "bought " + quantity + "@" + price + company.currency
                const recordData = {companyId: company.id, title: title, date: date, price: price}
                axios.post(domain + "/record", recordData)
                    .then((response) => {
                        props.triggerRefresh()
                        handleClose()
                    }).catch((error) => {setAlert(handleError(error))})
            }).catch((error) => {setAlert(handleError(error))})
    }

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            PaperProps={{component: 'form', onSubmit: (event) => {event.preventDefault();createTrade()},}}
        >
            <DialogTitle>Add Trade</DialogTitle>
            <DialogContent>
                <TextField required margin="dense" fullWidth variant="standard" id="trader-trade-date"
                           type="date"
                           value={date}
                           onChange={(e) => {setDate(e.target.value);setAlert(null);}}
                           error={date === ""}
                />
                <Select required margin="dense" fullWidth variant="standard" displayEmpty
                        value={company}
                        error={company === ""}
                        onChange={event => {setCompany(event.target.value);setAlert(null);}}
                        sx={{marginTop: "20px"}}
                >
                    <MenuItem value=""></MenuItem>
                    {props.companies.map((company, index) => (
                        <MenuItem key={index} value={company} >{(company.ticker === undefined) ? company : company.ticker}</MenuItem>
                    ))}
                </Select>
                <TextField required margin="dense" fullWidth variant="standard" id="trader-trade-quantity"
                           value={quantity}
                           label="Quantity"
                           onChange={(e) => {setQuantity(e.target.value);setAlert(null);}}
                           error={validateNumber(quantity, false, 8, 4) !== ""}
                           helperText={validateNumber(quantity, false, 8, 4)}
                />
                <TextField required margin="dense" fullWidth variant="standard" id="trader-trade-price"
                           value={price}
                           label="Price"
                           onChange={(e) => {setPrice(e.target.value);setAlert(null);}}
                           error={validateNumber(price, false, 10, 4) !== ""}
                           helperText={validateNumber(price, false, 10, 4) }
                />
                <TextField required margin="dense" fullWidth variant="standard" id="trader-trade-fees"
                           value={fees}
                           label="Fees"
                           onChange={(e) => {setFees(e.target.value);setAlert(null);}}
                           error={validateNumber(fees, false, 5, 2) !== ""}
                           helperText={validateNumber(fees, false, 5, 2) }
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
export default AddTradeDialog