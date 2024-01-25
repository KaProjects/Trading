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
import React, {useState} from "react";
import {properties} from "../properties";
import axios from "axios";
import {validateNumber} from "../utils";


const AddTradeDialog = props => {

    const [alert, setAlert] = useState(null);
    const [date, setDate] = useState("");
    const [price, setPrice] = useState("");
    const [quantity, setQuantity] = useState("");
    const [fees, setFees] = useState("");
    const [company, setCompany] = useState("");

    function handleClose(trade){
        setDate("")
        setPrice("")
        setQuantity("")
        setFees("")
        setCompany("")
        props.handleClose(trade)
    }

    function createTrade() {
        const dateSplit = date.split('-');
        const dtoDate = dateSplit[2] + "." + dateSplit[1] + "." + dateSplit[0]
        const tradeData = {companyId: company.id, date: dtoDate, price: price, quantity: quantity, fees: fees}
        const tradeUrl = properties.protocol + "://" + properties.host + ":" + properties.port + "/trade"
        axios.post(tradeUrl, tradeData)
            .then((response) => {
                const trade = response.data
                const title = "bought " + quantity + "@" + price + company.currency
                const recordData = {companyId: company.id, title: title, date: dtoDate, price: price}
                const recordUrl = properties.protocol + "://" + properties.host + ":" + properties.port + "/record"
                axios.post(recordUrl, recordData)
                    .then((response) => {
                        handleClose(trade)
                    }).catch((error) => {
                        console.error(error)
                        setAlert(error.response.data)
                })
            }).catch((error) => {
                console.error(error)
                setAlert(error.response.data)
        })
    }

    return (
        <Dialog
            open={props.open}
            onClose={() => handleClose()}
            PaperProps={{component: 'form', onSubmit: (event) => {event.preventDefault();createTrade()},}}
        >
            <DialogTitle>Add Trade</DialogTitle>
            <DialogContent>
                <TextField required margin="dense" fullWidth variant="standard"
                           type="date"
                           value={date}
                           onChange={(e) => setDate(e.target.value)}
                           error={date === ""}
                />
                <Select required margin="dense" fullWidth variant="standard" displayEmpty
                        value={company}
                        error={company === ""}
                        onChange={event => setCompany(event.target.value)}
                        sx={{marginTop: "20px"}}
                >
                    <MenuItem value=""></MenuItem>
                    {props.companies.map((company, index) => (
                        <MenuItem key={index} value={company} >{(company.ticker === undefined) ? company : company.ticker}</MenuItem>
                    ))}
                </Select>
                <TextField required margin="dense" fullWidth variant="standard"
                           value={quantity}
                           label="Quantity"
                           onChange={(e) => setQuantity(e.target.value)}
                           error={validateNumber(quantity, false, 8, 4) !== ""}
                           helperText={validateNumber(quantity, false, 8, 4)}
                />
                <TextField required margin="dense" fullWidth variant="standard"
                           value={price}
                           label="Price"
                           onChange={(e) => setPrice(e.target.value)}
                           error={validateNumber(price, false, 10, 4) !== ""}
                           helperText={validateNumber(price, false, 10, 4) }
                />
                <TextField required margin="dense" fullWidth variant="standard"
                           value={fees}
                           label="Fees"
                           onChange={(e) => setFees(e.target.value)}
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
                <Button onClick={() => handleClose()}>Cancel</Button>
                <Button type="submit">Create</Button>
            </DialogActions>
        </Dialog>
    )
}
export default AddTradeDialog;