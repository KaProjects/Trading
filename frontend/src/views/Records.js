import {Box, Button, Card, CardContent, Grid, Typography} from "@mui/material";
import React, {useEffect, useState} from "react";
import Loader from "../components/Loader";
import BorderedSection from "../components/BorderedSection";
import EditableValueBox from "../components/EditableValueBox";
import {properties} from "../properties";
import axios from "axios";
import ContentEditor from "../components/ContentEditor";
import EditableTypography from "../components/EditableTypography";
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import AddRecordDialog from "../components/AddRecordDialog";
import CompanySelector from "../components/CompanySelector";

function profitColor(profit){
    if (profit.startsWith("+")) return 'success.dark'
    if (profit.startsWith("-")) return 'error.dark'
    return 'text.primary'
}

function validateNumber(value, isNullable, lengthConstraint, decimalConstraint) {
    if (typeof value != "string") return "not a string"
    if (value === "") return isNullable ? "" : "not null"
    if (isNaN(value) || isNaN(parseFloat(value)) || value.endsWith(".")) return "not a valid number";
    if (value.replace(".", "").length > lengthConstraint) return "max length " + lengthConstraint;
    const split = value.split(".")
    if (split.length > 1 && split[1].length > decimalConstraint) return "max decimal " + decimalConstraint
    return ""
}

const Records = props => {

    useEffect(() => {
        props.toggleRecordsSelectors()
        // eslint-disable-next-line
    }, []);

    const [data, setData] = useState(null);
    const [loaded, setLoaded] = useState(false);
    const [error, setError] = useState(null);
    const [openAddRecordDialog, setOpenAddRecordDialog] = useState(false);

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

    function handleAddRecordDialogClose(record) {
        if (record) data.records.unshift(record)
        setOpenAddRecordDialog(false)
    }

    return (
        <>
            {!props.companySelectorValue && <CompanySelector {...props}/>}
            {props.companySelectorValue && !loaded && <Loader error={error}/>}
            {props.companySelectorValue && loaded && data.ticker !== undefined &&
                <Card sx={{bgcolor: 'background.paper', boxShadow: 1, borderRadius: 2, minWidth: 700, width: "max-content", margin: "10px auto 10px auto", maxHeight: "calc(100vh - 70px)", overflowY: "scroll"}}>
                    <CardContent>
                        <Box sx={{position: "relative"}}>
                            <Box sx={{color: 'text.secondary'}}>Records</Box>
                            <Box sx={{color: 'text.primary', fontSize: 34, fontWeight: 'medium'}}>
                                {data.ticker}
                            </Box>

                            <Button sx={{position: "absolute", top: "0", right: "0"}} onClick={() => setOpenAddRecordDialog(true)}>
                                <ControlPointIcon sx={{color: 'lightgreen',}}/>
                            </Button>
                            <AddRecordDialog open={openAddRecordDialog}
                                             handleClose={(record) => handleAddRecordDialogClose(record)}
                                             companyId={props.companySelectorValue.id}
                                             validatePrice={(value) => validateNumber(value, false, 10, 4)}
                            />
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
                            <BorderedSection key={index + record.id} title={record.date} style={{color: 'text.primary'}}>

                                <Grid container direction="row" justifyContent="flex-start" alignItems="stretch">
                                    <EditableValueBox index={index} value={record.price} suffix={data.currency} label="Price"
                                                      updateObject={(value) => {return {id: record.id, price: value}}}
                                                      validateInput={(value) => validateNumber(value, false, 10, 4)}
                                                      handleUpdate={(value) => record.price = value}
                                    />
                                    <EditableValueBox index={index} value={record.pe} suffix={""} label="P/E ratio" style={{marginLeft: "5px"}}
                                                      updateObject={(value) => {return {id: record.id, pe: value}}}
                                                      validateInput={(value) => validateNumber(value, true, 5, 2)}
                                                      handleUpdate={(value) => record.pe = value}
                                    />
                                    <EditableValueBox index={index} value={record.ps} suffix={""} label="P/S ratio" style={{marginLeft: "5px"}}
                                                      updateObject={(value) => {return {id: record.id, ps: value}}}
                                                      validateInput={(value) => validateNumber(value, true, 5, 2)}
                                                      handleUpdate={(value) => record.ps = value}
                                    />
                                    <EditableValueBox index={index} value={record.dy} suffix={"%"} label="dividend yield" style={{marginLeft: "5px"}}
                                                      updateObject={(value) => {return {id: record.id, dy: value}}}
                                                      validateInput={(value) => validateNumber(value, true, 5, 2)}
                                                      handleUpdate={(value) => record.dy = value}
                                    />
                                    <EditableValueBox index={index} value={record.targets} suffix={data.currency} label="targets" style={{marginLeft: "5px"}}
                                                      updateObject={(value) => {return {id: record.id, targets: value}}}
                                                      validateInput={(value) => ""}
                                                      handleUpdate={(value) => record.targets = value}
                                    />
                                </Grid>

                                <EditableTypography index={index} value={record.title} label={"Title"} style={{margin: "12px auto auto 5px"}}
                                                    updateObject={(value) => {return {id: record.id, title: value}}}
                                                    validateInput={(value) => {if (value === "") return "not null"; return ""}}
                                                    handleUpdate={(value) => record.title = value}
                                />

                                <div style={{width: "700px", margin: "15px auto 0 auto"}}>
                                    <ContentEditor index={index} record={record}
                                                   handleUpdate={(value) => record.content = JSON.stringify(value)}/>
                                </div>

                                <EditableValueBox index={index} value={record.strategy} label="strategy" style={{marginTop: "5px"}}
                                                  updateObject={(value) => {return {id: record.id, strategy: value}}}
                                                  validateInput={(value) => ""}
                                                  handleUpdate={(value) => record.strategy = value}
                                />

                            </BorderedSection>
                        ))}
                    </CardContent>
                </Card>
            }
        </>
    )
}
export default Records;