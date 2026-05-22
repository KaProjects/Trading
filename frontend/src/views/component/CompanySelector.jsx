import React, {useEffect, useState} from "react";
import {useData} from "../../service/BackendService";
import {Loader} from "./Loader";
import {Grid, List, ListItem, ListItemButton, ListItemText, ListSubheader, MenuItem, Select} from "@mui/material";
import {recordEvent} from "../../service/utils";

const SELECTOR_STATES = {
    ALL: "all",
    NONE: "none",
    WATCHING: "watching",
    OWNED: "owned",
    UNREPORTED: "unreported",
    DEPRECATED: "deprecated",
    SECTORS: "sectors",
}

export const CompanySelector = (props) => {
    const {refresh} = props
    const {data, loaded, error} = useData("/company/lists" + (refresh ? "?refresh" + refresh : ""))
    const [state, setState] = useState(SELECTOR_STATES.ALL)
    const [sector, setSector] = useState(null);

    useEffect(() => {
        if (!props.companySelectorValue) {
            setState(SELECTOR_STATES.ALL)
            return
        }

        setState((prev) => prev === SELECTOR_STATES.ALL ? SELECTOR_STATES.NONE : prev)
    }, [props.companySelectorValue])

    useEffect(() => {
        if (data) {
            setSector(Object.keys(data.sectors)[0] || null)
        }
        // eslint-disable-next-line
    }, [data])

    function handleCompanyClick(companyId, selectorState) {
        const selectedCompany = props.companies.find((company) => company.id === companyId)

        if (selectedCompany) {
            props.setCompanySelectorValue(selectedCompany)
        }

        setState(selectorState)
        recordEvent(window.location.pathname + "#selector:companies:" + selectorState)
    }

    const listStyle = {minWidth: "250px", maxHeight: "calc(100vh - 70px)", marginTop: "2px", overflowY: "scroll", bgcolor: 'background.paper', boxShadow: 1, borderRadius: 2}
    const listHeaderStyle = {textAlign: "center", boxShadow: 1, borderRadius: 2, fontSize: "16px", color: "grey"}
    const sidebarSx = {maxWidth: "250px", position: "absolute", display: "block", "@media (max-width:2099px)": {display: "none"}}

    function renderCompanyList({title, companies, selectorState, secondary, subheader}) {
        if (!(state === SELECTOR_STATES.ALL || state === selectorState)) {
            return null
        }

        return (
            <List
                dense
                sx={listStyle}
                subheader={subheader || <ListSubheader component="div" sx={listHeaderStyle}>{title}</ListSubheader>}
            >
                {companies.map((company) => (
                    <ListItem key={company.id}>
                        <ListItemButton onClick={() => handleCompanyClick(company.id, selectorState)}>
                            <ListItemText
                                primary={company.ticker}
                                primaryTypographyProps={{fontSize: "20px", textAlign: "center"}}
                                secondary={secondary(company)}
                                secondaryTypographyProps={{fontSize: "12px", textAlign: "center"}}
                            />
                        </ListItemButton>
                    </ListItem>
                ))}
            </List>
        )
    }

    return (
        <>
            {!loaded &&
                <Loader error ={error}/>
            }
            {loaded &&
                <Grid container direction="row" alignItems="stretch"
                      justifyContent={state === SELECTOR_STATES.ALL ? "center" : "flex-start"}
                      sx={state === SELECTOR_STATES.ALL ? {} : sidebarSx}
                >
                    {renderCompanyList({
                        title: "Watching",
                        companies: data.watching,
                        selectorState: SELECTOR_STATES.WATCHING,
                        secondary: company => company.latestRecordDate,
                    })}
                    {renderCompanyList({
                        title: "Owned",
                        companies: data.owned,
                        selectorState: SELECTOR_STATES.OWNED,
                        secondary: company => company.latestPurchaseDate,
                    })}
                    {renderCompanyList({
                        title: "Not Reported",
                        companies: data.unreported,
                        selectorState: SELECTOR_STATES.UNREPORTED,
                        secondary: company => company.latestUnreportedPeriodEndingMonth,
                    })}
                    {renderCompanyList({
                        title: "Deprecated",
                        companies: data.deprecated,
                        selectorState: SELECTOR_STATES.DEPRECATED,
                        secondary: company => company.latestRecordDate,
                    })}
                    {sector && data.sectors?.[sector] && renderCompanyList({
                        companies: data.sectors[sector],
                        selectorState: SELECTOR_STATES.SECTORS,
                        secondary: company => company.latestRecordDate,
                        subheader: (
                            <ListSubheader component="div" sx={listHeaderStyle}>
                                <Select value={sector} variant="standard"
                                    sx={{color: "grey", '.MuiSvgIcon-root ': {fill: "white"},
                                        ':not(.Mui-disabled):hover::before': { borderBottomColor: '#ffffff' },
                                        ':before': { borderBottomColor: '#ffffff' },
                                        ':after': { borderBottomColor: '#ffffff' }}}
                                    onChange={event => setSector(event.target.value)}
                                >
                                    {Object.keys(data.sectors).map((value) => (
                                        <MenuItem key={value} value={value} >{value}</MenuItem>
                                    ))}
                                </Select>
                            </ListSubheader>
                        ),
                    })}
                </Grid>
            }
        </>
    )
}
