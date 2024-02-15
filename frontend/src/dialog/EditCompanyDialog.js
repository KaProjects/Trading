import React, {useEffect, useState} from "react";
import axios from "axios";
import {domain} from "../properties";
import {handleError} from "../utils";
import {
    Alert,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControlLabel,
    MenuItem,
    Select,
    Switch,
    TextField
} from "@mui/material";


function validateTicker(value) {
    if (typeof value != "string") return "not a string"
    if (!value) return "non empty"
    if (value.length > 5) return "max length 5"
    if (value.toUpperCase() !== value) return "only uppercase"
    return ""
}

const EditCompanyDialog = props => {
    const company = props.openEditCompany
    const handleClose = () => props.setOpenEditCompany(null)

    const [alert, setAlert] = useState(null)
    const [ticker, setTicker] = useState("")
    const [currency, setCurrency] = useState("")
    const [watching, setWatching] = useState(true)
    const [sector, setSector] = useState("")

    const [values, setValues] = useState({currencies: [], sectors: []})

    useEffect(() => {
        if (company) {
            axios.get(domain + "/company/values")
                .then((response) => {
                    setValues(response.data)
                    setAlert(null)
                    setTicker(company.id ? company.ticker : "")
                    setCurrency(company.id ? company.currency : "")
                    setWatching(company.id ? company.watching : true)
                    setSector((company.id && company.sector) ? company.sector : "")
                }).catch((error) => {setAlert(handleError(error))})
        }
        // eslint-disable-next-line
    }, [company])

    function createEditCompany() {
        const companyData = {ticker: ticker, currency: currency, watching: watching}
        if (sector) companyData.sector = sector
        if (company.id){
            companyData.id = company.id
            axios.put(domain + "/company", companyData)
                .then((response) => {
                    props.triggerRefresh()
                    handleClose()
                }).catch((error) => {setAlert(handleError(error))})
        } else {
            axios.post(domain + "/company", companyData)
                .then((response) => {
                    props.triggerRefresh()
                    handleClose()
                }).catch((error) => {setAlert(handleError(error))})
        }
    }

    return (
        <Dialog
            open={!!company}
            onClose={handleClose}
            PaperProps={{component: 'form', onSubmit: (event) => {event.preventDefault();createEditCompany()},}}
        >
            <DialogTitle>{(company && company.id) ? "Edit " + company.ticker : "Add Company"}</DialogTitle>
            <DialogContent>
                {company && !company.id &&
                    <TextField required margin="dense" fullWidth variant="standard" id="company-ticker"
                               value={ticker}
                               label="Ticker"
                               onChange={(e) => {setTicker(e.target.value);setAlert(null);}}
                               error={validateTicker(ticker) !== ""}
                               helperText={validateTicker(ticker)}
                    />
                }
                <Select required margin="dense" fullWidth variant="standard" displayEmpty
                        value={currency}
                        error={currency === ""}
                        onChange={event => {setCurrency(event.target.value);setAlert(null);}}
                        sx={{marginTop: "20px"}}
                >
                    <MenuItem value=""></MenuItem>
                    {values.currencies.map((currency, index) => (
                        <MenuItem key={index} value={currency} >{currency}</MenuItem>
                    ))}
                </Select>
                <Select margin="dense" fullWidth variant="standard" displayEmpty
                        value={sector}
                        onChange={event => {setSector(event.target.value);setAlert(null);}}
                        sx={{marginTop: "20px"}}
                >
                    <MenuItem value=""></MenuItem>
                    {values.sectors.map((sector, index) => (
                        <MenuItem key={index} value={sector} >{sector}</MenuItem>
                    ))}
                </Select>
                <FormControlLabel
                    sx={{marginTop: "10px", marginLeft: "5px"}}
                    control={<Switch color="primary"
                                     checked={watching}
                                     onChange={(event) => setWatching(event.target.checked)}
                                     inputProps={{ 'aria-label': 'controlled' }}/>}
                    label="Watching"
                    labelPlacement="start"
                />
            </DialogContent>
            {alert &&
                <Alert severity="error" variant="filled">
                    {alert}
                </Alert>
            }
            <DialogActions>
                <Button onClick={handleClose}>Cancel</Button>
                <Button type="submit">{(company && company.id) ? "Edit" : "Create"}</Button>
            </DialogActions>
        </Dialog>
    )
}
export default EditCompanyDialog