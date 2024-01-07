import './App.css';
import {Component} from "react";
import {BrowserRouter, Route, Routes} from "react-router-dom";
import MainBar from "./components/MainBar";
import Home from "./views/Home";
import Trades from "./views/Trades";

class App extends Component {
    constructor(props) {
        super(props);

        this.state = {
            showActiveSelector: false,
            toggleActiveSelector: this.toggleActiveSelector.bind(this),
            activeSelectorValue: false,
            toggleActiveSelectorValue: this.toggleActiveSelectorValue.bind(this),
        }

        this.toggleActiveSelector = this.toggleActiveSelector.bind(this);
        this.toggleActiveSelectorValue = this.toggleActiveSelectorValue.bind(this);
    }

    toggleActiveSelector() {
        this.setState({showActiveSelector: !this.state.showActiveSelector})
    }

    toggleActiveSelectorValue() {
        this.setState({activeSelectorValue: !this.state.activeSelectorValue})
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
                        <Route path="*" element={this.PageNotFound()}/>
                    </Routes>
                </BrowserRouter>
            </div>
        )
    }
}

export default App;
