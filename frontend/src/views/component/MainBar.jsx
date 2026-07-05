import React, {useEffect} from "react";
import {AppBar, Box, Button, IconButton, Tab, Tabs, Toolbar, Typography} from "@mui/material";
import MenuIcon from '@mui/icons-material/Menu';
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import RemoveCircleOutlineIcon from '@mui/icons-material/RemoveCircleOutline';
import {MainBarSelect} from "./MainBarSelect";
import {useLocation, useNavigate} from "react-router-dom";

export const ACTIVE_STATES = ["only active", "only closed"];
const STATS_TABS = ["Companies", "Monthly", "Quarterly", "Yearly"];

const DEFAULT_MAIN_BAR_CONFIG = {
    showCompanySelector: false,
    showActiveSelector: false,
    showCurrencySelector: false,
    showYearSelector: false,
    showSectorSelector: false,
    showAddTradeButton: false,
    showSellTradeButton: false,
    showAddDividendButton: false,
    showAddCompanyButton: false,
    showStatsTabs: false,
};

const MAIN_BAR_CONFIG = {
    "/trades": {
        showActiveSelector: true,
        showCompanySelector: true,
        showCurrencySelector: true,
        showYearSelector: true,
        showSectorSelector: true,
        showAddTradeButton: true,
        showSellTradeButton: true,
    },
    "/research": {
        showCompanySelector: true,
    },
    "/dividends": {
        showCompanySelector: true,
        showCurrencySelector: true,
        showYearSelector: true,
        showSectorSelector: true,
        showAddDividendButton: true,
    },
    "/companies": {
        showCurrencySelector: true,
        showSectorSelector: true,
        showAddCompanyButton: true,
    },
    "/stats": {
        showStatsTabs: true,
        showSectorSelector: true,
    },
};

export const MainBar = props => {
    const navigate = useNavigate()
    const location = useLocation()
    const config = {
        ...DEFAULT_MAIN_BAR_CONFIG,
        ...(MAIN_BAR_CONFIG[location.pathname] ?? {}),
    }

    if (location.pathname === "/stats") {
        config.showCompanySelector = props.statsTabsIndex !== 0
        config.showYearSelector = props.statsTabsIndex === 0
    }

    function loadStorageStates() {
        const companyId = sessionStorage.getItem('companyId')
        if (companyId) {
            const company = props.companies.find(company => company.id === companyId)
            if (company) {
                props.setCompanySelectorValue(company);
                sessionStorage.removeItem('companyId');
            }
        }
        if (sessionStorage.getItem('tradeState')){
            props.setActiveSelectorValue(sessionStorage.getItem('tradeState'));
            sessionStorage.removeItem('tradeState');
        }
    }

    useEffect(() => {
        props.setYears([]);
        if (["/trades", "/research", "/dividends"].includes(location.pathname)) {
            loadStorageStates();
        }
        // eslint-disable-next-line
    }, [location.pathname, props.companies]);

    const actionButtons = [
        {
            key: "sell-trade",
            visible: config.showSellTradeButton,
            onClick: () => props.setOpenSellTrade(true),
            ariaLabel: "sell trade",
            sx: {},
            icon: <RemoveCircleOutlineIcon sx={{color: '#ff9f9f'}}/>,
        },
        {
            key: "add-trade",
            visible: config.showAddTradeButton,
            onClick: () => props.setOpenAddTrade(true),
            ariaLabel: "add trade",
            sx: {marginRight: "25px"},
            icon: <ControlPointIcon sx={{color: 'lightgreen'}}/>,
        },
        {
            key: "add-dividend",
            visible: config.showAddDividendButton,
            onClick: () => props.setOpenAddDividend(true),
            ariaLabel: "add dividend",
            sx: {marginRight: "25px"},
            icon: <ControlPointIcon sx={{color: 'lightgreen'}}/>,
        },
        {
            key: "add-company",
            visible: config.showAddCompanyButton,
            onClick: () => props.setOpenEditCompany({}),
            ariaLabel: "add company",
            sx: {marginRight: "25px"},
            icon: <ControlPointIcon sx={{color: 'lightgreen'}}/>,
        },
    ]

    const selectors = [
        {
            key: "active",
            visible: config.showActiveSelector,
            values: ACTIVE_STATES,
            value: props.activeSelectorValue,
            setValue: props.setActiveSelectorValue,
            label: "all",
        },
        {
            key: "company",
            visible: config.showCompanySelector,
            values: props.companies,
            value: props.companySelectorValue,
            setValue: props.setCompanySelectorValue,
            valueKey: "ticker",
            label: "companies",
        },
        {
            key: "currency",
            visible: config.showCurrencySelector,
            values: props.currencies,
            value: props.currencySelectorValue,
            setValue: props.setCurrencySelectorValue,
            label: "currencies",
        },
        {
            key: "year",
            visible: config.showYearSelector,
            values: props.years,
            value: props.yearSelectorValue,
            setValue: props.setYearSelectorValue,
            label: "years",
        },
        {
            key: "sector",
            visible: config.showSectorSelector,
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
                        {config.showStatsTabs &&
                            <Tabs value={props.statsTabsIndex}
                                  onChange={(event, value) => props.setStatsTabsIndex(value)}
                                  TabIndicatorProps={{style: {backgroundColor: "white"}}}
                                  textColor="inherit"
                            >
                                {STATS_TABS.map((tab) => (
                                    <Tab key={tab} label={tab}/>
                                ))}
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
