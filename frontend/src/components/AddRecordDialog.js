import {Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField} from "@mui/material";
import React, {useState} from "react";
import {properties} from "../properties";
import axios from "axios";


const AddRecordDialog = props => {
    const {companyId} = props
    const [alert, setAlert] = useState(null);
    const [title, setTitle] = useState("");
    const [date, setDate] = useState("");
    const [price, setPrice] = useState("");

    function createRecord() {
        const dateSplit = date.split('-');
        const dtoDate = dateSplit[2] + "." + dateSplit[1] + "." + dateSplit[0]
        const data = {companyId: companyId, title: title, date: dtoDate, price: price}
        const url = properties.protocol + "://" + properties.host + ":" + properties.port + "/record"
        axios.post(url, data)
            .then((response) => {
                handleClose(response.data)
            }).catch((error) => {
                console.error(error)
                setAlert(error.response.data)
        })
    }

    function handleClose(record){
        setTitle("")
        setDate("")
        setPrice("")
        props.handleClose(record)
    }

    return (
        <Dialog
            open={props.open}
            onClose={() => handleClose()}
            PaperProps={{component: 'form', onSubmit: (event) => {event.preventDefault();createRecord()},}}
        >
            <DialogTitle>Add Record</DialogTitle>
            <DialogContent>
                <TextField required margin="dense" fullWidth variant="standard"
                           type="date"
                           value={date}
                           onChange={(e) => setDate(e.target.value)}
                           error={date === ""}
                />
                <TextField required margin="dense" fullWidth variant="standard"
                           value={title}
                           label="Title"
                           onChange={(e) => setTitle(e.target.value)}
                           error={title === ""}
                />
                <TextField required margin="dense" fullWidth variant="standard"
                           value={price}
                           label="Price"
                           onChange={(e) => setPrice(e.target.value)}
                           error={props.validatePrice(price) !== ""}
                           helperText={props.validatePrice(price)}
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
export default AddRecordDialog;