import {Box, Button, Card, CardContent, Dialog, DialogActions, DialogTitle, Grid, Typography} from "@mui/material";
import React, {useEffect, useState} from "react";
import Loader from "../components/Loader";
import BorderedSection from "../components/BorderedSection";
import EditableValueBox from "../components/EditableValueBox";
import {backend} from "../properties";
import axios from "axios";
import ContentEditor from "../components/ContentEditor";
import EditableTypography from "../components/EditableTypography";
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import AddRecordDialog from "../dialog/AddRecordDialog";
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import {handleError, validateNumber} from "../utils";
import LatestValueBox from "../components/LatestValueBox";
import PeriodFinancials from "../components/PeriodFinancials";
import AddPeriodDialog from "../dialog/AddPeriodDialog";
import RecordFinancials from "../components/RecordFinancials";
import AddPeriodFinancialDialog from "../dialog/AddPeriodFinancialDialog";
import Tooltip from "@mui/material/Tooltip";


function profitColor(profit){
    if (profit.startsWith("+")) return 'success.dark'
    if (profit.startsWith("-")) return 'error.dark'
    return 'text.primary'
}

const Records = props => {
    const [refresh, setRefresh] = useState("")

    useEffect(() => {
        props.toggleRecordsSelectors()
        // eslint-disable-next-line
    }, [])

    const [dataOld, setDataOld] = useState(null)
    const [data, setData] = useState(null)
    const [loaded, setLoaded] = useState(false)
    const [error, setError] = useState(null)
    const [openAddRecordDialog, setOpenAddRecordDialog] = useState(false)
    const [openAddPeriodDialog, setOpenAddPeriodDialog] = useState(false)
    const [openConfirmWatchDialog, setOpenConfirmWatchDialog] = useState(false)
    const [expandFinancials, setExpandFinancials] = useState(false)
    const [openAddFinancialDialog, setOpenAddFinancialDialog] = useState(null)

    function fetchData(companyChanged) {
        if (props.companySelectorValue) {
            axios.get(backend + "/record/" + props.companySelectorValue.id + (refresh ? "?refresh" + refresh : ""))
                .then((response) => {
                    setDataOld(response.data)
                    setError(null)


                    axios.get(backend + "/research/" + props.companySelectorValue.id + (refresh ? "?refresh" + refresh : ""))
                        .then((response) => {
                            setData(response.data)
                            setError(null)
                            setLoaded(true)
                        }).catch((error) => {
                        setError(handleError(error))
                        setLoaded(false)
                    })


                    if (companyChanged) setExpandFinancials(false)
                    if (sessionStorage.getItem('showFinancials')){
                        setExpandFinancials(true)
                        sessionStorage.removeItem('showFinancials')
                    }
                }).catch((error) => {
                setError(handleError(error))
                setLoaded(false)
            })
        }
    }

    useEffect(() => {
        fetchData(true)
        // eslint-disable-next-line
    }, [props.companySelectorValue])

    useEffect(() => {
        fetchData(false)
        // eslint-disable-next-line
    }, [refresh])

    function triggerRefresh() {
        setRefresh(new Date().getTime().toString())
    }

    function recordUpdated(recordId, content) {
        axios.put(backend + "/record", {id: recordId, content: JSON.stringify(content)})
            .then((response) => {
                triggerRefresh()
            }).catch((error) => {handleError(error)})
    }

    function periodUpdated(periodId, content) {
        axios.put(backend + "/period", {id: periodId, research: JSON.stringify(content)})
            .then((response) => {
                triggerRefresh()
            }).catch((error) => {handleError(error)})
    }

    function handleConfirmWatch() {
        const newWatching = !dataOld.company.watching
        const companyData = {...dataOld.company}
        companyData.watching = newWatching
        axios.put(backend + "/company", companyData)
            .then(() => {
                triggerRefresh()
            }).catch((error) => {handleError(error)})
        setOpenConfirmWatchDialog(false)
    }

    return (
        <>
            {/*<CompanySelector refresh={refresh} {...props}/>*/}
            {props.companySelectorValue && !loaded && <Loader error={error}/>}
            {props.companySelectorValue && loaded && dataOld.company.ticker !== undefined &&
                <Grid container direction="row" sx={{justifyContent: "center", alignItems: "flex-start"}}>
                    {/*minWidth was 700 before*/}
                    <Card sx={{bgcolor: 'background.paper', boxShadow: 1, borderRadius: 2, minWidth: 700, width: 700, maxHeight: "calc(100vh - 70px)", overflowY: "scroll"}}>
                        <CardContent>
                            <Box sx={{position: "relative"}}>
                                <Box sx={{color: 'text.secondary'}}>Records</Box>
                                <Box sx={{color: 'text.primary', fontSize: 34, fontWeight: 'medium'}}>
                                    {dataOld.company.ticker}
                                </Box>
                                {dataOld.company.sector && <Box sx={{color: 'text.secondary', fontSize: 14, marginTop: "-4px"}}>{dataOld.company.sector.name}</Box>}
                                {dataOld.marketCap && <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>Market Cap: {dataOld.company.currency}{dataOld.marketCap}</Box>}

                                <RecordFinancials sx={{marginBottom: "20px", marginTop: "20px"}}
                                                  financials={dataOld.financials}
                                                  triggerRefresh={triggerRefresh}
                                                  expand={expandFinancials}
                                                  setExpand={setExpandFinancials}
                                                  {...props}
                                />

                                <Button sx={{position: "absolute", top: "0", left: "100px"}} onClick={() => setOpenConfirmWatchDialog(true)}>
                                    {dataOld.company.watching && <StarIcon sx={{color: 'gold',}}/>}
                                    {!dataOld.company.watching && <StarBorderIcon sx={{color: 'lightgrey',}}/>}
                                </Button>
                                <Dialog open={openConfirmWatchDialog} onClose={() => setOpenConfirmWatchDialog(false)}>
                                    <DialogTitle>{"Are you sure to " + (dataOld.company.watching ? "unwatch" : "watch") + " the company?"}</DialogTitle>
                                    <DialogActions>
                                        <Button onClick={() => setOpenConfirmWatchDialog(false)}>Cancel</Button>
                                        <Button onClick={() => handleConfirmWatch()} autoFocus>Confirm</Button>
                                    </DialogActions>
                                </Dialog>

                                <Button sx={{position: "absolute", top: "0", right: "0"}} onClick={() => setOpenAddRecordDialog(true)}>
                                    <ControlPointIcon sx={{color: 'lightgreen',}}/>
                                </Button>
                                <AddRecordDialog open={openAddRecordDialog}
                                                 handleClose={() => setOpenAddRecordDialog(false)}
                                                 triggerRefresh={triggerRefresh}
                                                 companyId={props.companySelectorValue.id}
                                />
                            </Box>

                            {dataOld.owns.length > 0 &&
                                <Grid container direction="row" justifyContent="flex-start" alignItems="stretch" sx={{marginBottom: "20px", marginTop: "10px"}}>
                                    {dataOld.owns.map((own, index) => (
                                        <Box key={index} sx={{marginLeft: "10px"}}>
                                            <Box sx={{color: profitColor(own.profit), fontWeight: 'bold', mx: 0.5, fontSize: 12, textAlign: "center"}}>
                                                {own.profit && own.profit}
                                            </Box>
                                            <Box sx={{color: 'text.secondary', fontSize: 16, fontFamily: "Roboto",}}>
                                                {own.quantity}@{own.price}{dataOld.company.currency}
                                            </Box>
                                        </Box>
                                    ))}
                                </Grid>
                            }

                            <Grid container direction="row" justifyContent="flex-start" alignItems="stretch" sx={{marginBottom: "20px", marginTop: "10px"}}>
                                <LatestValueBox label="latest price" data={dataOld.latest.price} suffix={dataOld.company.currency}/>
                                <LatestValueBox label="latest P/E" data={dataOld.latest.pe} suffix="" sx={{marginLeft: "10px"}}/>
                                <LatestValueBox label="latest P/S" data={dataOld.latest.ps} suffix="" sx={{marginLeft: "10px"}}/>
                                <LatestValueBox label="latest DY" data={dataOld.latest.dy} suffix="%" sx={{marginLeft: "10px"}}/>
                                <LatestValueBox label="latest targets" data={dataOld.latest.targets} suffix="" sx={{marginLeft: "10px"}}/>
                                <Box sx={{ flexGrow: 1 }} />
                                <LatestValueBox label="latest strategy" data={dataOld.latest.strategy} suffix="" sx={{marginLeft: "10px"}}/>
                            </Grid>

                            {dataOld.records.map((record, index) => (
                                <BorderedSection key={record.id} title={record.date} style={{color: 'text.primary'}}>

                                    <Grid container direction="row" justifyContent="flex-start" alignItems="stretch">
                                        <EditableValueBox value={record.price} suffix={dataOld.company.currency} label="price"
                                                          updateObject={(value) => {return {id: record.id, price: value}}}
                                                          validateInput={(value) => validateNumber(value, false, 10, 4)}
                                                          handleUpdate={triggerRefresh}
                                        />
                                        <EditableValueBox value={record.pe} suffix={""} label="p/e ratio" style={{marginLeft: "5px"}}
                                                          updateObject={(value) => {return {id: record.id, pe: value}}}
                                                          validateInput={(value) => validateNumber(value, true, 5, 2)}
                                                          handleUpdate={triggerRefresh}
                                        />
                                        <EditableValueBox value={record.ps} suffix={""} label="p/s ratio" style={{marginLeft: "5px"}}
                                                          updateObject={(value) => {return {id: record.id, ps: value}}}
                                                          validateInput={(value) => validateNumber(value, true, 5, 2)}
                                                          handleUpdate={triggerRefresh}
                                        />
                                        <EditableValueBox value={record.dy} suffix={"%"} label="dividend yield" style={{marginLeft: "5px"}}
                                                          updateObject={(value) => {return {id: record.id, dy: value}}}
                                                          validateInput={(value) => validateNumber(value, true, 5, 2)}
                                                          handleUpdate={triggerRefresh}
                                        />
                                        <EditableValueBox value={record.targets} suffix={dataOld.company.currency} label="targets" style={{marginLeft: "5px"}}
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
                                        <ContentEditor content={record.content} handleUpdate={(value) => recordUpdated(record.id, value)}/>
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
                    <Card sx={{bgcolor: 'background.paper', boxShadow: 1, borderRadius: 2, minWidth: 700, width: 700, maxHeight: "calc(100vh - 70px)", overflowY: "scroll"}}>
                        <CardContent>
                            <Box sx={{position: "relative"}}>
                                <Box sx={{color: 'text.secondary'}}>Research</Box>
                                <Box sx={{color: 'text.primary', fontSize: 34, fontWeight: 'medium'}}>
                                    {data.company.ticker}
                                </Box>
                                {data.company.sector && <Box sx={{color: 'text.secondary', fontSize: 14, marginTop: "-4px"}}>{data.company.sector.name}</Box>}

                                <PeriodFinancials sx={{marginBottom: "20px", marginTop: "20px"}}
                                                  financials={data.financials}
                                                  ttm={data.ttm}
                                                  triggerRefresh={triggerRefresh}
                                                  expand={expandFinancials}
                                                  setExpand={setExpandFinancials}
                                                  {...props}
                                />

                                <Button sx={{position: "absolute", top: "0", left: "100px"}} onClick={() => setOpenConfirmWatchDialog(true)}>
                                    {data.company.watching && <StarIcon sx={{color: 'gold',}}/>}
                                    {!data.company.watching && <StarBorderIcon sx={{color: 'lightgrey',}}/>}
                                </Button>
                                <Dialog open={openConfirmWatchDialog} onClose={() => setOpenConfirmWatchDialog(false)}>
                                    <DialogTitle>{"Are you sure to " + (data.company.watching ? "unwatch" : "watch") + " the company?"}</DialogTitle>
                                    <DialogActions>
                                        <Button onClick={() => setOpenConfirmWatchDialog(false)}>Cancel</Button>
                                        <Button onClick={() => handleConfirmWatch()} autoFocus>Confirm</Button>
                                    </DialogActions>
                                </Dialog>

                                <Button sx={{position: "absolute", top: "0", right: "0"}} onClick={() => setOpenAddPeriodDialog(true)}>
                                    <ControlPointIcon sx={{color: 'lightgreen',}}/>
                                </Button>
                                <AddPeriodDialog open={openAddPeriodDialog}
                                                 handleClose={() => setOpenAddPeriodDialog(false)}
                                                 triggerRefresh={triggerRefresh}
                                                 companyId={props.companySelectorValue.id}
                                />
                            </Box>

                            <AddPeriodFinancialDialog open={openAddFinancialDialog !== null}
                                                      period={openAddFinancialDialog}
                                                      triggerRefresh={triggerRefresh}
                                                      handleClose={() => setOpenAddFinancialDialog(null)}
                                                      companyId={props.companySelectorValue.id}
                                                      {...props}
                            />

                            {data.periods.map((period, index) => (
                                <BorderedSection key={period.id}
                                                 title={period.name + " - ending: " + period.endingMonth + " - report: " + period.reportDate}
                                                 style={{color: 'text.primary'}}>


                                    <div style={{width: "700px", margin: "15px auto 0 auto"}}>
                                        <ContentEditor content={period.research} handleUpdate={(value) => periodUpdated(period.id, value)}/>
                                    </div>

                                    {period.revenue &&
                                        <>
                                            <Typography sx={{color: 'text.secondary', fontSize: 14}}>
                                                {"Shares: " + period.shares
                                                    + " | H: " +  period.priceHigh + data.company.currency
                                                    + " | L: " + period.priceLow + data.company.currency
                                                    + " | Dividend: " + period.dividend}
                                            </Typography>
                                            <Typography sx={{color: 'text.secondary', fontSize: 14}} >
                                                {"Revenue: " + period.revenue
                                                    + " | Costs of Goods: " + period.costGoodsSold
                                                    + " | Op. Exp.: " + period.operatingExpenses
                                                    + " | Net Income: " + period.netIncome}
                                            </Typography>
                                        </>
                                    }
                                    {!period.revenue &&
                                        <Tooltip title="Add Financials">
                                            <Button sx={{height: "25px"}} onClick={() => {
                                                if (data.periods[index + 1] && data.periods[index + 1].reportDate) {
                                                    const [day, month, year] = data.periods[index + 1].reportDate.split(".")
                                                    const date = new Date(`${year}-${month}-${day}`)
                                                    date.setDate(date.getDate() + 1)
                                                    period.quoteFromDate = date.toISOString().split("T")[0]
                                                }
                                                setOpenAddFinancialDialog(period)
                                            }}>
                                                <ControlPointIcon sx={{color: 'lightgreen'}}/>
                                            </Button>
                                        </Tooltip>
                                    }

                                </BorderedSection>
                            ))}
                        </CardContent>
                    </Card>
                    <Card sx={{bgcolor: 'background.paper', boxShadow: 1, borderRadius: 2, minWidth: 700, width: 700, maxHeight: "calc(100vh - 70px)", overflowY: "scroll"}}>
                        <CardContent>
                            <Box sx={{position: "relative"}}>
                                <Box sx={{color: 'text.secondary'}}>Records</Box>
                                <Box sx={{color: 'text.primary', fontSize: 34, fontWeight: 'medium'}}>
                                    {dataOld.company.ticker}
                                </Box>
                                {dataOld.company.sector && <Box sx={{color: 'text.secondary', fontSize: 14, marginTop: "-4px"}}>{dataOld.company.sector.name}</Box>}
                                {dataOld.marketCap && <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>Market Cap: {dataOld.company.currency}{dataOld.marketCap}</Box>}

                                <PeriodFinancials sx={{marginBottom: "20px", marginTop: "20px"}}
                                                  financials={dataOld.financials}
                                                  triggerRefresh={triggerRefresh}
                                                  expand={expandFinancials}
                                                  setExpand={setExpandFinancials}
                                                  {...props}
                                />

                                <Button sx={{position: "absolute", top: "0", left: "100px"}} onClick={() => setOpenConfirmWatchDialog(true)}>
                                    {dataOld.company.watching && <StarIcon sx={{color: 'gold',}}/>}
                                    {!dataOld.company.watching && <StarBorderIcon sx={{color: 'lightgrey',}}/>}
                                </Button>
                                <Dialog open={openConfirmWatchDialog} onClose={() => setOpenConfirmWatchDialog(false)}>
                                    <DialogTitle>{"Are you sure to " + (dataOld.company.watching ? "unwatch" : "watch") + " the company?"}</DialogTitle>
                                    <DialogActions>
                                        <Button onClick={() => setOpenConfirmWatchDialog(false)}>Cancel</Button>
                                        <Button onClick={() => handleConfirmWatch()} autoFocus>Confirm</Button>
                                    </DialogActions>
                                </Dialog>

                                <Button sx={{position: "absolute", top: "0", right: "0"}} onClick={() => setOpenAddRecordDialog(true)}>
                                    <ControlPointIcon sx={{color: 'lightgreen',}}/>
                                </Button>
                                <AddRecordDialog open={openAddRecordDialog}
                                                 handleClose={() => setOpenAddRecordDialog(false)}
                                                 triggerRefresh={triggerRefresh}
                                                 companyId={props.companySelectorValue.id}
                                />
                            </Box>

                            {dataOld.owns.length > 0 &&
                                <Grid container direction="row" justifyContent="flex-start" alignItems="stretch" sx={{marginBottom: "20px", marginTop: "10px"}}>
                                    {dataOld.owns.map((own, index) => (
                                        <Box key={index} sx={{marginLeft: "10px"}}>
                                            <Box sx={{color: profitColor(own.profit), fontWeight: 'bold', mx: 0.5, fontSize: 12, textAlign: "center"}}>
                                                {own.profit && own.profit}
                                            </Box>
                                            <Box sx={{color: 'text.secondary', fontSize: 16, fontFamily: "Roboto",}}>
                                                {own.quantity}@{own.price}{dataOld.company.currency}
                                            </Box>
                                        </Box>
                                    ))}
                                </Grid>
                            }

                            <Grid container direction="row" justifyContent="flex-start" alignItems="stretch" sx={{marginBottom: "20px", marginTop: "10px"}}>
                                <LatestValueBox label="latest price" data={dataOld.latest.price} suffix={dataOld.company.currency}/>
                                <LatestValueBox label="latest P/E" data={dataOld.latest.pe} suffix="" sx={{marginLeft: "10px"}}/>
                                <LatestValueBox label="latest P/S" data={dataOld.latest.ps} suffix="" sx={{marginLeft: "10px"}}/>
                                <LatestValueBox label="latest DY" data={dataOld.latest.dy} suffix="%" sx={{marginLeft: "10px"}}/>
                                <LatestValueBox label="latest targets" data={dataOld.latest.targets} suffix="" sx={{marginLeft: "10px"}}/>
                                <Box sx={{ flexGrow: 1 }} />
                                <LatestValueBox label="latest strategy" data={dataOld.latest.strategy} suffix="" sx={{marginLeft: "10px"}}/>
                            </Grid>

                            {dataOld.records.map((record, index) => (
                                <BorderedSection key={record.id} title={record.date} style={{color: 'text.primary'}}>

                                    <Grid container direction="row" justifyContent="flex-start" alignItems="stretch">
                                        <EditableValueBox value={record.price} suffix={dataOld.company.currency} label="price"
                                                          updateObject={(value) => {return {id: record.id, price: value}}}
                                                          validateInput={(value) => validateNumber(value, false, 10, 4)}
                                                          handleUpdate={triggerRefresh}
                                        />
                                        <EditableValueBox value={record.pe} suffix={""} label="p/e ratio" style={{marginLeft: "5px"}}
                                                          updateObject={(value) => {return {id: record.id, pe: value}}}
                                                          validateInput={(value) => validateNumber(value, true, 5, 2)}
                                                          handleUpdate={triggerRefresh}
                                        />
                                        <EditableValueBox value={record.ps} suffix={""} label="p/s ratio" style={{marginLeft: "5px"}}
                                                          updateObject={(value) => {return {id: record.id, ps: value}}}
                                                          validateInput={(value) => validateNumber(value, true, 5, 2)}
                                                          handleUpdate={triggerRefresh}
                                        />
                                        <EditableValueBox value={record.dy} suffix={"%"} label="dividend yield" style={{marginLeft: "5px"}}
                                                          updateObject={(value) => {return {id: record.id, dy: value}}}
                                                          validateInput={(value) => validateNumber(value, true, 5, 2)}
                                                          handleUpdate={triggerRefresh}
                                        />
                                        <EditableValueBox value={record.targets} suffix={dataOld.company.currency} label="targets" style={{marginLeft: "5px"}}
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

                </Grid>
            }
        </>
    )
}
export default Records