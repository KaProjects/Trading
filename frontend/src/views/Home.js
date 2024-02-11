import {Button} from "@mui/material";
import React from "react";

const Home = props => {

    return (
        <>
            <Button onClick={() => window.location.href='/trades'}>Trades</Button>
            <Button onClick={() => window.location.href='/records'}>Records</Button>
            <Button onClick={() => window.location.href='/dividends'}>Dividends</Button>
            <Button onClick={() => window.location.href='/stats'}>Stats</Button>
        </>
    )
}
export default Home;