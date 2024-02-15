import {Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField} from "@mui/material";
import React, {useEffect, useState} from "react";
import {domain} from "../properties";
import axios from "axios";
import {handleError, validateNumber} from "../utils";


const AddRecordDialog = props => {
    const {companyId, open, handleClose} = props
    const [alert, setAlert] = useState(null)
    const [title, setTitle] = useState("")
    const [date, setDate] = useState("")
    const [price, setPrice] = useState("")

    useEffect(() => {
        if (open) {
            setTitle("")
            setDate("")
            setPrice("")
        }
        // eslint-disable-next-line
    }, [open])

    function createRecord() {
        const data = {companyId: companyId, title: title, date: date, price: price}
        axios.post(domain + "/record", data)
            .then((response) => {
                props.triggerRefresh()
                handleClose()
            }).catch((error) => {setAlert(handleError(error))})
    }

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            PaperProps={{component: 'form', onSubmit: (event) => {event.preventDefault();createRecord()},}}
        >
            <DialogTitle>Add Record</DialogTitle>
            <DialogContent>
                <TextField required margin="dense" fullWidth variant="standard" id="trader-record-date"
                           type="date"
                           value={date}
                           onChange={(e) => {setDate(e.target.value);setAlert(null);}}
                           error={date === ""}
                />
                <TextField required margin="dense" fullWidth variant="standard" id="trader-record-title"
                           value={title}
                           label="Title"
                           onChange={(e) => {setTitle(e.target.value);setAlert(null);}}
                           error={title === ""}
                />
                <TextField required margin="dense" fullWidth variant="standard" id="trader-record-price"
                           value={price}
                           label="Price"
                           onChange={(e) => {setPrice(e.target.value);setAlert(null);}}
                           error={validateNumber(price, false, 10, 4) !== ""}
                           helperText={validateNumber(price, false, 10, 4)}
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
export default AddRecordDialog