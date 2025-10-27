import {Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField} from "@mui/material";
import React, {useEffect, useState} from "react";
import {backend} from "../properties";
import axios from "axios";
import {handleError} from "../utils";


const AddPeriodDialog = props => {
    const {companyId, open, handleClose} = props
    const [alert, setAlert] = useState(null)
    const [name, setName] = useState("")
    const [endingMonth, setEndingMonth] = useState("")

    useEffect(() => {
        if (open) {
            setName("")
            setEndingMonth("")
        }
        // eslint-disable-next-line
    }, [open])

    function createPeriod() {
        const data = {companyId: companyId, name: name, endingMonth: endingMonth}
        axios.post(backend + "/period", data)
            .then((response) => {
                props.triggerRefresh()
                handleClose()
            }).catch((error) => {setAlert(handleError(error))})
    }

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            PaperProps={{component: 'form', onSubmit: (event) => {event.preventDefault();createPeriod()},}}
        >
            <DialogTitle>Add Period</DialogTitle>
            <DialogContent>
                <TextField required margin="dense" fullWidth variant="standard" id="trader-period-name"
                           value={name}
                           label="Name"
                           onChange={(e) => {setName(e.target.value);setAlert(null);}}
                           error={name.length !== 4}
                           helperText={name.length !== 4 ? "exactly 4 symbols, e.g. 25FY, 25Q1, ..." : ""}
                />
                <TextField required margin="dense" fullWidth variant="standard" id="trader-period-end-month"
                           type="month"
                           value={endingMonth}
                           label="Ending Month"
                           onChange={(e) => {setEndingMonth(e.target.value);setAlert(null);}}
                           error={endingMonth === ""}
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
export default AddPeriodDialog