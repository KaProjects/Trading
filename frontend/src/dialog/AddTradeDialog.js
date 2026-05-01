import {
    Alert,
    AlertTitle,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    MenuItem,
    Select
} from "@mui/material";
import React, {useEffect, useState} from "react";
import {backend} from "../properties";
import axios from "axios";
import {validateNumber} from "../service/ValidationService";
import {formatError} from "../service/FormattingService";
import {DialogTextField} from "./component/DialogTextField";
import {DialogDatePicker} from "./component/DialogDatePicker";


const AddTradeDialog = props => {
    const open = props.openAddTrade
    const handleClose = () => props.setOpenAddTrade(false)

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
        axios.post(backend + "/trade", tradeData)
            .then((response) => {
                props.triggerRefresh()
                handleClose()
            }).catch((error) => {setAlert(formatError(error))})
    }

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            PaperProps={{component: 'form', onSubmit: (event) => {event.preventDefault();createTrade()},}}
        >
            <DialogTitle>Add Trade</DialogTitle>
            <DialogContent>
                <DialogDatePicker
                    id="trader-trade-date"
                    value={date}
                    onChange={(e) => {setDate(e.target.value);setAlert(null);}}
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
                <DialogTextField
                    id="trader-trade-quantity"
                    value={quantity}
                    label="Quantity"
                    onChange={(e) => {setQuantity(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(quantity, false, 8, 4, false)}
                />
                <DialogTextField
                    id="trader-trade-price"
                    value={price}
                    label="Price"
                    onChange={(e) => {setPrice(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(price, false, 10, 4, false)}
                />
                <DialogTextField
                    id="trader-trade-fees"
                    value={fees}
                    label="Fees"
                    onChange={(e) => {setFees(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(fees, false, 5, 2, false)}
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
export default AddTradeDialog