import React, {useEffect, useState} from "react";
import {Grid, List, ListItem, ListItemButton, ListItemText, ListSubheader, MenuItem, Select} from "@mui/material";
import {recordEvent} from "../../service/utils";
import {COMPANY_LIST_TITLES, getCompanyListKeys, getCompanyListTitle} from "../../service/CompanyListService";

export const BUILT_IN_LIST_TITLES = COMPANY_LIST_TITLES

export const CompanySelector = (props) => {
    const data = props.companyLists ?? {all: []}
    const [activeList, setActiveList] = useState(null)

    const listKeys = getCompanyListKeys(data)

    useEffect(() => {
        if (data && props.onCustomTagsChange) {
            props.onCustomTagsChange(getCompanyListKeys(data).filter(key => !COMPANY_LIST_TITLES[key]))
        }
    }, [data, props.onCustomTagsChange])

    useEffect(() => {
        if (!props.companySelectorValue) {
            setActiveList(null)
            return
        }

        if (!data) {
            return
        }

        setActiveList((previousList) => {
            if (previousList && data[previousList]) {
                return previousList
            }

            const availableListKeys = getCompanyListKeys(data)
            return availableListKeys.find((listKey) => data[listKey].some(
                company => company.id === props.companySelectorValue.id
            )) ?? availableListKeys[0] ?? null
        })
    }, [data, props.companySelectorValue])

    function handleCompanyClick(companyId, listKey) {
        const selectedCompany = (props.companyLists.all ?? []).find((company) => company.id === companyId)

        if (selectedCompany) {
            props.setCompanySelectorValue(selectedCompany)
        }

        setActiveList(listKey)
        recordEvent(window.location.pathname + "#selector:companies:" + listKey)
    }

    function getListTitle(listKey) {
        return getCompanyListTitle(listKey)
    }

    function getSecondaryValue(company, listKey) {
        switch (listKey) {
            case "researched":
                return company.latestPeriodEndingMonth
            case "recent":
                return company.latestRecordDate
            default:
                return undefined
        }
    }

    const listStyle = {minWidth: "200px", marginTop: "2px", bgcolor: 'background.paper', boxShadow: 1, borderRadius: 2}
    const listHeaderStyle = {textAlign: "center", boxShadow: 1, borderRadius: 2, fontSize: "16px", color: "grey"}
    const listSelectorStyle = {
        color: "grey",
        fontSize: "16px",
        '.MuiSelect-select': {textAlign: "center", paddingTop: 0, paddingBottom: 0},
        '.MuiSvgIcon-root': {fill: "grey"},
        ':not(.Mui-disabled):hover::before': {borderBottomColor: 'grey'},
        ':before': {borderBottomColor: 'transparent'},
        ':after': {borderBottomColor: 'grey'},
    }
    const sidebarSx = {maxWidth: "200px", position: "absolute", display: "block", "@media (max-width:2030px)": {display: "none"}}

    function renderListHeader(listKey) {
        if (!activeList) {
            return <ListSubheader component="div" sx={listHeaderStyle}>{getListTitle(listKey)}</ListSubheader>
        }

        return (
            <ListSubheader component="div" sx={listHeaderStyle}>
                <Select
                    value={activeList}
                    variant="standard"
                    inputProps={{'aria-label': 'Company list'}}
                    sx={listSelectorStyle}
                    onChange={event => setActiveList(event.target.value)}
                >
                    {listKeys.map((key) => (
                        <MenuItem key={key} value={key}>{getListTitle(key)}</MenuItem>
                    ))}
                </Select>
            </ListSubheader>
        )
    }

    function renderCompanyList(listKey) {
        if (activeList && activeList !== listKey) {
            return null
        }

        return (
            <List dense sx={listStyle} subheader={renderListHeader(listKey)} key={listKey}>
                {data[listKey].map((company) => (
                    <ListItem key={company.id}>
                        <ListItemButton onClick={() => handleCompanyClick(company.id, listKey)}>
                            <ListItemText
                                primary={company.ticker}
                                secondary={getSecondaryValue(company, listKey)}
                                slotProps={{
                                    primary: {fontSize: "20px", textAlign: "center"},
                                    secondary: {fontSize: "12px", textAlign: "center"},
                                }}
                            />
                        </ListItemButton>
                    </ListItem>
                ))}
            </List>
        )
    }

    return (
        <Grid container direction="row" alignItems="stretch"
              justifyContent={activeList ? "flex-start" : "center"}
              sx={{width: "100%", ...(activeList ? sidebarSx : {})}}
        >
            {listKeys.map(renderCompanyList)}
        </Grid>
    )
}
