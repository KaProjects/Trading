import React, {useEffect, useState} from "react";
import {Grid, List, ListItem, ListItemButton, ListItemText, ListSubheader, MenuItem, Select} from "@mui/material";
import useMediaQuery from "@mui/material/useMediaQuery";
import {recordEvent} from "../../service/utils";
import {COMPANY_LIST_TITLES, getCompanyListKeys, getCompanyListTitle} from "../../service/CompanyListService";

export const BUILT_IN_LIST_TITLES = COMPANY_LIST_TITLES
export const COMPANY_SELECTOR_COMPACT_BREAKPOINT = 600
export const COMPANY_SELECTOR_SIDEBAR_BREAKPOINT = 1200
const EMPTY_COMPANY_LISTS = {all: []}

export const CompanySelector = (props) => {
    const data = props.companyLists ?? EMPTY_COMPANY_LISTS
    const onCustomTagsChange = props.onCustomTagsChange
    const [activeList, setActiveList] = useState(null)
    const isCompactScreen = useMediaQuery(`(max-width:${COMPANY_SELECTOR_COMPACT_BREAKPOINT}px)`)
    const isSidebarHidden = useMediaQuery(`(max-width:${COMPANY_SELECTOR_SIDEBAR_BREAKPOINT}px)`)

    const listKeys = getCompanyListKeys(data)
    const compactListVisible = !props.companySelectorValue && isCompactScreen
    const selectedList = activeList ?? (compactListVisible ? listKeys[0] ?? null : null)

    useEffect(() => {
        if (data && onCustomTagsChange) {
            onCustomTagsChange(getCompanyListKeys(data).filter(key => !COMPANY_LIST_TITLES[key]))
        }
    }, [data, onCustomTagsChange])

    useEffect(() => {
        const availableListKeys = getCompanyListKeys(data)

        if (!props.companySelectorValue) {
            setActiveList(previousList => isCompactScreen
                ? previousList && data[previousList] ? previousList : availableListKeys[0] ?? null
                : null
            )
            return
        }

        if (!data) {
            return
        }

        setActiveList((previousList) => {
            const containsSelectedCompany = (listKey) => data[listKey]?.some(
                company => company.id === props.companySelectorValue.id
            )

            if (props.companyListSelectorValue && data[props.companyListSelectorValue]) {
                return props.companyListSelectorValue
            }

            if (previousList && containsSelectedCompany(previousList)) {
                return previousList
            }

            return availableListKeys.find(containsSelectedCompany) ?? availableListKeys[0] ?? null
        })
    }, [data, isCompactScreen, props.companySelectorValue, props.companyListSelectorValue])

    function handleCompanyClick(companyId, listKey) {
        const selectedCompany = (props.companyLists.all ?? []).find((company) => company.id === companyId)

        if (selectedCompany) {
            props.setCompanySelectorValue(selectedCompany)
        }

        setActiveList(listKey)
        props.setCompanyListSelectorValue?.(listKey)
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

    const listStyle = {
        minWidth: compactListVisible ? "100%" : "200px",
        width: compactListVisible ? "100%" : "auto",
        flexShrink: 0,
        marginTop: "2px",
        maxHeight: {
            xs: "calc(100dvh - var(--main-bar-height, 48px) - 8px)",
            sm: "calc(100dvh - var(--main-bar-height, 48px) - 16px)",
        },
        overflowY: "auto",
        overscrollBehavior: "contain",
        bgcolor: 'background.paper',
        boxShadow: 1,
        borderRadius: 2,
    }
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
    const sidebarSx = {maxWidth: "200px", position: "absolute", left: 0, display: "block"}
    const compactSx = {
        width: {xs: "calc(100% + 16px)", sm: "calc(100% + 32px)"},
        maxWidth: "none",
        marginLeft: {xs: "-8px", sm: "-16px"},
    }

    function renderListHeader(listKey) {
        if (!selectedList) {
            return <ListSubheader component="div" sx={listHeaderStyle}>{getListTitle(listKey)}</ListSubheader>
        }

        return (
            <ListSubheader component="div" sx={listHeaderStyle}>
                <Select
                    value={selectedList}
                    variant="standard"
                    inputProps={{'aria-label': 'Company list'}}
                    sx={listSelectorStyle}
                    onChange={event => {
                        setActiveList(event.target.value)
                        props.setCompanyListSelectorValue?.(event.target.value)
                    }}
                >
                    {listKeys.map((key) => (
                        <MenuItem key={key} value={key}>{getListTitle(key)}</MenuItem>
                    ))}
                </Select>
            </ListSubheader>
        )
    }

    function renderCompanyList(listKey) {
        if (selectedList && selectedList !== listKey) {
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

    if (props.companySelectorValue && isSidebarHidden) {
        return null
    }

    return (
        <Grid container wrap="nowrap" direction="row" alignItems="stretch"
              justifyContent={selectedList ? "flex-start" : "safe center"}
              sx={{
                  width: "100%",
                  overflowX: selectedList ? "visible" : "auto",
                  ...(compactListVisible ? compactSx : activeList ? sidebarSx : {}),
              }}
        >
            {listKeys.map(renderCompanyList)}
        </Grid>
    )
}
