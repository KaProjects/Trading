import {Box, Card, CardContent, Grid, TextField, Typography} from "@mui/material";
import React, {useEffect, useState} from "react";
import Loader from "../components/Loader";
import BorderedSection from "../components/BorderedSection";
import EditableValueBox from "../components/EditableValueBox";
import {properties} from "../properties";
import axios from "axios";

function profitColor(profit){
    if (profit.startsWith("+")) return 'success.dark'
    if (profit.startsWith("-")) return 'error.dark'
    return 'text.primary'
}

const Records = props => {

    useEffect(() => {
        props.toggleRecordsSelectors()
        // eslint-disable-next-line
    }, []);

    const [data, setData] = useState(null);
    const [loaded, setLoaded] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (props.companySelectorValue) {
            const url = properties.protocol + "://" + properties.host + ":" + properties.port + "/record/" + props.companySelectorValue.id;
            axios.get(url)
                .then((response) => {
                    setData(response.data)
                    setError(null)
                    setLoaded(true)
                }).catch((error) => {
                console.error(error)
                setError(error)
                setLoaded(false)
            })
        }
        // eslint-disable-next-line
    }, [props.companySelectorValue]);

    return (
        <>
            {!props.companySelectorValue && <Typography style={{display: "grid", placeContent: "center", position: "absolute", left: 0, top: 50, bottom: 0, right: 0}}>select a company</Typography>}
            {props.companySelectorValue && !loaded && <Loader error={error}/>}
            {props.companySelectorValue && loaded && data.ticker !== undefined &&
                <Card sx={{bgcolor: 'background.paper', boxShadow: 1, borderRadius: 2, minWidth: 700, width: "max-content", margin: "10px auto 10px auto", maxHeight: "calc(100vh - 70px)", overflowY: "scroll"}}>
                    <CardContent>
                        <Box sx={{color: 'text.secondary'}}>Records</Box>
                        <Box sx={{color: 'text.primary', fontSize: 34, fontWeight: 'medium'}}>
                            {data.ticker}
                        </Box>

                        <Grid container direction="row" justifyContent="flex-start" alignItems="stretch" sx={{marginBottom: "20px", marginTop: "10px"}}>
                            <Box>
                                <Box sx={{color: 'text.primary', fontSize: 16, textAlign: "center", fontFamily: "Roboto",}}>
                                    {data.lastPrice && data.lastPrice + data.currency}
                                    {!data.lastPrice && "-"}
                                </Box>
                                <Box sx={{color: 'lightgrey', fontWeight: 'bold', mx: 0.5, fontSize: 12, textAlign: "center"}}>
                                    latest price
                                </Box>
                            </Box>
                            <Box sx={{flexGrow: 1}}/>
                            {data.owns.map((own, index) => (
                                <Box key={index} sx={{marginLeft: "10px"}}>
                                    <Box sx={{color: profitColor(own.profit), fontWeight: 'bold', mx: 0.5, fontSize: 12, textAlign: "center"}}>
                                        {own.profit && own.profit}
                                    </Box>
                                    <Box sx={{color: 'text.secondary', fontSize: 16, fontFamily: "Roboto",}}>
                                        {own.quantity}@{own.price}{data.currency}
                                    </Box>
                                </Box>
                            ))}

                            <Box sx={{flexGrow: 1}}/>

                            <Box>
                                <Box sx={{color: 'text.primary', fontSize: 16, textAlign: "center", fontFamily: "Roboto",}}>
                                    {data.lastStrategy && data.lastStrategy}
                                    {!data.lastStrategy && "-"}
                                </Box>
                                <Box sx={{color: 'lightgrey', fontWeight: 'bold', mx: 0.5, fontSize: 12, textAlign: "center"}}>
                                    latest strategy
                                </Box>
                            </Box>
                        </Grid>

                        {data.records.map((record, index) => (
                            <BorderedSection key={index} title={record.date} style={{color: 'text.primary'}}>

                                <Grid container direction="row" justifyContent="flex-start" alignItems="stretch">
                                    <EditableValueBox value={record.price} suffix={"$"} label="Price"/>
                                    <EditableValueBox value={record.pe} suffix={""} label="P/E ratio" style={{marginLeft: "5px"}}/>
                                    <EditableValueBox value={record.dy} suffix={"%"} label="dividend yield" style={{marginLeft: "5px"}}/>
                                    <EditableValueBox value={record.targets} suffix={"$"} label="targets" style={{marginLeft: "5px"}}/>
                                </Grid>

                                <Box sx={{color: 'text.primary', fontWeight: 'medium', fontSize: 20, margin: "12px auto auto 5px"}}>
                                    {record.title}
                                </Box>

                                <TextField multiline maxRows={4} placeholder={"write content"} value={record.content ? record.content : ""} fullWidth sx={{marginTop: "5px", border: "1px solid lightgrey", boxShadow: "0px 1px 1px lightgrey"}}/>

                                <EditableValueBox value={record.strategy} label="strategy" style={{marginTop: "5px"}}/>

                            </BorderedSection>

                        ))}
                    </CardContent>
                </Card>
            }
        </>
    )
}
export default Records;