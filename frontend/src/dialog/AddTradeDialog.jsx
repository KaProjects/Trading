import {
    Alert,
    AlertTitle,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    InputLabel,
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
import {DialogCompanySelect} from "./component/DialogCompanySelect";


export const AddTradeDialog = props => {
    const open = props.openAddTrade
    const handleClose = () => props.setOpenAddTrade(false)

    const [alert, setAlert] = useState(null)
    const [date, setDate] = useState("")
    const [price, setPrice] = useState("")
    const [quantity, setQuantity] = useState("")
    const [fees, setFees] = useState("")
    const [company, setCompany] = useState("")
    const [portfolio, setPortfolio] = useState("")

    useEffect(() => {
        if (open) {
            setAlert(null)
            setDate("")
            setPrice("")
            setQuantity("")
            setFees("")
            setCompany(props.companySelectorValue)
            setPortfolio("")
        }
        // eslint-disable-next-line
    }, [open])

    function createTrade() {
        const tradeData = {
            companyId: company.id,
            date: date,
            price: price,
            quantity: quantity,
            fees: fees,
            portfolio: portfolio || null,
        }
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
            slotProps={{paper: {component: 'form', onSubmit: (event) => {event.preventDefault();createTrade()},}}}
        >
            <DialogTitle>Add Trade</DialogTitle>
            <DialogContent>
                <DialogDatePicker
                    id="trader-trade-date"
                    value={date}
                    onChange={(e) => {setDate(e.target.value);setAlert(null);}}
                />
                <DialogCompanySelect
                    key={`add-trade-company-${open}`}
                    id="trader-trade-company"
                    companyLists={props.companyLists}
                    defaultCompanyList="recent"
                    value={company}
                    onChange={value => {setCompany(value);setAlert(null);}}
                />
                <FormControl fullWidth variant="standard" sx={{marginTop: "20px"}}>
                    <InputLabel id="trader-trade-portfolio-label">Portfolio</InputLabel>
                    <Select
                        labelId="trader-trade-portfolio-label"
                        value={portfolio}
                        onChange={event => {setPortfolio(event.target.value);setAlert(null);}}
                    >
                        <MenuItem value=""></MenuItem>
                        {(props.portfolios ?? []).map(item => (
                            <MenuItem key={item.key} value={item.key}>{item.name}</MenuItem>
                        ))}
                    </Select>
                </FormControl>
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
