import './style/App.css';
import React, {useCallback, useEffect, useState} from "react";
import {BrowserRouter, Route, Routes} from "react-router-dom";
import {backend} from "./properties";
import axios from "axios";
import {formatError} from "./service/FormattingService";
import {Loader} from "./views/component/Loader";
import {Research} from "./views/Research";
import {Trades} from "./views/Trades";
import {Stats} from "./views/Stats";
import {Dividends} from "./views/Dividends";
import {Companies} from "./views/Companies";
import {MainBar} from "./views/component/MainBar";
import {Analytics} from "./views/Analytics";
import {Home} from "./views/Home";
import {AdminPortfolio} from "./views/AdminPortfolio";
import {Box, ThemeProvider} from "@mui/material";
import {appTheme} from "./theme";

const PageNotFound = () => (
    <div style={{position: "absolute", top: "25%", left: "50%", transform: "translate(-50%, -50%)"}}>
        <h2>404 Page not found</h2>
    </div>
);

export const App = () => {
    const [loaded, setLoaded] = useState(false);
    const [error, setError] = useState(null);
    const [companyLists, setCompanyLists] = useState({all: []});
    const [currencies, setCurrencies] = useState([]);
    const [sectors, setSectors] = useState([]);
    const [portfolios, setPortfolios] = useState([]);
    const [years, setYears] = useState([]);
    const [activeSelectorValue, setActiveSelectorValue] = useState("");
    const [companySelectorValue, setCompanySelectorStateValue] = useState("");
    const [companyListSelectorValue, setCompanyListSelectorValue] = useState("all");
    const [currencySelectorValue, setCurrencySelectorValue] = useState("");
    const [yearSelectorValue, setYearSelectorValue] = useState("");
    const [sectorSelectorValue, setSectorSelectorValue] = useState("");
    const [portfolioSelectorValue, setPortfolioSelectorValue] = useState("");
    const [openAddTrade, setOpenAddTrade] = useState(false);
    const [openSellTrade, setOpenSellTrade] = useState(false);
    const [openAddDividend, setOpenAddDividend] = useState(false);
    const [openEditCompany, setOpenEditCompany] = useState(null);
    const [statsTabsIndex, setStatsTabsIndex] = useState(0);
    const [researchTabsIndex, setResearchTabsIndex] = useState(0);

    useEffect(() => {
        Promise.all([
            axios.get(backend + "/company/values"),
            axios.get(backend + "/company/lists"),
        ]).then(([valuesResponse, listsResponse]) => {
            setCompanyLists(listsResponse.data);
            setCurrencies(valuesResponse.data.currencies);
            setSectors(valuesResponse.data.sectors);
            setPortfolios(valuesResponse.data.portfolios ?? []);
            setYears(valuesResponse.data.years ?? []);
            setError(null);
            setLoaded(true);
        }).catch((error) => {
            setError(formatError(error));
            setLoaded(false);
        });
    }, []);

    function refreshCompanyLists() {
        axios.get(backend + "/company/lists?refresh" + Date.now())
            .then(response => setCompanyLists(response.data))
            .catch(requestError => setError(formatError(requestError)));
    }

    const setCompanySelectorValue = useCallback((company) => {
        setCompanySelectorStateValue(company);
        if (company?.id) {
            setCurrencySelectorValue("");
            setSectorSelectorValue("");
        }
    }, []);

    const props = {
        companyLists,
        refreshCompanyLists,
        currencies,
        sectors,
        portfolios,

        years,

        activeSelectorValue,
        setActiveSelectorValue,
        companySelectorValue,
        setCompanySelectorValue,
        companyListSelectorValue,
        setCompanyListSelectorValue,
        currencySelectorValue,
        setCurrencySelectorValue,
        yearSelectorValue,
        setYearSelectorValue,
        sectorSelectorValue,
        setSectorSelectorValue,
        portfolioSelectorValue,
        setPortfolioSelectorValue,

        openAddTrade,
        setOpenAddTrade,
        openSellTrade,
        setOpenSellTrade,
        openAddDividend,
        setOpenAddDividend,
        openEditCompany,
        setOpenEditCompany,

        statsTabsIndex,
        setStatsTabsIndex,
        researchTabsIndex,
        setResearchTabsIndex,
    };

    return (
        <ThemeProvider theme={appTheme}>
            <BrowserRouter>
                <MainBar {...props} />
                <Box sx={{
                    pt: "var(--main-bar-height, 48px)",
                    px: {xs: 1, sm: 2},
                    pb: {xs: 1, sm: 2},
                }}>
                    {!loaded && <Loader error={error}/>}
                    {loaded &&
                        <Routes>
                            <Route exact path="/" element={<Home {...props}/>}/>
                            <Route exact path="/trades" element={<Trades {...props}/>}/>
                            <Route exact path="/research" element={<Research {...props}/>}/>
                            <Route exact path="/dividends" element={<Dividends {...props}/>}/>
                            <Route exact path="/stats" element={<Stats {...props}/>}/>
                            <Route exact path="/companies" element={<Companies {...props}/>}/>
                            <Route exact path="/analytics" element={<Analytics {...props}/>}/>
                            <Route exact path="/admin/portfolio" element={<AdminPortfolio {...props}/>}/>
                            <Route path="*" element={<PageNotFound/>}/>
                        </Routes>
                    }
                </Box>
            </BrowserRouter>
        </ThemeProvider>
    );
}
