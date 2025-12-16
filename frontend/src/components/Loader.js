import {Alert, AlertTitle, CircularProgress} from "@mui/material";
import React from "react";

const Loader = props => {
    const {error} = props
    return (
        <div style={{ display: "flex", justifyContent: "center", alignItems: "center", height: "100vh"}}>
            {error === null
                ? <CircularProgress/>
                : <Alert severity="error"><AlertTitle>{error.title}</AlertTitle>{error.message}</Alert> }
        </div>
    )
}
export default Loader