import React, {useState} from "react";
import {useNavigate} from "react-router-dom";
import {Box, Chip, Paper, Stack, Typography} from "@mui/material";
import axios from "axios";
import {useData} from "../service/BackendService";
import {Loader} from "./component/Loader";
import {SnackbarErrorAlert} from "./component/SnackbarErrorAlert";
import {backend} from "../properties";
import {CaseNameDialog} from "../dialog/CaseNameDialog";
import {formatDate, formatError} from "../service/FormattingService";
import {COMPANY_CHIP, NARROW} from "./component/companyChip";

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
            {/* On a line of their own under the name, however wide the screen is: a case is
                read by its name first and by what it covers second. A company the app does not
                hold has no id, and is marked as written rather than linked. */}
            <Box sx={{
                display: "flex",
                flexWrap: "wrap",
                width: "100%",
                gap: "4px",
                marginTop: "3px",
                [NARROW]: {gap: "3px"},
            }}>
                {value.companies.map(company => (
                    <Chip
                        key={company.id ?? `name:${company.ticker}`}
                        label={company.ticker}
                        size="small"
                        variant="outlined"
                        sx={company.id
                            ? COMPANY_CHIP
                            : {...COMPANY_CHIP, fontStyle: "italic"}}
                    />
                ))}
            </Box>
        </Stack>
    </Paper>
);

/** The cases, the ones still running first and the closed ones as history under them. */
export const Cases = props => {
    const navigate = useNavigate()
    const {data, loaded, error} = useData("/case")
    const [alert, setAlert] = useState(null)

    const cases = data ?? []
    const open = cases.filter(value => !value.closedAt)
    const closed = cases.filter(value => value.closedAt)

    /** A case is named before it exists, and opens on the page it was named for. */
    function create(name) {
        axios.post(`${backend}/case`, {name: name, companyIds: []})
            .then(response => navigate(`/cases/${response.data.id}`))
            .catch(reason => setAlert(formatError(reason)))
    }

    return (
        <>
            {!loaded && <Loader error={error}/>}
            {loaded &&
                <Box sx={{width: {xs: "100%", sm: "700px"}, margin: "10px auto"}}>
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
            <CaseNameDialog
                open={Boolean(props.openAddCase)}
                title="New case"
                onSubmit={create}
                handleClose={() => props.setOpenAddCase?.(false)}
            />
            <SnackbarErrorAlert error={alert} open={alert !== null} onClose={() => setAlert(null)}/>
        </>
    )
}
