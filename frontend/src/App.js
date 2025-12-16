import './style/App.css';
import React, {Component} from "react";
import {BrowserRouter, Route, Routes} from "react-router-dom";
import MainBar from "./components/MainBar";
import Home from "./views/Home";
import Trades from "./views/Trades";
import Research from "./views/Research";
import {backend} from "./properties";
import axios from "axios";
import Dividends from "./views/Dividends";
import Stats from "./views/Stats";
import Companies from "./views/Companies";
import {wait} from "@testing-library/user-event/dist/utils";
import Analytics from "./views/Analytics";
import {formatError} from "./service/FormattingService";
import Loader from "./components/Loader";

class App extends Component {
    constructor(props) {
        super(props)

        this.state = {
            loaded: false,
            error: null,
            companies: [],
            activeStates: ["only active", "only closed"],
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
            toggleTradesSelectors: this.toggleTradesSelectors.bind(this),
            toggleRecordsSelectors: this.toggleRecordsSelectors.bind(this),
            toggleDividendsSelectors: this.toggleDividendsSelectors.bind(this),
            toggleStatsSelectors: this.toggleStatsSelectors.bind(this),
            toggleCompaniesSelectors: this.toggleCompaniesSelectors.bind(this),
            activeSelectorValue: "",
            setActiveSelectorValue: this.setActiveSelectorValue.bind(this),
            companySelectorValue: "",
            setCompanySelectorValue: this.setCompanySelectorValue.bind(this),
            currencySelectorValue: "",
            setCurrencySelectorValue: this.setCurrencySelectorValue.bind(this),
            yearSelectorValue: "",
            setYearSelectorValue: this.setYearSelectorValue.bind(this),
            sectorSelectorValue: "",
            setSectorSelectorValue: this.setSectorSelectorValue.bind(this),
            openAddTrade: false,
            setOpenAddTrade: this.setOpenAddTrade.bind(this),
            openSellTrade: false,
            setOpenSellTrade: this.setOpenSellTrade.bind(this),
            openAddDividend: false,
            setOpenAddDividend: this.setOpenAddDividend.bind(this),
            openEditCompany: null,
            setOpenEditCompany: this.setOpenEditCompany.bind(this),
            statsTabsIndex: 0,
            setStatsTabsIndex: this.setStatsTabsIndex.bind(this),
        }

        this.toggleTradesSelectors = this.toggleTradesSelectors.bind(this)
        this.toggleRecordsSelectors = this.toggleRecordsSelectors.bind(this)
        this.toggleDividendsSelectors = this.toggleDividendsSelectors.bind(this)
        this.toggleStatsSelectors = this.toggleStatsSelectors.bind(this)
        this.toggleCompaniesSelectors = this.toggleCompaniesSelectors.bind(this)
        this.setActiveSelectorValue = this.setActiveSelectorValue.bind(this)
        this.setCompanySelectorValue = this.setCompanySelectorValue.bind(this)
        this.setCurrencySelectorValue = this.setCurrencySelectorValue.bind(this)
        this.setYearSelectorValue = this.setYearSelectorValue.bind(this)
        this.setSectorSelectorValue = this.setSectorSelectorValue.bind(this)
        this.setOpenAddTrade = this.setOpenAddTrade.bind(this)
        this.setOpenSellTrade = this.setOpenSellTrade.bind(this)
        this.setOpenAddDividend = this.setOpenAddDividend.bind(this)
        this.setOpenEditCompany = this.setOpenEditCompany.bind(this)
        this.setStatsTabsIndex = this.setStatsTabsIndex.bind(this)
    }

    toggleTradesSelectors(years) {
        this.setState({showActiveSelector: true})
        this.setState({showCompanySelector: true})
        this.setState({showCurrencySelector: true})
        this.setState({showYearSelector: years})
        if (!years.includes(this.state.yearSelectorValue)) this.setState({yearSelectorValue: ""})
        this.setState({showSectorSelector: true})
        this.setState({showAddTradeButton: true})
        this.setState({showSellTradeButton: true})
        this.loadStorageStates()
    }

    toggleRecordsSelectors() {
        this.setState({showCompanySelector: true})
        this.loadStorageStates()
    }

    toggleDividendsSelectors(years) {
        this.setState({showCompanySelector: true})
        this.setState({showCurrencySelector: true})
        this.setState({showYearSelector: years})
        if (!years.includes(this.state.yearSelectorValue)) this.setState({yearSelectorValue: ""})
        this.setState({showSectorSelector: true})
        this.setState({showAddDividendButton: true})
        this.loadStorageStates()
    }

    toggleStatsSelectors(years, companySelector, sectorSelector){
        this.setState({showStatsTabs: [0,1,2]})
        this.setState({showCompanySelector: companySelector})
        if (!companySelector) this.setState({companySelectorValue: ""})
        this.setState({showYearSelector: years})
        if (!years || !years.includes(this.state.yearSelectorValue)) this.setState({yearSelectorValue: ""})
        this.setState({showSectorSelector: sectorSelector})
        if (!sectorSelector) this.setState({sectorSelectorValue: ""})
    }

    toggleCompaniesSelectors() {
        this.setState({showCurrencySelector: true})
        this.setState({showSectorSelector: true})
        this.setState({showAddCompanyButton: true})
    }

    setActiveSelectorValue(value) {this.setState({activeSelectorValue: value})}
    setCompanySelectorValue(value) {this.setState({companySelectorValue: value})}
    setCurrencySelectorValue(value) {this.setState({currencySelectorValue: value})}
    setYearSelectorValue(value) {this.setState({yearSelectorValue: value})}
    setSectorSelectorValue(value) {this.setState({sectorSelectorValue: value})}
    setOpenAddTrade(value) {this.setState({openAddTrade: value})}
    setOpenSellTrade(value) {this.setState({openSellTrade: value})}
    setOpenAddDividend(value) {this.setState({openAddDividend: value})}
    setOpenEditCompany(value) {this.setState({openEditCompany: value})}
    setStatsTabsIndex(index) {this.setState({statsTabsIndex: index})}

    loadStorageStates() {
        if (sessionStorage.getItem('companyId')){
            wait(100).then(() => {
                this.state.companies.forEach(company => {if (company.id === sessionStorage.getItem('companyId')) this.setState({companySelectorValue: company})})
                sessionStorage.removeItem('companyId')
            })
        }
        if (sessionStorage.getItem('tradeState')){
            this.setActiveSelectorValue(sessionStorage.getItem('tradeState'))
            sessionStorage.removeItem('tradeState')
        }
    }

    componentDidMount() {
        axios.get(backend + "/company/values")
            .then((response) => {
                this.setState({companies: response.data.companies})
                this.setState({currencies: response.data.currencies})
                this.setState({sectors: response.data.sectors})
                this.setState({error: null})
                this.setState({loaded: true})
            }).catch((error) => {
                this.setState({error: formatError(error)})
                this.setState({loaded: false})
            })
    }

    PageNotFound() {
        return (
            <div style={{position: "absolute", top: "25%", left: "50%", transform: "translate(-50%, -50%)"}}>
                <h2>404 Page not found</h2>
            </div>
        )
    }

    render() {
        return (
            <div>
                <MainBar {...this.state} />
                {!this.state.loaded && <Loader error={this.state.error}/>}
                {this.state.loaded &&
                    <BrowserRouter>
                        <Routes>
                            <Route exact path="/" element={<Home {...this.state}/>}/>
                            <Route exact path="/trades" element={<Trades {...this.state}/>}/>
                            <Route exact path="/research" element={<Research {...this.state}/>}/>
                            <Route exact path="/dividends" element={<Dividends {...this.state}/>}/>
                            <Route exact path="/stats" element={<Stats {...this.state}/>}/>
                            <Route exact path="/companies" element={<Companies {...this.state}/>}/>
                            <Route exact path="/analytics" element={<Analytics {...this.state}/>}/>
                            <Route path="*" element={this.PageNotFound()}/>
                        </Routes>
                    </BrowserRouter>
                }
            </div>
        )
    }
}
export default App
