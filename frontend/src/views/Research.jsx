import {Badge, Box, Button, Card, CardContent, Dialog, DialogActions, DialogTitle, Grid, Stack} from "@mui/material";
import React, {useEffect, useState} from "react";
import Loader from "../components/Loader";
import {backend} from "../properties";
import axios from "axios";
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import AddRecordDialog from "../dialog/AddRecordDialog";
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import PeriodFinancials from "../components/PeriodFinancials";
import AddPeriodDialog from "../dialog/AddPeriodDialog";
import AddPeriodFinancialDialog from "../dialog/AddPeriodFinancialDialog";
import {formatDecimals, formatError, formatMillions, formatPercent} from "../service/FormattingService";
import SnackbarErrorAlert from "../components/SnackbarErrorAlert";
import CloudDownloadIcon from '@mui/icons-material/CloudDownload';
import {AssetBox} from "./component/AssetBox";
import ImportPeriodDialog from "../dialog/ImportPeriodDialog";
import {DateTime} from "./component/DateTime";
import {Record} from "./component/Record";
import {Period} from "./component/Period";
import {CompanySelector} from "../components/CompanySelector";

const badgeStyle = {"& .MuiBadge-badge": {fontSize: "0.6rem", height: "15px", minWidth: "15px", backgroundColor: "#ff7961", color: "white"}}

export const Research = props => {
    const [refresh, setRefresh] = useState("")

    useEffect(() => {
        props.toggleRecordsSelectors()
        // eslint-disable-next-line
    }, [])

    const [data, setData] = useState(null)
    const [loaded, setLoaded] = useState(false)
    const [error, setError] = useState(null)
    const [alert, setAlert] = useState(null)
    const [openAddRecordDialog, setOpenAddRecordDialog] = useState(false)
    const [openAddPeriodDialog, setOpenAddPeriodDialog] = useState(false)
    const [openImportPeriodDialog, setOpenImportPeriodDialog] = useState(false)
    const [openConfirmWatchDialog, setOpenConfirmWatchDialog] = useState(false)
    const [expandFinancials, setExpandFinancials] = useState(false)
    const [openAddFinancialDialog, setOpenAddFinancialDialog] = useState(null)

    function fetchData(companyChanged) {
        if (props.companySelectorValue) {
            axios.get(backend + "/research/" + props.companySelectorValue.id + (refresh ? "?refresh" + refresh : ""))
                .then((response) => {
                    setData(response.data)
                    setError(null)

                    if (companyChanged) setExpandFinancials(false)
                    if (sessionStorage.getItem('showFinancials')){
                        setExpandFinancials(true)
                        sessionStorage.removeItem('showFinancials')
                    }
                    setLoaded(true)
                })
                .catch((error) => {
                    setError(formatError(error))
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

    function handleConfirmWatch() {
        const newWatching = !data.company.watching
        const companyData = {...data.company}
        companyData.sector = companyData.sector.key
        companyData.watching = newWatching
        axios.put(backend + "/company", companyData)
            .then((response) => {
                setData(prev => ({...prev, company: {...prev.company, watching: newWatching}}))
            })
            .catch((error) => {
                const formatted = formatError(error)
                setAlert(formatted)
                return formatted
            })
        setOpenConfirmWatchDialog(false)
    }

    return (
        <>
            <CompanySelector refresh={refresh} {...props}/>
            {props.companySelectorValue && !loaded && <Loader error={error}/>}
            {props.companySelectorValue && loaded && data.company.ticker !== undefined &&
                <Grid container direction="row" sx={{justifyContent: "center", alignItems: "flex-start"}}>
                    <Card sx={{bgcolor: 'background.paper', boxShadow: 1, borderRadius: 2, minWidth: 700, width: 800, maxHeight: "calc(100vh - 70px)", overflowY: "scroll"}}>
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

                                <Box sx={{position: "absolute", top: "0", right: "0"}}>
                                    {data.newerCachedPeriods.length > 0 &&
                                        <>
                                            <Button
                                                onClick={() => setOpenImportPeriodDialog(true)}>
                                                <Badge badgeContent={data.newerCachedPeriods.length} sx={badgeStyle}>
                                                    <CloudDownloadIcon sx={{color: 'lightgreen'}}/>
                                                </Badge>
                                            </Button>
                                            <ImportPeriodDialog
                                                open={openImportPeriodDialog}
                                                handleClose={() => setOpenImportPeriodDialog(false)}
                                                company={props.companySelectorValue}
                                                periods={data.newerCachedPeriods}
                                                triggerRefresh={triggerRefresh}
                                            />
                                        </>
                                    }
                                    <Button onClick={() => setOpenAddPeriodDialog(true)}>
                                        <ControlPointIcon sx={{color: 'lightgreen',}}/>
                                    </Button>
                                    <AddPeriodDialog
                                        open={openAddPeriodDialog}
                                        handleClose={() => setOpenAddPeriodDialog(false)}
                                        triggerRefresh={triggerRefresh}
                                        companyId={props.companySelectorValue.id}
                                    />
                                </Box>
                            </Box>

                            <AddPeriodFinancialDialog
                                open={openAddFinancialDialog !== null}
                                period={openAddFinancialDialog}
                                triggerRefresh={triggerRefresh}
                                handleClose={() => setOpenAddFinancialDialog(null)}
                                company={props.companySelectorValue}
                            />

                            {data.periods.map((period, index) => (
                                <Period
                                    key={index}
                                    period={period}
                                    currency={data.company.currency}
                                    setAlert={setAlert}
                                    openDialog={() => setOpenAddFinancialDialog(period)}
                                />
                            ))}
                        </CardContent>
                    </Card>
                    <Card sx={{bgcolor: 'background.paper', boxShadow: 1, borderRadius: 2, minWidth: 700, width: 800, maxHeight: "calc(100vh - 70px)", overflowY: "scroll"}}>
                        <CardContent>
                            <Box sx={{position: "relative"}}>
                                <Box sx={{color: 'text.secondary'}}>Records</Box>

                                {data.latest &&
                                <>
                                    <Box sx={{color: 'text.primary', fontSize: 34, fontWeight: 'medium'}}>
                                        {data.company.currency}{formatDecimals(data.latest.price,0,2)}
                                    </Box>
                                    <DateTime value={data.latest.datetime} sx={{marginTop: '-2px', color: 'text.secondary', fontSize: 11}} iconMarginTop={"1px"}/>
                                </>
                                }

                                <Button sx={{position: "absolute", top: "0", right: "0"}} onClick={() => setOpenAddRecordDialog(true)}>
                                    <ControlPointIcon sx={{color: 'lightgreen',}}/>
                                </Button>
                                <AddRecordDialog open={openAddRecordDialog}
                                                 handleClose={() => setOpenAddRecordDialog(false)}
                                                 triggerRefresh={triggerRefresh}
                                                 companyId={props.companySelectorValue.id}
                                                 indicators={data.indicators}
                                                 assets={data.assets}
                                />
                            </Box>

                            {data.indicators &&
                                <Box>
                                    <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>Market Cap: {data.company.currency}{formatMillions(data.indicators.marketCap)}</Box>
                                    <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>Dividend Yield: {formatPercent(data.indicators.ttm.dividendYield)}</Box>

                                    <Stack direction={"row"} spacing={2}>
                                        <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>PS: {formatDecimals(data.indicators.ttm.marketCapToRevenues, 0, 2)}</Box>
                                        <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>PG: {formatDecimals(data.indicators.ttm.marketCapToGrossProfit, 0, 2)}</Box>
                                        <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>PO: {formatDecimals(data.indicators.ttm.marketCapToOperatingIncome, 0, 2)}</Box>
                                        <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>PE: {formatDecimals(data.indicators.ttm.marketCapToNetIncome, 0, 2)}</Box>
                                    </Stack>
                                </Box>
                            }

                            {data.assets.assets.length > 0 &&
                                <Grid container direction="row" justifyContent="flex-start" alignItems="stretch" sx={{marginBottom: "20px", marginTop: "10px"}}>
                                    {data.assets.assets.map((asset, index) => (
                                        <AssetBox key={index} asset={asset} currency={data.company.currency}/>
                                    ))}
                                </Grid>
                            }

                            {data.records.map((record, index) => (
                                <Record
                                    key={index}
                                    record={record}
                                    currency={data.company.currency}
                                    setAlert={setAlert}
                                />
                            ))}
                        </CardContent>
                    </Card>

                </Grid>
            }
            <SnackbarErrorAlert error={alert} open={alert !== null} onClose={() => setAlert(null)}/>
        </>
    )
}
