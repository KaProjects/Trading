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
            showActiveSelector: false,
            showCompanySelector: false,
            showCurrencySelector: null,  // null = false, true otherwise
            showYearSelector: null,      // null = false, true otherwise
            toggleTradesSelectors: this.toggleTradesSelectors.bind(this),
            toggleRecordsSelectors: this.toggleRecordsSelectors.bind(this),
            activeSelectorValue: false,
            toggleActiveSelectorValue: this.toggleActiveSelectorValue.bind(this),
            companySelectorValue: "",
            setCompanySelectorValue: this.setCompanySelectorValue.bind(this),
            currencySelectorValue: "",
            setCurrencySelectorValue: this.setCurrencySelectorValue.bind(this),
            yearSelectorValue: "",
            setYearSelectorValue: this.setYearSelectorValue.bind(this),
        }

        this.toggleTradesSelectors = this.toggleTradesSelectors.bind(this);
        this.toggleRecordsSelectors = this.toggleRecordsSelectors.bind(this);
        this.toggleActiveSelectorValue = this.toggleActiveSelectorValue.bind(this);
        this.setCompanySelectorValue = this.setCompanySelectorValue.bind(this);
        this.setCurrencySelectorValue = this.setCurrencySelectorValue.bind(this);
        this.setYearSelectorValue = this.setYearSelectorValue.bind(this);
    }

    toggleTradesSelectors(currencies, years) {
        this.setState({showActiveSelector: true})
        this.setState({showCompanySelector: true})
        this.setState({showCurrencySelector: currencies})
        this.setState({showYearSelector: years})
    }

    toggleRecordsSelectors() {
        this.setState({showCompanySelector: true})
    }

    toggleActiveSelectorValue() {
        this.setState({activeSelectorValue: !this.state.activeSelectorValue})
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
