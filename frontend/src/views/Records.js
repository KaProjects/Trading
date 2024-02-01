import {Box, Button, Card, CardContent, Dialog, DialogActions, DialogTitle, Grid} from "@mui/material";
import React, {useEffect, useState} from "react";
import Loader from "../components/Loader";
import BorderedSection from "../components/BorderedSection";
import EditableValueBox from "../components/EditableValueBox";
import {domain} from "../properties";
import axios from "axios";
import ContentEditor from "../components/ContentEditor";
import EditableTypography from "../components/EditableTypography";
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import AddRecordDialog from "../components/AddRecordDialog";
import CompanySelector from "../components/CompanySelector";
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import {handleError, validateNumber} from "../utils";
import LatestValueBox from "../components/LatestValueBox";

function profitColor(profit){
    if (profit.startsWith("+")) return 'success.dark'
    if (profit.startsWith("-")) return 'error.dark'
    return 'text.primary'
}

const Records = props => {
    const [refresh, setRefresh] = useState("");

    useEffect(() => {
        props.toggleRecordsSelectors()
        // eslint-disable-next-line
    }, []);

    const [data, setData] = useState(null);
    const [loaded, setLoaded] = useState(false);
    const [error, setError] = useState(null);
    const [openAddRecordDialog, setOpenAddRecordDialog] = useState(false);
    const [openConfirmWatchDialog, setOpenConfirmWatchDialog] = useState(false);

    useEffect(() => {
        if (props.companySelectorValue) {
            axios.get(domain + "/record/" + props.companySelectorValue.id + (refresh ? "?refresh" + refresh : ""))
                .then((response) => {
                    setData(response.data)
                    setError(null)
                    setLoaded(true)
                }).catch((error) => {
                    setError(handleError(error))
                    setLoaded(false)
                })
        }
        // eslint-disable-next-line
    }, [props.companySelectorValue, refresh]);

    function triggerRefresh() {
        setRefresh(new Date().getTime().toString())
    }

    function handleAddRecordDialogClose() {
        triggerRefresh()
        setOpenAddRecordDialog(false)
    }

    function handleConfirmWatch() {
        const newWatching = !data.watching
        axios.put(domain + "/company", {id: data.companyId, watching: newWatching})
            .then(() => {
                triggerRefresh()
            }).catch((error) => {handleError(error)})
        setOpenConfirmWatchDialog(false)
    }

    return (
        <>
            <CompanySelector refresh={refresh} {...props}/>
            {props.companySelectorValue && !loaded && <Loader error={error}/>}
            {props.companySelectorValue && loaded && data.ticker !== undefined &&
                <Card sx={{bgcolor: 'background.paper', boxShadow: 1, borderRadius: 2, minWidth: 700, width: "max-content", margin: "10px auto 10px auto", maxHeight: "calc(100vh - 70px)", overflowY: "scroll"}}>
                    <CardContent>
                        <Box sx={{position: "relative"}}>
                            <Box sx={{color: 'text.secondary'}}>Records</Box>
                            <Box sx={{color: 'text.primary', fontSize: 34, fontWeight: 'medium'}}>
                                {data.ticker}
                            </Box>

                            <Button sx={{position: "absolute", top: "0", left: "100px"}} onClick={() => setOpenConfirmWatchDialog(true)}>
                                {data.watching && <StarIcon sx={{color: 'gold',}}/>}
                                {!data.watching && <StarBorderIcon sx={{color: 'lightgrey',}}/>}
                            </Button>
                            <Dialog open={openConfirmWatchDialog} onClose={() => setOpenConfirmWatchDialog(false)}>
                                <DialogTitle>{"Are you sure to " + (data.watching ? "unwatch" : "watch") + " the company?"}</DialogTitle>
                                <DialogActions>
                                    <Button onClick={() => setOpenConfirmWatchDialog(false)}>Cancel</Button>
                                    <Button onClick={() => handleConfirmWatch()} autoFocus>Confirm</Button>
                                </DialogActions>
                            </Dialog>

                            <Button sx={{position: "absolute", top: "0", right: "0"}} onClick={() => setOpenAddRecordDialog(true)}>
                                <ControlPointIcon sx={{color: 'lightgreen',}}/>
                            </Button>
                            <AddRecordDialog open={openAddRecordDialog}
                                             handleClose={() => handleAddRecordDialogClose()}
                                             companyId={props.companySelectorValue.id}
                            />
                        </Box>

                        {data.owns.length > 0 &&
                            <Grid container direction="row" justifyContent="flex-start" alignItems="stretch" sx={{marginBottom: "20px", marginTop: "10px"}}>
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
                            </Grid>
                        }

                        <Grid container direction="row" justifyContent="flex-start" alignItems="stretch" sx={{marginBottom: "20px", marginTop: "10px"}}>
                            <LatestValueBox label="latest price" data={data.latestPrice} suffix={data.currency}/>
                            <LatestValueBox label="latest P/E" data={data.latestPe} suffix="" sx={{marginLeft: "10px"}}/>
                            <LatestValueBox label="latest P/S" data={data.latestPs} suffix="" sx={{marginLeft: "10px"}}/>
                            <LatestValueBox label="latest DY" data={data.latestDy} suffix="%" sx={{marginLeft: "10px"}}/>
                            <LatestValueBox label="latest targets" data={data.latestTargets} suffix="" sx={{marginLeft: "10px"}}/>
                            <Box sx={{ flexGrow: 1 }} />
                            <LatestValueBox label="latest strategy" data={data.latestStrategy} suffix="" sx={{marginLeft: "10px"}}/>
                        </Grid>

                        {data.records.map((record, index) => (
                            <BorderedSection key={record.id} title={record.date} style={{color: 'text.primary'}}>

                                <Grid container direction="row" justifyContent="flex-start" alignItems="stretch">
                                    <EditableValueBox value={record.price} suffix={data.currency} label="Price"
                                                      updateObject={(value) => {return {id: record.id, price: value}}}
                                                      validateInput={(value) => validateNumber(value, false, 10, 4)}
                                                      handleUpdate={triggerRefresh}
                                    />
                                    <EditableValueBox value={record.pe} suffix={""} label="P/E ratio" style={{marginLeft: "5px"}}
                                                      updateObject={(value) => {return {id: record.id, pe: value}}}
                                                      validateInput={(value) => validateNumber(value, true, 5, 2)}
                                                      handleUpdate={triggerRefresh}
                                    />
                                    <EditableValueBox value={record.ps} suffix={""} label="P/S ratio" style={{marginLeft: "5px"}}
                                                      updateObject={(value) => {return {id: record.id, ps: value}}}
                                                      validateInput={(value) => validateNumber(value, true, 5, 2)}
                                                      handleUpdate={triggerRefresh}
                                    />
                                    <EditableValueBox value={record.dy} suffix={"%"} label="dividend yield" style={{marginLeft: "5px"}}
                                                      updateObject={(value) => {return {id: record.id, dy: value}}}
                                                      validateInput={(value) => validateNumber(value, true, 5, 2)}
                                                      handleUpdate={triggerRefresh}
                                    />
                                    <EditableValueBox value={record.targets} suffix={data.currency} label="targets" style={{marginLeft: "5px"}}
                                                      updateObject={(value) => {return {id: record.id, targets: value}}}
                                                      validateInput={(value) => ""}
                                                      handleUpdate={triggerRefresh}
                                    />
                                </Grid>

                                <EditableTypography value={record.title} label={"Title"} style={{margin: "12px auto auto 5px"}}
                                                    updateObject={(value) => {return {id: record.id, title: value}}}
                                                    validateInput={(value) => {if (value === "") return "not null"; return ""}}
                                                    handleUpdate={triggerRefresh}
                                />

                                <div style={{width: "700px", margin: "15px auto 0 auto"}}>
                                    <ContentEditor record={record} handleUpdate={triggerRefresh}/>
                                </div>

                                <EditableValueBox value={record.strategy} label="strategy" style={{marginTop: "5px"}}
                                                  updateObject={(value) => {return {id: record.id, strategy: value}}}
                                                  validateInput={(value) => ""}
                                                  handleUpdate={triggerRefresh}
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