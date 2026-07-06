import React, {useEffect} from "react";
import {AppBar, Box, Button, IconButton, Tab, Tabs, Toolbar, Tooltip, Typography} from "@mui/material";
import MenuIcon from '@mui/icons-material/Menu';
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import RemoveCircleOutlineIcon from '@mui/icons-material/RemoveCircleOutline';
import {MainBarSelect} from "./MainBarSelect";
import {useLocation, useNavigate} from "react-router-dom";
import {ReactComponent as TradesRedirectIcon} from "../../assets/icons/trades-redirect.svg";
import {ReactComponent as DividendsRedirectIcon} from "../../assets/icons/dividends-redirect.svg";
import {ReactComponent as ResearchRedirectIcon} from "../../assets/icons/research-redirect.svg";

export const ACTIVE_STATES = ["only active", "only closed"];
const STATS_TABS = ["Companies", "Monthly", "Quarterly", "Yearly"];
const DATA_ROUTES = ["/trades", "/dividends", "/research"];

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

const researchExternalLinks = (ticker) => [
    {
        label: "TradingView financials",
        icon: "https://www.google.com/s2/favicons?domain=tradingview.com&sz=32",
        url: `https://www.tradingview.com/symbols/NASDAQ-${ticker}/financials-income-statement/?statements-period=FQ`,
    },
    {
        label: "MarketBeat ratings",
        icon: "https://www.google.com/s2/favicons?domain=marketbeat.com&sz=32",
        url: `https://www.marketbeat.com/stocks/NASDAQ/${ticker}/forecast/#ratings-table`,
    },
    {
        label: "Zacks earnings estimates",
        icon: "https://www.google.com/s2/favicons?domain=zacks.com&sz=32",
        url: `https://www.zacks.com/stock/quote/${ticker}/detailed-earning-estimates#detailed_earnings_estimates`,
    },
]

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

    if (props.companySelectorValue) {
        config.showCurrencySelector = false
        config.showSectorSelector = false
    }

    function loadNavigationState() {
        if (!location.state) {
            return
        }
        const remainingState = {...location.state}
        let consumed = false

        if (location.state.companyId) {
            const company = props.companies.find(company => company.id === location.state.companyId)
            if (company) {
                props.setCompanySelectorValue(company);
                delete remainingState.companyId
                consumed = true
            }
        }
        if (location.state.tradeState){
            props.setActiveSelectorValue(location.state.tradeState);
            delete remainingState.tradeState
            consumed = true
        }
        if (location.state.currency){
            props.setCurrencySelectorValue(location.state.currency);
            delete remainingState.currency
            consumed = true
        }
        if (location.state.year){
            props.setYearSelectorValue(location.state.year);
            delete remainingState.year
            consumed = true
        }
        if (location.state.sector) {
            const sector = props.sectors.find(sector => sector.key === location.state.sector)
            if (sector) {
                props.setSectorSelectorValue(sector);
                delete remainingState.sector
                consumed = true
            }
        }
        if (consumed) {
            navigate(location.pathname, {replace: true, state: remainingState})
        }
    }

    useEffect(() => {
        props.setYears([]);
        if (["/trades", "/research", "/dividends"].includes(location.pathname)) {
            loadNavigationState();
        }
        // eslint-disable-next-line
    }, [location.pathname, location.state, props.companies, props.sectors]);

    function redirectTo(path) {
        navigate(path, {
            state: {
                companyId: props.companySelectorValue?.id,
                currency: props.currencySelectorValue,
                year: props.yearSelectorValue,
                sector: props.sectorSelectorValue?.key,
            }
        });
    }

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

    const pageNavigationButtons = [
        {
            key: "go-trades",
            visible: location.pathname !== "/trades" && DATA_ROUTES.includes(location.pathname),
            onClick: () => redirectTo("/trades"),
            ariaLabel: "go to trades",
            tooltip: "Go to trades",
            icon: TradesRedirectIcon,
        },
        {
            key: "go-dividends",
            visible: location.pathname !== "/dividends" && DATA_ROUTES.includes(location.pathname),
            onClick: () => redirectTo("/dividends"),
            ariaLabel: "go to dividends",
            tooltip: "Go to dividends",
            icon: DividendsRedirectIcon,
        },
        {
            key: "go-research",
            visible: location.pathname !== "/research" && DATA_ROUTES.includes(location.pathname),
            onClick: () => redirectTo("/research"),
            ariaLabel: "go to research",
            tooltip: "Go to research",
            icon: ResearchRedirectIcon,
        },
    ]
    const showResearchExternalLinks = location.pathname === "/research" && props.companySelectorValue?.ticker

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
                        {showResearchExternalLinks &&
                            <Box sx={{display: "flex", alignItems: "center", marginRight: "8px"}}>
                                {researchExternalLinks(props.companySelectorValue.ticker).map((link) => (
                                    <Tooltip key={link.label} title={link.label}>
                                        <IconButton
                                            component="a"
                                            href={link.url}
                                            target="_blank"
                                            rel="noopener noreferrer"
                                            size="small"
                                            sx={{width: 45, height: 30}}
                                        >
                                            <Box
                                                component="img"
                                                src={link.icon}
                                                alt={link.label}
                                                sx={{width: 21, height: 21}}
                                            />
                                        </IconButton>
                                    </Tooltip>
                                ))}
                            </Box>
                        }
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
                        <Box sx={{display: "flex", alignItems: "center", marginLeft: "8px"}}>
                            {pageNavigationButtons
                                .filter((button) => button.visible)
                                .map((button) => {
                                    const NavigationIcon = button.icon
                                    return (
                                        <Tooltip key={button.key} title={button.tooltip}>
                                            <IconButton onClick={button.onClick} aria-label={button.ariaLabel} size="small" sx={{width: 50, height: 30}}>
                                                <Box
                                                    component={NavigationIcon}
                                                    sx={{color: "white", width: 23, height: 23}}
                                                />
                                            </IconButton>
                                        </Tooltip>
                                    )
                                })}
                        </Box>
                    </Box>
                    <Box sx={{ flexGrow: 1 }} />
                </Toolbar>
            </AppBar>
        </Box>
    )
}
