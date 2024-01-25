import './style/App.css';
import {Component} from "react";
import {BrowserRouter, Route, Routes} from "react-router-dom";
import MainBar from "./components/MainBar";
import Home from "./views/Home";
import Trades from "./views/Trades";
import Records from "./views/Records";
import {properties} from "./properties";
import axios from "axios";

class App extends Component {
    constructor(props) {
        super(props);

        this.state = {
            companies: [],
            showCompanySelector: false,
            showActiveSelector: null,    // null = false, true otherwise
            showCurrencySelector: null,  // null = false, true otherwise
            showYearSelector: null,      // null = false, true otherwise
            showAddTradeButton: false,
            toggleTradesSelectors: this.toggleTradesSelectors.bind(this),
            toggleRecordsSelectors: this.toggleRecordsSelectors.bind(this),
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
        }

        this.toggleTradesSelectors = this.toggleTradesSelectors.bind(this);
        this.toggleRecordsSelectors = this.toggleRecordsSelectors.bind(this);
        this.setActiveSelectorValue = this.setActiveSelectorValue.bind(this);
        this.setCompanySelectorValue = this.setCompanySelectorValue.bind(this);
        this.setCurrencySelectorValue = this.setCurrencySelectorValue.bind(this);
        this.setYearSelectorValue = this.setYearSelectorValue.bind(this);
        this.setOpenAddTrade = this.setOpenAddTrade.bind(this);
    }

    toggleTradesSelectors(actives, currencies, years) {
        this.setState({showActiveSelector: actives})
        this.setState({showCompanySelector: true})
        this.setState({showCurrencySelector: currencies})
        this.setState({showYearSelector: years})
        this.setState({showAddTradeButton: true})
    }

    toggleRecordsSelectors() {
        this.setState({showCompanySelector: true})
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

    componentDidMount() {
        const url = properties.protocol + "://" + properties.host + ":" + properties.port + "/company";
        axios.get(url)
            .then((response) => {
                this.setState({companies: response.data})
            }).catch((error) => {
                console.error(error)
            })
    }

    PageNotFound() {
        return (
            <div style={{position: "absolute", top: "25%", left: "50%", transform: "translate(-50%, -50%)"}}>
                <h2>404 Page not found</h2>
            </div>
        );
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
                        <Route path="*" element={this.PageNotFound()}/>
                    </Routes>
                </BrowserRouter>
            </div>
        )
    }
}
export default App;
