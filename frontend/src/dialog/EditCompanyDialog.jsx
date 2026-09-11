import React, {useEffect, useState} from "react";
import axios from "axios";
import {backend} from "../properties";
import {
    Alert,
    AlertTitle,
    Autocomplete,
    Box,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    FormHelperText,
    IconButton,
    InputLabel,
    MenuItem,
    Select,
    Tab,
    Tabs,
    TextField,
} from "@mui/material";
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import {ReactComponent as DeleteIcon} from "../assets/icons/delete.svg";
import {validateTicker} from "../service/ValidationService";
import {formatError} from "../service/FormattingService";
import {COMPANY_LIST_TITLES, getCompanyListKeys} from "../service/CompanyListService";
import {DialogTextField} from "./component/DialogTextField";

const TAB = {general: 0, data: 1, tags: 2}
const MAX_TAG_LENGTH = 30

function tagsForCompany(companyLists, companyId) {
    return Object.entries(companyLists ?? {})
        .filter(([key]) => !COMPANY_LIST_TITLES[key])
        .filter(([, companies]) => (companies ?? []).some(entry => String(entry.id) === String(companyId)))
        .map(([key]) => key)
}

export const EditCompanyDialog = props => {
    const company = props.openEditCompany
    const handleClose = () => props.setOpenEditCompany(null)

    const [tab, setTab] = useState(TAB.general)
    const [alert, setAlert] = useState(null)
    const [ticker, setTicker] = useState("")
    const [currency, setCurrency] = useState("")
    const [sector, setSector] = useState("")
    const [exchange, setExchange] = useState("")
    const [alphaVantageTicker, setAlphaVantageTicker] = useState("")
    const [name, setName] = useState("")
    const [description, setDescription] = useState("")
    const [website, setWebsite] = useState("")
    const [profileLoading, setProfileLoading] = useState(false)
    const [alphaVantageTickers, setAlphaVantageTickers] = useState([])
    const [tickerSearchLoading, setTickerSearchLoading] = useState(false)
    const [tickerSearchCompleted, setTickerSearchCompleted] = useState(false)
    const [tags, setTags] = useState([])
    const [newTag, setNewTag] = useState("")
    const [submitting, setSubmitting] = useState(false)

    useEffect(() => {
        if (company) {
            setTab(TAB.general)
            setAlert(null)
            setSubmitting(false)
            setTicker(company.id ? company.ticker : "")
            setCurrency(company.id ? company.currency : "")
            setName(company.id ? company.name ?? "" : "")
            setDescription(company.id ? company.description ?? "" : "")
            setWebsite(company.id ? company.website ?? "" : "")
            setProfileLoading(false)
            const exchangeKey = company.id
                ? (typeof company.exchange === "string" ? company.exchange : company.exchange?.key)
                : ""
            const selectedExchange = props.exchanges.find(value => value.key === exchangeKey) ?? ""
            setExchange(selectedExchange)
            const selectedAlphaVantageTicker = company.id ? company.alphaVantageTicker ?? "" : ""
            setAlphaVantageTicker(selectedAlphaVantageTicker)
            setAlphaVantageTickers(selectedAlphaVantageTicker
                ? [{symbol: selectedAlphaVantageTicker}]
                : [])
            setTickerSearchLoading(false)
            setTickerSearchCompleted(selectedAlphaVantageTicker !== "")
            setTags(company.id ? tagsForCompany(props.companyLists, company.id) : [])
            setNewTag("")
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
        if (submitting) return
        setSubmitting(true)
        const companyData = {
            ticker: ticker,
            currency: currency,
            alphaVantageTicker: currency === "$" ? null : alphaVantageTicker || null,
            exchange: exchange ? exchange.key : null,
            name: name.trim() || null,
            description: description.trim() || null,
            website: website.trim() || null,
        }
        if (sector) companyData.sector = sector.key
        if (company.id){
            companyData.id = company.id
            axios.put(backend + "/company", companyData)
                .then((response) => {
                    props.triggerRefresh()
                    handleClose()
                })
                .catch((error) => {setAlert(formatError(error))})
                .finally(() => setSubmitting(false))
        } else {
            axios.post(backend + "/company", companyData)
                .then((response) => {
                    props.triggerRefresh()
                    handleClose()
                })
                .catch((error) => {setAlert(formatError(error))})
                .finally(() => setSubmitting(false))
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

    function addTag() {
        if (!newTagValid || submitting) return
        setSubmitting(true)

        axios.post(backend + "/company/tag", {
            companyId: company.id,
            value: newTag,
        }).then(() => {
            setTags(previous => [...previous, newTag])
            setNewTag("")
            setAlert(null)
            props.triggerRefresh()
        })
            .catch(error => setAlert(formatError(error)))
            .finally(() => setSubmitting(false))
    }

    function deleteTag(tag) {
        if (submitting) return
        setSubmitting(true)
        axios.delete(backend + "/company/" + company.id + "/tag", {
            params: {value: tag},
        }).then(() => {
            setTags(previous => previous.filter(value => value !== tag))
            props.triggerRefresh()
        })
            .catch(error => setAlert(formatError(error)))
            .finally(() => setSubmitting(false))
    }

    function tickerError() {
        const formatError = validateTicker(ticker)
        if (formatError) return formatError

        const normalizedTicker = ticker.toLocaleUpperCase()
        return (props.companyLists?.all ?? []).some(item => item.ticker.toLocaleUpperCase() === normalizedTicker)
            ? "already exists"
            : ""
    }

    const alphaVantageEnabled = currency !== "" && currency !== "$"
    const tickerSearchDisabled = !alphaVantageEnabled
        || validateTicker(ticker) !== ""
        || tickerSearchLoading
    const profileLoadingDisabled = currency === ""
        || validateTicker(ticker) !== ""
        || profileLoading
    const normalizedNewTag = newTag.toLocaleLowerCase()
    const newTagContainsWhitespace = /\s/.test(newTag)
    const newTagAlreadyAssigned = newTag.length > 0
        && tags.some(tag => tag.toLocaleLowerCase() === normalizedNewTag)
    const newTagReserved = newTag.length > 0
        && Object.prototype.hasOwnProperty.call(COMPANY_LIST_TITLES, normalizedNewTag)
    const newTagValid = newTag.length > 0
        && newTag.length <= MAX_TAG_LENGTH
        && !newTagContainsWhitespace
        && !newTagAlreadyAssigned
        && !newTagReserved
    const tagSuggestions = getCompanyListKeys(props.companyLists ?? {}).filter(key => {
        const normalizedKey = key.toLocaleLowerCase()
        return !Object.prototype.hasOwnProperty.call(COMPANY_LIST_TITLES, normalizedKey)
            && !tags.some(tag => tag.toLocaleLowerCase() === normalizedKey)
    })
    return (
        <Dialog
            open={!!company}
            onClose={handleClose}
            fullWidth
            maxWidth="xs"
            slotProps={{paper: {component: 'form', onSubmit: (event) => {event.preventDefault();createEditCompany()},}}}
        >
            <DialogTitle sx={{paddingBottom: 1}}>{(company && company.id) ? "Edit " + company.ticker : "Add Company"}</DialogTitle>
            <Tabs
                value={tab}
                onChange={(event, value) => setTab(value)}
                sx={{paddingX: 3, borderBottom: 1, borderColor: "divider"}}
            >
                <Tab label="General"/>
                <Tab label="Data"/>
                {company && company.id &&
                    <Tab label="Tags"/>
                }
            </Tabs>
            <DialogContent sx={{minHeight: {xs: "auto", sm: "360px"}}}>
                {tab === TAB.general &&
                    <>
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
                                validate={tickerError}
                            />
                        }
                        <FormControl required fullWidth variant="standard" error={currency === ""} sx={{marginTop: "20px"}}>
                            <InputLabel id="company-currency-label">Currency</InputLabel>
                            <Select
                                labelId="company-currency-label"
                                value={currency}
                                onChange={event => {
                                    setCurrency(event.target.value)
                                    resetAlphaVantageTicker()
                                    setAlert(null)
                                }}
                            >
                                <MenuItem value=""></MenuItem>
                                {props.currencies.map((currency, index) => (
                                    <MenuItem key={index} value={currency} >{currency}</MenuItem>
                                ))}
                            </Select>
                        </FormControl>
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
                        <FormControl fullWidth variant="standard" sx={{marginTop: "20px"}}>
                            <InputLabel id="company-sector-label">Sector</InputLabel>
                            <Select
                                labelId="company-sector-label"
                                value={sector}
                                onChange={event => {setSector(event.target.value);setAlert(null);}}
                            >
                                <MenuItem value="">None</MenuItem>
                                {props.sectors.map((sector, index) => (
                                    <MenuItem key={index} value={sector} >{sector.name}</MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                        <FormControl fullWidth variant="standard" sx={{marginTop: "20px"}}>
                            <InputLabel id="company-exchange-label">Exchange</InputLabel>
                            <Select
                                labelId="company-exchange-label"
                                value={exchange}
                                onChange={event => {
                                    setExchange(event.target.value)
                                    setAlert(null)
                                }}
                            >
                                <MenuItem value="">None</MenuItem>
                                {props.exchanges.map(value => (
                                    <MenuItem key={value.key} value={value}>{value.name}</MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    </>
                }
                {tab === TAB.data &&
                    <>
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
                        <Box sx={{marginTop: "20px"}}>
                            <Button
                                type="button"
                                variant="outlined"
                                disabled={profileLoadingDisabled}
                                onClick={loadPolygonProfile}
                                startIcon={profileLoading ? <CircularProgress size={14}/> : null}
                            >
                                Try Load Company Data
                            </Button>
                        </Box>
                    </>
                }
                {tab === TAB.tags &&
                    <>
                        <Box sx={{display: "flex", flexWrap: "wrap", gap: 1}}>
                            {tags.length === 0 &&
                                <Box sx={{color: "text.secondary", fontSize: 14}}>No tags yet.</Box>
                            }
                            {tags.map(tag => (
                                <Box
                                    key={tag}
                                    component="span"
                                    sx={{
                                        display: "inline-flex",
                                        alignItems: "center",
                                        border: "1px solid",
                                        borderColor: "divider",
                                        borderRadius: "16px",
                                        paddingLeft: "10px",
                                        fontSize: 14,
                                    }}
                                >
                                    #{tag}
                                    <IconButton
                                        aria-label={`Remove tag ${tag}`}
                                        size="small"
                                        disabled={submitting}
                                        onClick={() => deleteTag(tag)}
                                        sx={{marginLeft: "2px", "& svg": {width: 15, height: 15, display: "block"}}}
                                    >
                                        <DeleteIcon/>
                                    </IconButton>
                                </Box>
                            ))}
                        </Box>
                        <Box sx={{marginTop: "20px", display: "flex", alignItems: "flex-end", gap: 1}}>
                            <Autocomplete
                                freeSolo
                                fullWidth
                                options={tagSuggestions}
                                inputValue={newTag}
                                onInputChange={(event, value) => {
                                    setNewTag(value)
                                    setAlert(null)
                                }}
                                renderInput={params => (
                                    <TextField
                                        {...params}
                                        fullWidth
                                        margin="dense"
                                        variant="standard"
                                        label="Tag"
                                        error={newTag.length > MAX_TAG_LENGTH
                                            || newTagContainsWhitespace
                                            || newTagAlreadyAssigned
                                            || newTagReserved}
                                        helperText={newTag.length > MAX_TAG_LENGTH
                                            ? `Maximum ${MAX_TAG_LENGTH} characters`
                                            : newTagContainsWhitespace ? "Tag must not contain spaces or tabs"
                                                : newTagAlreadyAssigned ? "Tag is already assigned to this company"
                                                    : newTagReserved ? "Tag name is reserved" : ""}
                                        slotProps={{htmlInput: {...params.inputProps, maxLength: MAX_TAG_LENGTH + 1}}}
                                        onKeyDown={event => {
                                            if (event.key !== "Enter") return
                                            event.preventDefault()
                                            addTag()
                                        }}
                                    />
                                )}
                            />
                            <IconButton
                                aria-label="Add tag"
                                disabled={newTag.trim() === "" || submitting}
                                onClick={addTag}
                                sx={{marginBottom: "2px"}}
                            >
                                <ControlPointIcon sx={{color: newTag.trim() === "" ? undefined : "lightgreen"}}/>
                            </IconButton>
                        </Box>
                    </>
                }
            </DialogContent>
            {alert &&
                <Alert severity="error" variant="filled">
                    <AlertTitle>{alert.title}</AlertTitle>{alert.message}
                </Alert>
            }
            <DialogActions>
                <Button onClick={handleClose} disabled={submitting}>Cancel</Button>
                <Button type="submit" disabled={submitting}>{(company && company.id) ? "Edit" : "Create"}</Button>
            </DialogActions>
        </Dialog>
    )
}
