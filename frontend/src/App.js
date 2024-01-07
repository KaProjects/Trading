import './App.css';
import {Component} from "react";
import {BrowserRouter, Route, Routes} from "react-router-dom";
import MainBar from "./components/MainBar";

class App extends Component {
  constructor(props) {
    super(props);
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
                        {/*<Route exact path="/" element={<Home {...this.state}/> }/>*/}
                        <Route path="*" element={this.PageNotFound()} />
                    </Routes>
                </BrowserRouter>
            </div>
        )
    }
}

export default App;
