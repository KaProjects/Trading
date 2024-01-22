import React from "react";
import {useData} from "../fetch";
import Loader from "./Loader";
import {Grid, List, ListItem, ListItemButton, ListItemText, ListSubheader} from "@mui/material";

const listStyle = {minWidth: "250px", maxHeight: "calc(100vh - 70px)", marginTop: "2px", overflowY: "scroll", bgcolor: 'background.paper', boxShadow: 1, borderRadius: 2}
const listHeaderStyle = {textAlign: "center", boxShadow: 1, borderRadius: 2}

const CompanySelector = (props) => {

    const {data, loaded, error} = useData("/company/lists")

    function selectCompany(id) {
        props.companies.map((company) => {if (company.id === id) {props.setCompanySelectorValue(company)}})
    }

    return (
        <>
            {!loaded &&
                <Loader error ={error}/>
            }
            {loaded &&
                <Grid container direction="row" justifyContent="center" alignItems="stretch">
                    <List dense sx={listStyle}
                          subheader={<ListSubheader component="div" sx={listHeaderStyle}>Watching</ListSubheader>}>
                        {data.watchingOldestReview.map((company, index) => (
                            <ListItem key={"a" + index}>
                                <ListItemButton onClick={() => selectCompany(company.id)}>
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
                    <List dense sx={listStyle}
                          subheader={<ListSubheader component="div" sx={listHeaderStyle}>No Strategy</ListSubheader>}>
                        {data.ownedWithoutStrategy.map((company, index) => (
                            <ListItem key={"a" + index}>
                                <ListItemButton onClick={() => selectCompany(company.id)}>
                                    <ListItemText
                                        primary={company.ticker}
                                        primaryTypographyProps={{ fontSize: "20px", textAlign: "center" }}
                                        secondary={company.latestPurchaseDate}
                                        secondaryTypographyProps={{ fontSize: "12px", textAlign: "center" }}
                                    />
                                </ListItemButton>
                            </ListItem>
                        ))}
                    </List>
                    <List dense sx={listStyle}
                          subheader={<ListSubheader component="div" sx={listHeaderStyle}>Deprecated</ListSubheader>}>
                        {data.notWatching.map((company, index) => (
                            <ListItem key={"a" + index}>
                                <ListItemButton onClick={() => selectCompany(company.id)}>
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
                </Grid>
            }
        </>
    )
}
export default CompanySelector;