import React, {useEffect, useState} from "react";
import axios from "axios";
import {backend} from "../properties";
import {handleError, validateShares, validateTicker} from "../utils";
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


const EditCompanyDialog = props => {
    const company = props.openEditCompany
    const handleClose = () => props.setOpenEditCompany(null)

    const [alert, setAlert] = useState(null)
    const [ticker, setTicker] = useState("")
    const [currency, setCurrency] = useState("")
    const [watching, setWatching] = useState(true)
    const [sector, setSector] = useState("")

    useEffect(() => {
        if (company) {
            setAlert(null)
            setTicker(company.id ? company.ticker : "")
            setCurrency(company.id ? company.currency : "")
            setWatching(company.id ? company.watching : true)
            if ((company.id && company.sector)){
                props.sectors.forEach(sector => {
                    if (sector.key === company.sector.key) setSector(sector)
                })
            } else {
                setSector("")
            }
        }
        // eslint-disable-next-line
    }, [company])

    function createEditCompany() {
        const companyData = {ticker: ticker, currency: currency, watching: watching}
        if (sector) companyData.sector = sector
        if (company.id){
            companyData.id = company.id
            axios.put(backend + "/company", companyData)
                .then((response) => {
                    props.triggerRefresh()
                    handleClose()
                }).catch((error) => {setAlert(handleError(error))})
        } else {
            axios.post(backend + "/company", companyData)
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
                    {props.currencies.map((currency, index) => (
                        <MenuItem key={index} value={currency} >{currency}</MenuItem>
                    ))}
                </Select>
                <Select margin="dense" fullWidth variant="standard" displayEmpty
                        value={sector}
                        onChange={event => {setSector(event.target.value);setAlert(null);}}
                        sx={{marginTop: "20px"}}
                >
                    <MenuItem value=""></MenuItem>
                    {props.sectors.map((sector, index) => (
                        <MenuItem key={index} value={sector} >{sector.name}</MenuItem>
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