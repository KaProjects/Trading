import React, {useEffect, useState} from "react";
import {useData} from "../service/BackendService";
import Loader from "./Loader";
import {Grid, List, ListItem, ListItemButton, ListItemText, ListSubheader, MenuItem, Select} from "@mui/material";
import {recordEvent} from "../service/utils";

const listStyle = {minWidth: "250px", maxHeight: "calc(100vh - 70px)", marginTop: "2px", overflowY: "scroll", bgcolor: 'background.paper', boxShadow: 1, borderRadius: 2}
const listHeaderStyle = {textAlign: "center", boxShadow: 1, borderRadius: 2, fontSize: "16px", color: "grey"}

const showStates = ["all", "none", "watching", "no-strategy", "deprecated", "sectors"]

const CompanySelector = (props) => {
    const {refresh} = props
    const {data, loaded, error} = useData("/company/lists" + (refresh ? "?refresh" + refresh : ""))
    const [state, setState] = useState(showStates[0])
    const [sector, setSector] = useState(null);

    useEffect(() => {
        if (!props.companySelectorValue) {
            setState(showStates[0])
        } else {
            if (state === showStates[0]) setState(showStates[1])
        }
        // eslint-disable-next-line
    }, [props.companySelectorValue])

    useEffect(() => {
        if (data) {
            setSector(Object.keys(data.sectors)[0])
        }
        // eslint-disable-next-line
    }, [data])

    function handleCompanyClick(companyId, state) {
        props.companies.forEach((company) => {if (company.id === companyId) {props.setCompanySelectorValue(company)}})
        setState(state)
        recordEvent(window.location.pathname + "#selector:companies:" + state)
    }

    return (
        <>
            {!loaded &&
                <Loader error ={error}/>
            }
            {loaded &&
                <Grid container direction="row" alignItems="stretch"
                      justifyContent={state === showStates[0] ? "center" : "flex-start"}
                      sx={state === showStates[0] ? {} : {maxWidth: "250px", position: "absolute", display: { xs: 'none', sm: 'none', md: "none", lg: "block" }}}
                >
                    {(state === showStates[0] || state === showStates[2]) &&
                        <List dense sx={listStyle}
                              subheader={<ListSubheader component="div" sx={listHeaderStyle}>Watching</ListSubheader>}>
                            {data.watchingOldestReview.map((company, index) => (
                                <ListItem key={"a" + index}>
                                    <ListItemButton onClick={() => handleCompanyClick(company.id, showStates[2])}>
                                        <ListItemText
                                            primary={company.ticker}
                                            primaryTypographyProps={{ fontSize: "20px", textAlign: "center" }}
                                            secondary={company.latestReviewDate}
                                            secondaryTypographyProps={{ fontSize: "12px", textAlign: "center" }}
                                        />
                                    </ListItemButton>
                                </ListItem>
                            ))}
                        </List>
                    }
                    {(state === showStates[0] || state === showStates[3]) &&
                        <List dense sx={listStyle}
                              subheader={<ListSubheader component="div" sx={listHeaderStyle}>No Strategy</ListSubheader>}>
                            {data.ownedWithoutStrategy.map((company, index) => (
                                <ListItem key={"a" + index}>
                                    <ListItemButton onClick={() => handleCompanyClick(company.id, showStates[3])}>
                                        <ListItemText
                                            primary={company.ticker}
                                            primaryTypographyProps={{fontSize: "20px", textAlign: "center"}}
                                            secondary={company.latestPurchaseDate}
                                            secondaryTypographyProps={{fontSize: "12px", textAlign: "center"}}
                                        />
                                    </ListItemButton>
                                </ListItem>
                            ))}
                        </List>
                    }
                    {(state === showStates[0] || state === showStates[4]) &&
                        <List dense sx={listStyle}
                              subheader={<ListSubheader component="div" sx={listHeaderStyle}>Deprecated</ListSubheader>}>
                            {data.notWatching.map((company, index) => (
                                <ListItem key={"a" + index}>
                                    <ListItemButton onClick={() => handleCompanyClick(company.id, showStates[4])}>
                                        <ListItemText
                                            primary={company.ticker}
                                            primaryTypographyProps={{fontSize: "20px", textAlign: "center"}}
                                            secondary={company.latestReviewDate}
                                            secondaryTypographyProps={{fontSize: "12px", textAlign: "center"}}
                                        />
                                    </ListItemButton>
                                </ListItem>
                            ))}
                        </List>
                    }
                    {sector && (state === showStates[0] || state === showStates[5]) &&
                        <List dense sx={listStyle}
                              subheader={
                                <ListSubheader component="div" sx={listHeaderStyle}>
                                  <Select value={sector} variant="standard"
                                      sx={{color: "grey", '.MuiSvgIcon-root ': {fill: "white"},
                                          ':not(.Mui-disabled):hover::before': { borderBottomColor: '#ffffff' },
                                          ':before': { borderBottomColor: '#ffffff' },
                                          ':after': { borderBottomColor: '#ffffff' }}}
                                      onChange={event => setSector(event.target.value)}
                                  >
                                      {Object.keys(data.sectors).map((value, index) => (
                                          <MenuItem key={index} value={value} >{value}</MenuItem>
                                      ))}
                                  </Select>
                                </ListSubheader>}
                        >
                            {data.sectors[sector].map((company, index) => (
                                <ListItem key={"a" + index}>
                                    <ListItemButton onClick={() => handleCompanyClick(company.id, showStates[5])}>
                                        <ListItemText
                                            primary={company.ticker}
                                            primaryTypographyProps={{fontSize: "20px", textAlign: "center"}}
                                            secondary={company.latestReviewDate}
                                            secondaryTypographyProps={{fontSize: "12px", textAlign: "center"}}
                                        />
                                    </ListItemButton>
                                </ListItem>
                            ))}
                        </List>
                    }
                </Grid>
            }
        </>
    )
}
export default CompanySelector