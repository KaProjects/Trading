import './style/App.css';
import React, {useEffect, useState} from "react";
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

const PageNotFound = () => (
    <div style={{position: "absolute", top: "25%", left: "50%", transform: "translate(-50%, -50%)"}}>
        <h2>404 Page not found</h2>
    </div>
);

export const App = () => {
    const [loaded, setLoaded] = useState(false);
    const [error, setError] = useState(null);
    const [companies, setCompanies] = useState([]);
    const [currencies, setCurrencies] = useState([]);
    const [sectors, setSectors] = useState([]);
    const [years, setYears] = useState([]);
    const [activeSelectorValue, setActiveSelectorValue] = useState("");
    const [companySelectorValue, setCompanySelectorValue] = useState("");
    const [currencySelectorValue, setCurrencySelectorValue] = useState("");
    const [yearSelectorValue, setYearSelectorValue] = useState("");
    const [sectorSelectorValue, setSectorSelectorValue] = useState("");
    const [openAddTrade, setOpenAddTrade] = useState(false);
    const [openSellTrade, setOpenSellTrade] = useState(false);
    const [openAddDividend, setOpenAddDividend] = useState(false);
    const [openEditCompany, setOpenEditCompany] = useState(null);
    const [statsTabsIndex, setStatsTabsIndex] = useState(0);

    useEffect(() => {
        axios.get(backend + "/company/values")
            .then((response) => {
                setCompanies(response.data.companies);
                setCurrencies(response.data.currencies);
                setSectors(response.data.sectors);
                setError(null);
                setLoaded(true);
            }).catch((error) => {
                setError(formatError(error));
                setLoaded(false);
            });
    }, []);

    const appProps = {
        companies,
        currencies,
        sectors,
        years,
        activeSelectorValue,
        companySelectorValue,
        currencySelectorValue,
        yearSelectorValue,
        sectorSelectorValue,
        openAddTrade,
        openSellTrade,
        openAddDividend,
        openEditCompany,
        statsTabsIndex,
        setYears,
        setActiveSelectorValue,
        setCompanySelectorValue,
        setCurrencySelectorValue,
        setYearSelectorValue,
        setSectorSelectorValue,
        setOpenAddTrade,
        setOpenSellTrade,
        setOpenAddDividend,
        setOpenEditCompany,
        setStatsTabsIndex,
    };

    return (
        <BrowserRouter>
            <MainBar {...appProps} />
            {!loaded && <Loader error={error}/>}
            {loaded &&
                <Routes>
                    <Route exact path="/" element={<Home {...appProps}/>}/>
                    <Route exact path="/trades" element={<Trades {...appProps}/>}/>
                    <Route exact path="/research" element={<Research {...appProps}/>}/>
                    <Route exact path="/dividends" element={<Dividends {...appProps}/>}/>
                    <Route exact path="/stats" element={<Stats {...appProps}/>}/>
                    <Route exact path="/companies" element={<Companies {...appProps}/>}/>
                    <Route exact path="/analytics" element={<Analytics {...appProps}/>}/>
                    <Route path="*" element={<PageNotFound/>}/>
                </Routes>
            }
        </BrowserRouter>
    );
}
