import React, {useState} from "react";
import {useNavigate} from "react-router-dom";
import {Box, Button, Chip, Paper, Stack, TextField, Typography} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import axios from "axios";
import {useData} from "../service/BackendService";
import {Loader} from "./component/Loader";
import {SnackbarErrorAlert} from "./component/SnackbarErrorAlert";
import {backend} from "../properties";
import {formatDate, formatError} from "../service/FormattingService";

const CARD = {
    padding: "10px 12px",
    cursor: "pointer",
    "&:hover": {backgroundColor: "action.hover"},
};

const CaseCard = ({value, onOpen}) => (
    <Paper elevation={1} sx={CARD} onClick={onOpen} data-testid={`case-${value.id}`}>
        <Stack direction="row" alignItems="baseline" spacing={1} sx={{flexWrap: "wrap"}}>
            <Typography sx={{fontWeight: 600}}>{value.name}</Typography>
            <Typography sx={{fontSize: 12, color: "text.secondary"}}>
                {formatDate(value.createdAt)}
                {value.closedAt ? ` - ${formatDate(value.closedAt)}` : ""}
            </Typography>
            <Box sx={{flex: 1}}/>
            {value.companies.map(company => (
                <Chip key={company.id} label={company.ticker} size="small" variant="outlined"/>
            ))}
        </Stack>
    </Paper>
);

/** The cases, the ones still running first and the closed ones as history under them. */
export const Cases = () => {
    const navigate = useNavigate()
    const {data, loaded, error} = useData("/case")
    const [name, setName] = useState("")
    const [alert, setAlert] = useState(null)

    const cases = data ?? []
    const open = cases.filter(value => !value.closedAt)
    const closed = cases.filter(value => value.closedAt)

    function create() {
        if (!name.trim()) return

        axios.post(`${backend}/case`, {name: name.trim(), companyIds: []})
            .then(response => navigate(`/cases/${response.data.id}`))
            .catch(reason => setAlert(formatError(reason)))
    }

    return (
        <>
            {!loaded && <Loader error={error}/>}
            {loaded &&
                <Box sx={{width: {xs: "100%", sm: "700px"}, margin: "10px auto"}}>
                    <Stack direction="row" spacing={1} sx={{marginBottom: "12px"}}>
                        <TextField
                            size="small"
                            fullWidth
                            label="New case"
                            value={name}
                            onChange={event => setName(event.target.value)}
                            onKeyDown={event => {if (event.key === "Enter") create()}}
                        />
                        <Button variant="outlined" onClick={create} disabled={!name.trim()} startIcon={<AddIcon/>}>
                            Add
                        </Button>
                    </Stack>

                    <Stack spacing={1}>
                        {open.map(value => (
                            <CaseCard key={value.id} value={value} onOpen={() => navigate(`/cases/${value.id}`)}/>
                        ))}
                    </Stack>

                    {closed.length > 0 &&
                        <>
                            <Typography sx={{margin: "16px 0 6px 2px", fontSize: 12, color: "text.secondary"}}>
                                Closed
                            </Typography>
                            <Stack spacing={1} sx={{opacity: 0.7}}>
                                {closed.map(value => (
                                    <CaseCard key={value.id} value={value} onOpen={() => navigate(`/cases/${value.id}`)}/>
                                ))}
                            </Stack>
                        </>
                    }

                    {cases.length === 0 &&
                        <Typography sx={{color: "text.secondary", textAlign: "center", marginTop: "20px"}}>
                            No cases yet.
                        </Typography>
                    }
                </Box>
            }
            <SnackbarErrorAlert error={alert} open={alert !== null} onClose={() => setAlert(null)}/>
        </>
    )
}
