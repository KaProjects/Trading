import './style/App.css';
import React, {useEffect, useState} from "react";
import {BrowserRouter, Route, Routes} from "react-router-dom";
import {backend} from "./properties";
import axios from "axios";
import {wait} from "@testing-library/user-event/dist/utils";
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

const ACTIVE_STATES = ["only active", "only closed"];

const PageNotFound = () => (
    <div style={{position: "absolute", top: "25%", left: "50%", transform: "translate(-50%, -50%)"}}>
        <h2>404 Page not found</h2>
    </div>
);

export const App = () => {
    const [state, setState] = useState({
        loaded: false,
        error: null,
        companies: [],
        activeStates: ACTIVE_STATES,
        currencies: [],
        sectors: [],
        showCompanySelector: false,
        showActiveSelector: false,
        showCurrencySelector: false,
        showYearSelector: null,      // null = false, true otherwise
        showSectorSelector: false,
        showAddTradeButton: false,
        showSellTradeButton: false,
        showAddDividendButton: false,
        showAddCompanyButton: false,
        showStatsTabs: null,         // null = false, true otherwise
        activeSelectorValue: "",
        companySelectorValue: "",
        currencySelectorValue: "",
        yearSelectorValue: "",
        sectorSelectorValue: "",
        openAddTrade: false,
        openSellTrade: false,
        openAddDividend: false,
        openEditCompany: null,
        statsTabsIndex: 0,
    });

    useEffect(() => {
        axios.get(backend + "/company/values")
            .then((response) => {
                setState((prev) => ({
                    ...prev,
                    companies: response.data.companies,
                    currencies: response.data.currencies,
                    sectors: response.data.sectors,
                    error: null,
                    loaded: true,
                }));
            }).catch((error) => {
                setState((prev) => ({
                    ...prev,
                    error: formatError(error),
                    loaded: false,
                }));
            });
    }, []);

    function loadStorageStates() {
        if (sessionStorage.getItem('companyId')){
            wait(100).then(() => {
                state.companies.forEach(company => {
                    if (company.id === sessionStorage.getItem('companyId')) {
                        setCompanySelectorValue(company);
                    }
                });
                sessionStorage.removeItem('companyId');
            });
        }
        if (sessionStorage.getItem('tradeState')){
            setActiveSelectorValue(sessionStorage.getItem('tradeState'));
            sessionStorage.removeItem('tradeState');
        }
    }

    function toggleTradesSelectors(years) {
        setState((prev) => ({
            ...prev,
            showActiveSelector: true,
            showCompanySelector: true,
            showCurrencySelector: true,
            showYearSelector: years,
            yearSelectorValue: years.includes(prev.yearSelectorValue) ? prev.yearSelectorValue : "",
            showSectorSelector: true,
            showAddTradeButton: true,
            showSellTradeButton: true,
        }));
        loadStorageStates();
    }

    function toggleRecordsSelectors() {
        setState((prev) => ({
            ...prev,
            showCompanySelector: true,
        }));
        loadStorageStates();
    }

    function toggleDividendsSelectors(years) {
        setState((prev) => ({
            ...prev,
            showCompanySelector: true,
            showCurrencySelector: true,
            showYearSelector: years,
            yearSelectorValue: years.includes(prev.yearSelectorValue) ? prev.yearSelectorValue : "",
            showSectorSelector: true,
            showAddDividendButton: true,
        }));
        loadStorageStates();
    }

    function toggleStatsSelectors(years, companySelector, sectorSelector){
        setState((prev) => ({
            ...prev,
            showStatsTabs: [0,1,2],
            showCompanySelector: companySelector,
            companySelectorValue: companySelector ? prev.companySelectorValue : "",
            showYearSelector: years,
            yearSelectorValue: years && years.includes(prev.yearSelectorValue) ? prev.yearSelectorValue : "",
            showSectorSelector: sectorSelector,
            sectorSelectorValue: sectorSelector ? prev.sectorSelectorValue : "",
        }));
    }

    function toggleCompaniesSelectors() {
        setState((prev) => ({
            ...prev,
            showCurrencySelector: true,
            showSectorSelector: true,
            showAddCompanyButton: true,
        }));
    }

    function setActiveSelectorValue(value) {setState((prev) => ({...prev, activeSelectorValue: value}))}
    function setCompanySelectorValue(value) {setState((prev) => ({...prev, companySelectorValue: value}))}
    function setCurrencySelectorValue(value) {setState((prev) => ({...prev, currencySelectorValue: value}))}
    function setYearSelectorValue(value) {setState((prev) => ({...prev, yearSelectorValue: value}))}
    function setSectorSelectorValue(value) {setState((prev) => ({...prev, sectorSelectorValue: value}))}
    function setOpenAddTrade(value) {setState((prev) => ({...prev, openAddTrade: value}))}
    function setOpenSellTrade(value) {setState((prev) => ({...prev, openSellTrade: value}))}
    function setOpenAddDividend(value) {setState((prev) => ({...prev, openAddDividend: value}))}
    function setOpenEditCompany(value) {setState((prev) => ({...prev, openEditCompany: value}))}
    function setStatsTabsIndex(index) {setState((prev) => ({...prev, statsTabsIndex: index}))}

    const appProps = {
        ...state,
        toggleTradesSelectors,
        toggleRecordsSelectors,
        toggleDividendsSelectors,
        toggleStatsSelectors,
        toggleCompaniesSelectors,
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
            {!state.loaded && <Loader error={state.error}/>}
            {state.loaded &&
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
