import React from "react";
import {AppBar, Box, Button, IconButton, Tab, Tabs, Toolbar, Typography} from "@mui/material";
import MenuIcon from '@mui/icons-material/Menu';
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import RemoveCircleOutlineIcon from '@mui/icons-material/RemoveCircleOutline';
import {MainBarSelect} from "./MainBarSelect";
import {useNavigate} from "react-router-dom";


export const MainBar = props => {
    const navigate = useNavigate()
    const actionButtons = [
        {
            key: "sell-trade",
            visible: props.showSellTradeButton,
            onClick: () => props.setOpenSellTrade(true),
            ariaLabel: "sell trade",
            sx: {},
            icon: <RemoveCircleOutlineIcon sx={{color: '#ff9f9f'}}/>,
        },
        {
            key: "add-trade",
            visible: props.showAddTradeButton,
            onClick: () => props.setOpenAddTrade(true),
            ariaLabel: "add trade",
            sx: {marginRight: "25px"},
            icon: <ControlPointIcon sx={{color: 'lightgreen'}}/>,
        },
        {
            key: "add-dividend",
            visible: props.showAddDividendButton,
            onClick: () => props.setOpenAddDividend(true),
            ariaLabel: "add dividend",
            sx: {marginRight: "25px"},
            icon: <ControlPointIcon sx={{color: 'lightgreen'}}/>,
        },
        {
            key: "add-company",
            visible: props.showAddCompanyButton,
            onClick: () => props.setOpenEditCompany({}),
            ariaLabel: "add company",
            sx: {marginRight: "25px"},
            icon: <ControlPointIcon sx={{color: 'lightgreen'}}/>,
        },
    ]

    const selectors = [
        {
            key: "active",
            visible: props.showActiveSelector,
            values: props.activeStates,
            value: props.activeSelectorValue,
            setValue: props.setActiveSelectorValue,
            label: "all",
        },
        {
            key: "company",
            visible: props.showCompanySelector,
            values: props.companies,
            value: props.companySelectorValue,
            setValue: props.setCompanySelectorValue,
            valueKey: "ticker",
            label: "companies",
        },
        {
            key: "currency",
            visible: props.showCurrencySelector,
            values: props.currencies,
            value: props.currencySelectorValue,
            setValue: props.setCurrencySelectorValue,
            label: "currencies",
        },
        {
            key: "year",
            visible: props.showYearSelector !== null,
            values: props.showYearSelector,
            value: props.yearSelectorValue,
            setValue: props.setYearSelectorValue,
            label: "years",
        },
        {
            key: "sector",
            visible: props.showSectorSelector,
            values: props.sectors,
            value: props.sectorSelectorValue,
            setValue: props.setSectorSelectorValue,
            valueKey: "name",
            label: "sectors",
        },
    ]

    return (
        <Box sx={{ flexGrow: 1 }}>
            <AppBar position="static">
                <Toolbar variant="dense">
                    <IconButton size="large" edge="start" color="inherit" aria-label="open drawer" sx={{ mr: 2 }}
                                onClick={() => navigate("/")}>
                        <MenuIcon />
                    </IconButton>
                    <Typography variant="h6" sx={{ display: { xs: 'none', sm: 'block' } }}>
                        Trading
                    </Typography>
                    <Box sx={{ flexGrow: 1 }} />
                    <Box sx={{ display: { xs: 'block', md: 'flex' } }}>
                        {props.showStatsTabs &&
                            <Tabs value={props.statsTabsIndex}
                                  onChange={(event, value) => props.setStatsTabsIndex(value)}
                                  TabIndicatorProps={{style: {backgroundColor: "white"}}}
                                  textColor="inherit"
                            >
                                <Tab label="Companies"/>
                                <Tab label="Monthly"/>
                                <Tab label="Quarterly"/>
                                <Tab label="Yearly"/>
                            </Tabs>
                        }
                        {actionButtons
                            .filter((button) => button.visible)
                            .map((button) => (
                                <Button key={button.key} onClick={button.onClick} aria-label={button.ariaLabel} sx={button.sx}>
                                    {button.icon}
                                </Button>
                            ))}
                        {selectors
                            .filter((selector) => selector.visible)
                            .map((selector) => (
                                <MainBarSelect
                                    key={selector.key}
                                    values={selector.values}
                                    value={selector.value}
                                    setValue={selector.setValue}
                                    valueKey={selector.valueKey}
                                    label={selector.label}
                                />
                            ))}
                    </Box>
                    <Box sx={{ flexGrow: 1 }} />
                </Toolbar>
            </AppBar>
        </Box>
    )
}
