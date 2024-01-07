import {Button} from "@mui/material";
import React from "react";

const MainBar = props => {


    return (
        <>
            <Button onClick={() => window.location.href='/trades'}>Trades</Button>
        </>
    )
}
export default MainBar;