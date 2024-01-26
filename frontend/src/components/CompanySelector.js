import React, {useEffect, useState} from "react";
import {useData} from "../fetch";
import Loader from "./Loader";
import {Grid, List, ListItem, ListItemButton, ListItemText, ListSubheader} from "@mui/material";

const listStyle = {minWidth: "250px", maxHeight: "calc(100vh - 70px)", marginTop: "2px", overflowY: "scroll", bgcolor: 'background.paper', boxShadow: 1, borderRadius: 2}
const listHeaderStyle = {textAlign: "center", boxShadow: 1, borderRadius: 2}

const showStates = ["all", "none", "watching", "no-strategy", "deprecated"]

const CompanySelector = (props) => {
    const {refresh} = props
    const {data, loaded, error} = useData("/company/lists" + (refresh ? "?refresh" + refresh : ""))
    const [state, setState] = useState(showStates[0]);

    useEffect(() => {
        if (!props.companySelectorValue) {
            setState(showStates[0])
        } else {
            if (state === showStates[0]) setState(showStates[1])
        }
        // eslint-disable-next-line
    }, [props.companySelectorValue]);

    function handleCompanyClick(companyId, state) {
        props.companies.forEach((company) => {if (company.id === companyId) {props.setCompanySelectorValue(company)}})
        setState(state)
    }

    return (
        <>
            {!loaded &&
                <Loader error ={error}/>
            }
            {loaded &&
                <Grid container direction="row" alignItems="stretch"
                      justifyContent={state === showStates[0] ? "center" : "flex-start"}
                      sx={state === showStates[0] ? {} : {maxWidth: "250px", position: "absolute"}}
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
                </Grid>
            }
        </>
    )
}
export default CompanySelector;