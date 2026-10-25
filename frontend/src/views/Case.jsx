import React, {useState} from "react";
import {useNavigate, useParams} from "react-router-dom";
import {
    Autocomplete,
    Box,
    Button,
    Chip,
    Paper,
    Stack,
    TextField,
    Tooltip,
    Typography,
} from "@mui/material";
import DeleteIcon from "@mui/icons-material/DeleteOutline";
import axios from "axios";
import {useData} from "../service/BackendService";
import {Loader} from "./component/Loader";
import {SnackbarErrorAlert} from "./component/SnackbarErrorAlert";
import {backend} from "../properties";
import {formatDate, formatError} from "../service/FormattingService";

/**
 * One case: what it is called, the companies it covers, and - from the next step on - the days
 * written into it. Everything here saves as it is changed; there is no form to submit.
 */
export const Case = props => {
    const {caseId} = useParams()
    const navigate = useNavigate()
    const [refresh, setRefresh] = useState("")
    const {data, loaded, error} = useData(`/case/${caseId}${refresh ? "?refresh=" + refresh : ""}`)
    const [alert, setAlert] = useState(null)
    const [name, setName] = useState(null)

    const companies = props.companyLists?.all ?? []
    const value = data

    function save(changes) {
        return axios.put(`${backend}/case/${caseId}`, changes)
            .then(() => setRefresh(Date.now().toString()))
            .catch(reason => setAlert(formatError(reason)))
    }

    function removeCase() {
        axios.delete(`${backend}/case/${caseId}`)
            .then(() => navigate("/cases"))
            .catch(reason => setAlert(formatError(reason)))
    }

    return (
        <>
            {!loaded && <Loader error={error}/>}
            {loaded &&
                <Box sx={{width: {xs: "100%", sm: "700px"}, margin: "10px auto"}}>
                    <Paper elevation={1} sx={{padding: "10px 12px"}}>
                        <Stack direction="row" alignItems="center" spacing={1}>
                            <TextField
                                variant="standard"
                                fullWidth
                                inputProps={{"aria-label": "Case name"}}
                                value={name ?? value.name}
                                onChange={event => setName(event.target.value)}
                                onBlur={() => {
                                    if (name !== null && name.trim() && name.trim() !== value.name) {
                                        save({name: name.trim()})
                                    }
                                    setName(null)
                                }}
                            />
                            <Button
                                size="small"
                                variant="outlined"
                                onClick={() => save({closed: !value.closedAt})}
                            >
                                {value.closedAt ? "Reopen" : "Close"}
                            </Button>
                            <Tooltip title="Delete case">
                                <Button size="small" color="error" aria-label="Delete case" onClick={removeCase}>
                                    <DeleteIcon/>
                                </Button>
                            </Tooltip>
                        </Stack>

                        <Typography sx={{fontSize: 12, color: "text.secondary", marginTop: "2px"}}>
                            {`opened ${formatDate(value.createdAt)}`}
                            {value.closedAt ? `, closed ${formatDate(value.closedAt)}` : ""}
                        </Typography>

                        <Stack direction="row" spacing={1} sx={{marginTop: "10px", flexWrap: "wrap", gap: "4px"}}>
                            {value.companies.map(company => (
                                <Chip
                                    key={company.id}
                                    label={company.ticker}
                                    size="small"
                                    onDelete={() => save({
                                        companyIds: value.companies
                                            .filter(each => each.id !== company.id)
                                            .map(each => each.id),
                                    })}
                                />
                            ))}
                        </Stack>

                        <Autocomplete
                            size="small"
                            sx={{marginTop: "10px", maxWidth: "260px"}}
                            options={companies.filter(company =>
                                !value.companies.some(each => each.id === company.id))}
                            getOptionLabel={company => company.ticker}
                            value={null}
                            onChange={(event, company) => company && save({
                                companyIds: [...value.companies.map(each => each.id), company.id],
                            })}
                            renderInput={params => <TextField {...params} label="Add company"/>}
                        />
                    </Paper>
                </Box>
            }
            <SnackbarErrorAlert error={alert} open={alert !== null} onClose={() => setAlert(null)}/>
        </>
    )
}
