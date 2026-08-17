import React, {useEffect, useLayoutEffect, useRef} from "react";
import {AppBar, Box, IconButton, Tab, Tabs, Toolbar, Typography} from "@mui/material";
import MenuIcon from '@mui/icons-material/Menu';
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import RemoveCircleOutlineIcon from '@mui/icons-material/RemoveCircleOutline';
import {MainBarSelect} from "./MainBarSelect";
import {MainBarIconButton} from "./MainBarIconButton";
import {useLocation, useNavigate} from "react-router-dom";
import {ReactComponent as TradesRedirectIcon} from "../../assets/icons/trades-redirect.svg";
import {ReactComponent as DividendsRedirectIcon} from "../../assets/icons/dividends-redirect.svg";
import {ReactComponent as ResearchRedirectIcon} from "../../assets/icons/research-redirect.svg";
import {getCompanyListKeys} from "../../service/CompanyListService";

export const ACTIVE_STATES = ["only active", "only closed"];
export const RESEARCH_TAB = {
    research: 0,
    records: 1,
};
export const RESEARCH_SPLIT_BREAKPOINT = 2000;
const STATS_TABS = ["Companies", "Monthly", "Quarterly", "Yearly"];
const RESEARCH_TAB_LABELS = ["Research", "Records"];
const DATA_ROUTES = ["/trades", "/dividends", "/research"];
const COMPANY_QUERY_PARAMETER = "company";
const COMPANY_LIST_QUERY_PARAMETER = "list";
const DEFAULT_COMPANY_LIST = "all";

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
    const selectedCompanyRef = useRef(props.companySelectorValue)
    const selectedCompanyListRef = useRef(props.companyListSelectorValue)
    const companySelectionFromUrl = useRef(false)
    const companyListSelectionFromUrl = useRef(false)
    const waitingForCompanyLists = useRef(false)
    const waitingForCompanyListOptions = useRef(false)
    const companies = props.companyLists?.all ?? []
    const companyTickers = companies.map(company => company.ticker).join(",")
    const companyListKeys = getCompanyListKeys(props.companyLists)
    const companyListKeysSignature = JSON.stringify(companyListKeys)
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

    useLayoutEffect(() => {
        selectedCompanyRef.current = props.companySelectorValue
        selectedCompanyListRef.current = props.companyListSelectorValue
    }, [props.companySelectorValue, props.companyListSelectorValue])

    function searchWithCompany(ticker, search = location.search) {
        const searchParams = new URLSearchParams(search ?? "")
        if (ticker) {
            searchParams.set(COMPANY_QUERY_PARAMETER, ticker)
        } else {
            searchParams.delete(COMPANY_QUERY_PARAMETER)
        }

        const query = searchParams.toString()
        return query ? `?${query}` : ""
    }

    function searchWithCompanyList(listKey, search = location.search) {
        const searchParams = new URLSearchParams(search ?? "")
        if (listKey && listKey !== DEFAULT_COMPANY_LIST) {
            searchParams.set(COMPANY_LIST_QUERY_PARAMETER, listKey)
        } else {
            searchParams.delete(COMPANY_LIST_QUERY_PARAMETER)
        }

        const query = searchParams.toString()
        return query ? `?${query}` : ""
    }

    function searchWithResearchSelection(ticker, listKey, search = location.search) {
        return searchWithCompanyList(listKey, searchWithCompany(ticker, search))
    }

    function loadNavigationState() {
        if (!location.state) {
            return
        }
        const remainingState = {...location.state}
        let nextSearch = location.search ?? ""
        let consumed = false

        if (location.state.companyId) {
            const company = companies.find(company => company.id === location.state.companyId)
            if (company) {
                props.setCompanySelectorValue(company);
                nextSearch = searchWithCompany(company.ticker)
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
            navigate({
                pathname: location.pathname,
                search: nextSearch,
                hash: location.hash,
            }, {replace: true, state: remainingState})
        }
    }

    useEffect(() => {
        if (["/trades", "/research", "/dividends"].includes(location.pathname)) {
            loadNavigationState();
        }
        // eslint-disable-next-line
    }, [location.pathname, location.state, props.companyLists, props.sectors]);

    useEffect(() => {
        if (!DATA_ROUTES.includes(location.pathname)) {
            companySelectionFromUrl.current = false
            waitingForCompanyLists.current = false
            return
        }

        const searchParams = new URLSearchParams(location.search ?? "")
        const ticker = searchParams.get(COMPANY_QUERY_PARAMETER)
        const selectedTicker = selectedCompanyRef.current?.ticker ?? ""

        if (!ticker) {
            waitingForCompanyLists.current = false
            const blankParameterPresent = searchParams.has(COMPANY_QUERY_PARAMETER)
            companySelectionFromUrl.current = Boolean(selectedTicker) || blankParameterPresent

            if (selectedTicker) {
                props.setCompanySelectorValue("")
            }
            if (blankParameterPresent) {
                navigate({
                    pathname: location.pathname,
                    search: searchWithCompany(""),
                    hash: location.hash,
                }, {replace: true, state: location.state})
            }
            return
        }

        if (companies.length === 0) {
            waitingForCompanyLists.current = true
            return
        }

        waitingForCompanyLists.current = false
        const company = companies.find(company => company.ticker.toLowerCase() === ticker.toLowerCase())

        if (!company) {
            companySelectionFromUrl.current = true
            if (selectedTicker) {
                props.setCompanySelectorValue("")
            }
            navigate({
                pathname: location.pathname,
                search: searchWithCompany(""),
                hash: location.hash,
            }, {replace: true, state: location.state})
            return
        }

        const selectionChanged = selectedTicker !== company.ticker
        const queryNeedsCanonicalTicker = ticker !== company.ticker
        companySelectionFromUrl.current = selectionChanged || queryNeedsCanonicalTicker

        if (selectionChanged) {
            props.setCompanySelectorValue(company)
        }
        if (queryNeedsCanonicalTicker) {
            navigate({
                pathname: location.pathname,
                search: searchWithCompany(company.ticker),
                hash: location.hash,
            }, {replace: true, state: location.state})
        }
        // URL changes drive this effect; selectedCompanyRef supplies the current state without reversing user selections.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [location.pathname, location.search, companyTickers])

    useEffect(() => {
        if (!DATA_ROUTES.includes(location.pathname) || waitingForCompanyLists.current) {
            return
        }
        if (companySelectionFromUrl.current) {
            companySelectionFromUrl.current = false
            return
        }

        const ticker = props.companySelectorValue?.ticker ?? ""
        const tickerFromUrl = new URLSearchParams(location.search ?? "").get(COMPANY_QUERY_PARAMETER) ?? ""
        if (ticker === tickerFromUrl) {
            return
        }

        navigate({
            pathname: location.pathname,
            search: location.pathname === "/research"
                ? searchWithResearchSelection(ticker, props.companyListSelectorValue)
                : searchWithCompany(ticker),
            hash: location.hash,
        }, {state: location.state})
        // Company selection changes drive this effect; URL changes are handled by the preceding effect.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [location.pathname, props.companySelectorValue?.ticker])

    useEffect(() => {
        if (location.pathname !== "/research") {
            companyListSelectionFromUrl.current = false
            waitingForCompanyListOptions.current = false
            return
        }

        if (companies.length === 0) {
            waitingForCompanyListOptions.current = true
            return
        }

        waitingForCompanyListOptions.current = false
        const searchParams = new URLSearchParams(location.search ?? "")
        const listParameterPresent = searchParams.has(COMPANY_LIST_QUERY_PARAMETER)
        const listFromUrl = searchParams.get(COMPANY_LIST_QUERY_PARAMETER) || DEFAULT_COMPANY_LIST
        const selectedList = selectedCompanyListRef.current || DEFAULT_COMPANY_LIST

        if (!companyListKeys.includes(listFromUrl)) {
            companyListSelectionFromUrl.current = selectedList !== DEFAULT_COMPANY_LIST || listParameterPresent
            if (selectedList !== DEFAULT_COMPANY_LIST) {
                props.setCompanyListSelectorValue(DEFAULT_COMPANY_LIST)
            }
            if (listParameterPresent) {
                navigate({
                    pathname: location.pathname,
                    search: searchWithCompanyList(DEFAULT_COMPANY_LIST),
                    hash: location.hash,
                }, {replace: true, state: location.state})
            }
            return
        }

        const selectionChanged = selectedList !== listFromUrl
        companyListSelectionFromUrl.current = selectionChanged
        if (selectionChanged) {
            props.setCompanyListSelectorValue(listFromUrl)
        }
        // URL changes drive this effect; selectedCompanyListRef prevents stale state from reversing browser navigation.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [location.pathname, location.search, companyListKeysSignature, companyTickers])

    useEffect(() => {
        if (location.pathname !== "/research" || waitingForCompanyListOptions.current) {
            return
        }
        if (companyListSelectionFromUrl.current) {
            companyListSelectionFromUrl.current = false
            return
        }

        const selectedList = props.companyListSelectorValue || DEFAULT_COMPANY_LIST
        const listFromUrl = new URLSearchParams(location.search ?? "")
            .get(COMPANY_LIST_QUERY_PARAMETER) || DEFAULT_COMPANY_LIST
        if (selectedList === listFromUrl || !companyListKeys.includes(selectedList)) {
            return
        }

        navigate({
            pathname: location.pathname,
            search: searchWithResearchSelection(props.companySelectorValue?.ticker, selectedList),
            hash: location.hash,
        }, {state: location.state})
        // Company-list selection changes drive this effect; URL changes are handled by the preceding effect.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [location.pathname, props.companyListSelectorValue])

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
        const listFromUrl = new URLSearchParams(location.search ?? "")
            .get(COMPANY_LIST_QUERY_PARAMETER)

        navigate({
            pathname: path,
            search: searchWithResearchSelection(
                props.companySelectorValue?.ticker,
                listFromUrl || props.companyListSelectorValue,
                "",
            ),
        }, {
            state: {
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
            companyListValue: props.companyListSelectorValue,
            setCompanyListValue: props.setCompanyListSelectorValue,
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
                                  sx={{
                                      display: "none",
                                      [`@media (max-width:${RESEARCH_SPLIT_BREAKPOINT}px)`]: {display: "flex"},
                                  }}
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
                                        companyListValue={selector.companyListValue}
                                        setCompanyListValue={selector.setCompanyListValue}
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
