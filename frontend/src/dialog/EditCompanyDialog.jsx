import React, {useEffect, useState} from "react";
import axios from "axios";
import {backend} from "../properties";
import {
    Alert,
    AlertTitle,
    Box,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    FormHelperText,
    InputLabel,
    MenuItem,
    Select
} from "@mui/material";
import {validateTicker} from "../service/ValidationService";
import {formatError} from "../service/FormattingService";
import {DialogTextField} from "./component/DialogTextField";


export const EditCompanyDialog = props => {
    const company = props.openEditCompany
    const handleClose = () => props.setOpenEditCompany(null)

    const [alert, setAlert] = useState(null)
    const [ticker, setTicker] = useState("")
    const [currency, setCurrency] = useState("")
    const [sector, setSector] = useState("")
    const [alphaVantageTicker, setAlphaVantageTicker] = useState("")
    const [name, setName] = useState("")
    const [description, setDescription] = useState("")
    const [logoUrl, setLogoUrl] = useState("")
    const [website, setWebsite] = useState("")
    const [profileLoading, setProfileLoading] = useState(false)
    const [alphaVantageTickers, setAlphaVantageTickers] = useState([])
    const [tickerSearchLoading, setTickerSearchLoading] = useState(false)
    const [tickerSearchCompleted, setTickerSearchCompleted] = useState(false)

    useEffect(() => {
        if (company) {
            setAlert(null)
            setTicker(company.id ? company.ticker : "")
            setCurrency(company.id ? company.currency : "")
            setName(company.id ? company.name ?? "" : "")
            setDescription(company.id ? company.description ?? "" : "")
            setLogoUrl(company.id ? company.logoUrl ?? "" : "")
            setWebsite(company.id ? company.website ?? "" : "")
            setProfileLoading(false)
            const selectedAlphaVantageTicker = company.id ? company.alphaVantageTicker ?? "" : ""
            setAlphaVantageTicker(selectedAlphaVantageTicker)
            setAlphaVantageTickers(selectedAlphaVantageTicker
                ? [{symbol: selectedAlphaVantageTicker}]
                : [])
            setTickerSearchLoading(false)
            setTickerSearchCompleted(selectedAlphaVantageTicker !== "")
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
        const companyData = {
            ticker: ticker,
            currency: currency,
            alphaVantageTicker: currency === "$" ? null : alphaVantageTicker || null,
            name: name.trim() || null,
            description: description.trim() || null,
            logoUrl: logoUrl.trim() || null,
            website: website.trim() || null,
        }
        if (sector) companyData.sector = sector.key
        if (company.id){
            companyData.id = company.id
            axios.put(backend + "/company", companyData)
                .then((response) => {
                    props.triggerRefresh()
                    handleClose()
                }).catch((error) => {setAlert(formatError(error))})
        } else {
            axios.post(backend + "/company", companyData)
                .then((response) => {
                    props.triggerRefresh()
                    handleClose()
                }).catch((error) => {setAlert(formatError(error))})
        }
    }

    function resetAlphaVantageTicker() {
        setAlphaVantageTicker("")
        setAlphaVantageTickers([])
        setTickerSearchCompleted(false)
    }

    function resetProfile() {
        setName("")
        setDescription("")
        setLogoUrl("")
        setWebsite("")
    }

    function loadPolygonProfile() {
        setProfileLoading(true)
        setAlert(null)
        axios.get(backend + "/company/polygon/profile", {
            params: {ticker},
        }).then(response => {
            const profile = response.data ?? {}
            setName(profile.name ?? "")
            setDescription(profile.description ?? "")
            setLogoUrl(profile.logoUrl ?? "")
            setWebsite(profile.website ?? "")
        }).catch(error => {
            setAlert(formatError(error))
        }).finally(() => {
            setProfileLoading(false)
        })
    }

    function findAlphaVantageTickers() {
        setTickerSearchLoading(true)
        setTickerSearchCompleted(false)
        setAlert(null)
        axios.get(backend + "/company/alpha-vantage/tickers", {
            params: {ticker, currency},
        }).then(response => {
            const candidates = response.data ?? []
            setAlphaVantageTickers(candidates)
            setTickerSearchCompleted(true)
            if (!candidates.some(candidate => candidate.symbol === alphaVantageTicker)) {
                setAlphaVantageTicker("")
            }
        }).catch(error => {
            setAlert(formatError(error))
        }).finally(() => {
            setTickerSearchLoading(false)
        })
    }

    const alphaVantageEnabled = currency !== "" && currency !== "$"
    const tickerSearchDisabled = !alphaVantageEnabled
        || validateTicker(ticker) !== ""
        || tickerSearchLoading
    const profileLoadingDisabled = currency === ""
        || validateTicker(ticker) !== ""
        || profileLoading

    return (
        <Dialog
            open={!!company}
            onClose={handleClose}
            slotProps={{paper: {component: 'form', onSubmit: (event) => {event.preventDefault();createEditCompany()},}}}
        >
            <DialogTitle>{(company && company.id) ? "Edit " + company.ticker : "Add Company"}</DialogTitle>
            <DialogContent>
                {company && !company.id &&
                    <DialogTextField
                        id="company-ticker"
                        value={ticker}
                        label="Ticker"
                        onChange={(e) => {
                            setTicker(e.target.value)
                            resetAlphaVantageTicker()
                            resetProfile()
                            setAlert(null)
                        }}
                        validate={() => validateTicker(ticker)}
                    />
                }
                <Select required margin="dense" fullWidth variant="standard" displayEmpty
                        value={currency}
                        error={currency === ""}
                        onChange={event => {
                            setCurrency(event.target.value)
                            resetAlphaVantageTicker()
                            setAlert(null)
                        }}
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
                <Box sx={{
                    marginTop: "20px",
                    padding: "8px 12px 12px",
                    border: "1px solid",
                    borderColor: "divider",
                    borderRadius: "4px",
                }}>
                    <Button
                        type="button"
                        variant="outlined"
                        disabled={profileLoadingDisabled}
                        onClick={loadPolygonProfile}
                        startIcon={profileLoading ? <CircularProgress size={14}/> : null}
                    >
                        Try Load Company Data
                    </Button>
                    <DialogTextField
                        id="company-name"
                        value={name}
                        label="Name"
                        required={false}
                        onChange={event => {setName(event.target.value);setAlert(null)}}
                    />
                    <DialogTextField
                        id="company-description"
                        value={description}
                        label="Description"
                        multiline
                        minRows={3}
                        required={false}
                        onChange={event => {setDescription(event.target.value);setAlert(null)}}
                    />
                    <DialogTextField
                        id="company-website"
                        value={website}
                        label="Website"
                        type="url"
                        required={false}
                        onChange={event => {setWebsite(event.target.value);setAlert(null)}}
                    />
                    <DialogTextField
                        id="company-logo-url"
                        value={logoUrl}
                        label="Logo URL"
                        type="url"
                        required={false}
                        onChange={event => {setLogoUrl(event.target.value);setAlert(null)}}
                    />
                </Box>
                {alphaVantageEnabled && !tickerSearchCompleted &&
                    <Box sx={{marginTop: "20px"}}>
                        <Button
                            type="button"
                            variant="outlined"
                            disabled={tickerSearchDisabled}
                            onClick={findAlphaVantageTickers}
                            startIcon={tickerSearchLoading ? <CircularProgress size={14}/> : null}
                        >
                            Find Alpha Vantage tickers
                        </Button>
                    </Box>
                }
                {alphaVantageEnabled && tickerSearchCompleted &&
                    <FormControl fullWidth variant="standard" sx={{marginTop: "20px"}}>
                        <InputLabel id="alpha-vantage-ticker-label">Alpha Vantage ticker</InputLabel>
                        <Select
                            labelId="alpha-vantage-ticker-label"
                            value={alphaVantageTicker}
                            onChange={event => {
                                setAlphaVantageTicker(event.target.value)
                                setAlert(null)
                            }}
                        >
                            <MenuItem value="">None</MenuItem>
                            {alphaVantageTickers.map(candidate => (
                                <MenuItem key={candidate.symbol} value={candidate.symbol}>
                                    {candidate.symbol}
                                    {candidate.region ? ` — ${candidate.name} (${candidate.region})` : ""}
                                </MenuItem>
                            ))}
                        </Select>
                        {alphaVantageTickers.length === 0 &&
                            <FormHelperText>No matching tickers found.</FormHelperText>
                        }
                    </FormControl>
                }
            </DialogContent>
            {alert &&
                <Alert severity="error" variant="filled">
                    <AlertTitle>{alert.title}</AlertTitle>{alert.message}
                </Alert>
            }
            <DialogActions>
                <Button onClick={handleClose}>Cancel</Button>
                <Button type="submit">{(company && company.id) ? "Edit" : "Create"}</Button>
            </DialogActions>
        </Dialog>
    )
}
