import {Button} from "@mui/material";
import React from "react";

const MainBar = props => {


    return (
        <>
            <Button onClick={() => window.location.href='/trades'}>Trades</Button>
            <Button onClick={() => window.location.href='/records'}>Records</Button>
            <Button onClick={() => window.location.href='/dividends'}>Dividends</Button>
        </>
    )
}
export default MainBar;