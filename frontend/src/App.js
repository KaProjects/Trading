import './style/App.css';
import {Component} from "react";
import {BrowserRouter, Route, Routes} from "react-router-dom";
import MainBar from "./components/MainBar";
import Home from "./views/Home";
import Trades from "./views/Trades";
import Records from "./views/Records";
import {domain} from "./properties";
import axios from "axios";
import {handleError} from "./utils";
import Dividends from "./views/Dividends";

class App extends Component {
    constructor(props) {
        super(props)

        this.state = {
            companies: [],
            showCompanySelector: false,
            showActiveSelector: null,    // null = false, true otherwise
            showCurrencySelector: null,  // null = false, true otherwise
            showYearSelector: null,      // null = false, true otherwise
            showAddTradeButton: false,
            showSellTradeButton: false,
            showAddDividendButton: false,
            toggleTradesSelectors: this.toggleTradesSelectors.bind(this),
            toggleRecordsSelectors: this.toggleRecordsSelectors.bind(this),
            toggleDividendsSelectors: this.toggleDividendsSelectors.bind(this),
            activeSelectorValue: "",
            setActiveSelectorValue: this.setActiveSelectorValue.bind(this),
            companySelectorValue: "",
            setCompanySelectorValue: this.setCompanySelectorValue.bind(this),
            currencySelectorValue: "",
            setCurrencySelectorValue: this.setCurrencySelectorValue.bind(this),
            yearSelectorValue: "",
            setYearSelectorValue: this.setYearSelectorValue.bind(this),
            openAddTrade: false,
            setOpenAddTrade: this.setOpenAddTrade.bind(this),
            openSellTrade: false,
            setOpenSellTrade: this.setOpenSellTrade.bind(this),
            openAddDividend: false,
            setOpenAddDividend: this.setOpenAddDividend.bind(this),
        }

        this.toggleTradesSelectors = this.toggleTradesSelectors.bind(this)
        this.toggleRecordsSelectors = this.toggleRecordsSelectors.bind(this)
        this.toggleDividendsSelectors = this.toggleDividendsSelectors.bind(this)
        this.setActiveSelectorValue = this.setActiveSelectorValue.bind(this)
        this.setCompanySelectorValue = this.setCompanySelectorValue.bind(this)
        this.setCurrencySelectorValue = this.setCurrencySelectorValue.bind(this)
        this.setYearSelectorValue = this.setYearSelectorValue.bind(this)
        this.setOpenAddTrade = this.setOpenAddTrade.bind(this)
        this.setOpenSellTrade = this.setOpenSellTrade.bind(this)
        this.setOpenAddDividend = this.setOpenAddDividend.bind(this)
    }

    toggleTradesSelectors(actives, currencies, years) {
        this.setState({showActiveSelector: actives})
        this.setState({showCompanySelector: true})
        this.setState({showCurrencySelector: currencies})
        this.setState({showYearSelector: years})
        this.setState({showAddTradeButton: true})
        this.setState({showSellTradeButton: true})
    }

    toggleRecordsSelectors() {
        this.setState({showCompanySelector: true})
    }

    toggleDividendsSelectors(currencies, years) {
        this.setState({showCompanySelector: true})
        this.setState({showCurrencySelector: currencies})
        this.setState({showYearSelector: years})
        this.setState({showAddDividendButton: true})
    }

    setActiveSelectorValue(value) {
        this.setState({activeSelectorValue: value})
    }

    setCompanySelectorValue(value) {
        this.setState({companySelectorValue: value})
    }

    setCurrencySelectorValue(value) {
        this.setState({currencySelectorValue: value})
    }

    setYearSelectorValue(value) {
        this.setState({yearSelectorValue: value})
    }

    setOpenAddTrade(value) {
        this.setState({openAddTrade: value})
    }

    setOpenSellTrade(value) {
        this.setState({openSellTrade: value})
    }

    setOpenAddDividend(value) {
        this.setState({openAddDividend: value})
    }

    componentDidMount() {
        axios.get(domain + "/company")
            .then((response) => {
                this.setState({companies: response.data})
            }).catch((error) => {handleError(error)})
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
                <BrowserRouter>
                    <Routes>
                        <Route exact path="/" element={<Home {...this.state}/>}/>
                        <Route exact path="/trades" element={<Trades {...this.state}/>}/>
                        <Route exact path="/records" element={<Records {...this.state}/>}/>
                        <Route exact path="/dividends" element={<Dividends {...this.state}/>}/>
                        <Route path="*" element={this.PageNotFound()}/>
                    </Routes>
                </BrowserRouter>
            </div>
        )
    }
}
export default App;
