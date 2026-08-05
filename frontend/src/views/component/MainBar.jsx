import React, {useEffect, useLayoutEffect, useRef} from "react";
import {AppBar, Box, Button, IconButton, Tab, Tabs, Toolbar, Typography} from "@mui/material";
import MenuIcon from '@mui/icons-material/Menu';
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import RemoveCircleOutlineIcon from '@mui/icons-material/RemoveCircleOutline';
import {MainBarSelect} from "./MainBarSelect";
import {MainBarIconButton} from "./MainBarIconButton";
import {useLocation, useNavigate} from "react-router-dom";
import {ReactComponent as TradesRedirectIcon} from "../../assets/icons/trades-redirect.svg";
import {ReactComponent as DividendsRedirectIcon} from "../../assets/icons/dividends-redirect.svg";
import {ReactComponent as ResearchRedirectIcon} from "../../assets/icons/research-redirect.svg";

export const ACTIVE_STATES = ["only active", "only closed"];
export const RESEARCH_TAB = {
    research: 0,
    records: 1,
};
const STATS_TABS = ["Companies", "Monthly", "Quarterly", "Yearly"];
const RESEARCH_TAB_LABELS = ["Research", "Records"];
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
    showResearchTabs: false,
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
        showResearchTabs: true,
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
    const mainBarRef = useRef(null)
    const companies = props.companyLists?.all ?? []
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
            const company = companies.find(company => company.id === location.state.companyId)
            if (company) {
                props.setCompanySelectorValue(company);
                delete remainingState.companyId
                consumed = true
            }
        }
        if (Object.prototype.hasOwnProperty.call(location.state, "tradeState")) {
            props.setActiveSelectorValue(location.state.tradeState ?? "");
            delete remainingState.tradeState
            consumed = true
        }
        if (Object.prototype.hasOwnProperty.call(location.state, "researchTab")) {
            props.setResearchTabsIndex(location.state.researchTab);
            delete remainingState.researchTab
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
        if (["/trades", "/research", "/dividends"].includes(location.pathname)) {
            loadNavigationState();
        }
        // eslint-disable-next-line
    }, [location.pathname, location.state, props.companyLists, props.sectors]);

    useLayoutEffect(() => {
        function updateMainBarHeight() {
            if (mainBarRef.current) {
                document.documentElement.style.setProperty("--main-bar-height", `${mainBarRef.current.offsetHeight}px`)
            }
        }

        updateMainBarHeight()
        window.addEventListener("resize", updateMainBarHeight)

        let resizeObserver
        if (typeof ResizeObserver !== "undefined" && mainBarRef.current) {
            resizeObserver = new ResizeObserver(updateMainBarHeight)
            resizeObserver.observe(mainBarRef.current)
        }

        return () => {
            window.removeEventListener("resize", updateMainBarHeight)
            if (resizeObserver) {
                resizeObserver.disconnect()
            }
        }
    }, [])

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
            tooltip: "Sell trade",
            color: "#ff9f9f",
            icon: RemoveCircleOutlineIcon,
        },
        {
            key: "add-trade",
            visible: config.showAddTradeButton,
            onClick: () => props.setOpenAddTrade(true),
            ariaLabel: "add trade",
            tooltip: "Add trade",
            color: "lightgreen",
            icon: ControlPointIcon,
        },
        {
            key: "add-dividend",
            visible: config.showAddDividendButton,
            onClick: () => props.setOpenAddDividend(true),
            ariaLabel: "add dividend",
            tooltip: "Add dividend",
            color: "lightgreen",
            icon: ControlPointIcon,
        },
        {
            key: "add-company",
            visible: config.showAddCompanyButton,
            onClick: () => props.setOpenEditCompany({}),
            ariaLabel: "add company",
            tooltip: "Add company",
            color: "lightgreen",
            icon: ControlPointIcon,
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
            values: companies,
            value: props.companySelectorValue,
            setValue: props.setCompanySelectorValue,
            valueKey: "ticker",
            label: "companies",
            companyLists: props.companyLists,
            defaultCompanyList: "all",
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
    const visibleActionButtons = actionButtons.filter((button) => button.visible)
    const visibleSelectors = selectors.filter((selector) => selector.visible)
    const visiblePageNavigationButtons = pageNavigationButtons.filter((button) => button.visible)

    return (
        <Box sx={{flexGrow: 1}}>
            <AppBar ref={mainBarRef} position="fixed" sx={{top: 0, zIndex: (theme) => theme.zIndex.drawer + 1}}>
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
                                  slotProps={{indicator: {style: {backgroundColor: "white"}}}}
                                  textColor="inherit"
                                  sx={{
                                      "& .MuiTabs-list": {flexWrap: {xs: "wrap", sm: "nowrap"}},
                                      "& .MuiTab-root": {minWidth: {xs: "50%", sm: 90}},
                                  }}
                            >
                                {STATS_TABS.map((tab) => (
                                    <Tab key={tab} label={tab}/>
                                ))}
                            </Tabs>
                        }
                        {config.showResearchTabs && props.companySelectorValue &&
                            <Tabs value={props.researchTabsIndex}
                                  onChange={(event, value) => props.setResearchTabsIndex(value)}
                                  slotProps={{indicator: {style: {backgroundColor: "white"}}}}
                                  textColor="inherit"
                                  sx={{display: {xs: "flex", sm: "none"}}}
                            >
                                {RESEARCH_TAB_LABELS.map((tab) => (
                                    <Tab key={tab} label={tab}/>
                                ))}
                            </Tabs>
                        }
                        {visibleActionButtons.length > 0 &&
                            <Box sx={{display: "flex", alignItems: "center", marginRight: "8px"}}>
                                {visibleActionButtons.map((button) => (
                                    <MainBarIconButton
                                        key={button.key}
                                        tooltip={button.tooltip}
                                        ariaLabel={button.ariaLabel}
                                        onClick={button.onClick}
                                        icon={button.icon}
                                        color={button.color}
                                        buttonSx={{width: 45, height: 30}}
                                        iconSx={{width: 23, height: 23}}
                                    />
                                ))}
                            </Box>
                        }
                        {showResearchExternalLinks &&
                            <Box sx={{display: "flex", alignItems: "center", marginRight: "8px"}}>
                                {researchExternalLinks(props.companySelectorValue.ticker).map((link) => (
                                    <MainBarIconButton
                                        key={link.label}
                                        tooltip={link.label}
                                        href={link.url}
                                        image={link.icon}
                                        alt={link.label}
                                        buttonSx={{width: 45, height: 30}}
                                        iconSx={{width: 21, height: 21}}
                                    />
                                ))}
                            </Box>
                        }
                        {(visibleSelectors.length > 0 || visiblePageNavigationButtons.length > 0) &&
                            <Box sx={{display: "flex", alignItems: "center", flexWrap: "nowrap", maxWidth: "100%", overflowX: "auto"}}>
                                {visibleSelectors.map((selector) => (
                                    <MainBarSelect
                                        key={selector.key}
                                        values={selector.values}
                                        value={selector.value}
                                        setValue={selector.setValue}
                                        valueKey={selector.valueKey}
                                        label={selector.label}
                                        companyLists={selector.companyLists}
                                        defaultCompanyList={selector.defaultCompanyList}
                                    />
                                ))}
                                {visiblePageNavigationButtons.length > 0 &&
                                    <Box sx={{display: "flex", alignItems: "center", flexShrink: 0, marginLeft: "8px"}}>
                                        {visiblePageNavigationButtons.map((button) => (
                                            <MainBarIconButton
                                                key={button.key}
                                                tooltip={button.tooltip}
                                                ariaLabel={button.ariaLabel}
                                                onClick={button.onClick}
                                                icon={button.icon}
                                                color="white"
                                                buttonSx={{width: 50, height: 30}}
                                                iconSx={{width: 23, height: 23}}
                                            />
                                        ))}
                                    </Box>
                                }
                            </Box>
                        }
                    </Box>
                    <Box sx={{ flexGrow: 1 }} />
                </Toolbar>
            </AppBar>
        </Box>
    )
}
