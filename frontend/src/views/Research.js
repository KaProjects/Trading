import {
    Box,
    Button,
    Card,
    CardContent,
    Dialog,
    DialogActions,
    DialogTitle,
    Grid,
    Stack,
    Typography
} from "@mui/material";
import React, {useEffect, useState} from "react";
import Loader from "../components/Loader";
import BorderedSection from "../components/BorderedSection";
import {backend} from "../properties";
import axios from "axios";
import ContentEditor from "../components/ContentEditor";
import EditableTypography from "../components/EditableTypography";
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import AddRecordDialog from "../dialog/AddRecordDialog";
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import {handleError} from "../service/utils";
import PeriodFinancials from "../components/PeriodFinancials";
import AddPeriodDialog from "../dialog/AddPeriodDialog";
import AddPeriodFinancialDialog from "../dialog/AddPeriodFinancialDialog";
import Tooltip from "@mui/material/Tooltip";
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import CompanySelector from "../components/CompanySelector";
import {formatDate, formatDecimals, formatMillions, formatPercent, formatPeriodName} from "../service/FormattingService";


const Research = props => {
    const [refresh, setRefresh] = useState("")

    useEffect(() => {
        props.toggleRecordsSelectors()
        // eslint-disable-next-line
    }, [])

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

    function updateRecordContent(recordId, content) {
        axios.put(backend + "/record", {id: recordId, content: JSON.stringify(content)})
            .then((response) => {
                triggerRefresh()
            }).catch((error) => {handleError(error)})
    }

    function updatePeriodResearch(periodId, content) {
        axios.put(backend + "/period", {id: periodId, research: JSON.stringify(content)})
            .then((response) => {
                triggerRefresh()
            }).catch((error) => {handleError(error)})
    }

    function handleConfirmWatch() {
        const newWatching = !data.company.watching
        const companyData = {...data.company}
        companyData.watching = newWatching
        axios.put(backend + "/company", companyData)
            .then(() => {
                triggerRefresh()
            }).catch((error) => {handleError(error)})
        setOpenConfirmWatchDialog(false)
    }

    function formatEndingMonth(endingMonth) {
        if (endingMonth === null || endingMonth === undefined) return "";
        return endingMonth.substring(5, 7) + "/" + endingMonth.substring(2, 4);
    }

    function formatPrice(price, currency) {
        if (price === null || price === undefined) return "";
        return price + currency;
    }

    const DateTime = ({value, sx, iconMarginTop = '0'}) => {
        if (value === null || value === undefined) return null;

        let [date, time] = value.split("T")
        let [year, month, day] = date.split("-")

        const typoSx = {fontSize: sx.fontSize, align: 'center', noWrap: true, variant: 'string'}

        return <Stack sx={sx} direction="row" alignItems={"stretch"}>
            <CalendarTodayIcon sx={{fontSize: sx.fontSize, marginTop: iconMarginTop, marginRight: '0'}}/>
            <Typography sx={typoSx}>
                {day}.{month}.{year}
            </Typography>
            <AccessTimeIcon sx={{fontSize: sx.fontSize + 1, marginTop: iconMarginTop, marginRight: '1px'}}/>
            <Typography sx={typoSx}>
                {time}
            </Typography>
        </Stack>
    }

    const AssetBox = ({asset, currency}) => {
        let profitColor = 'text.primary'
        let profitPercent = null

        if (!isNaN(Number(asset.profitPercent))) {

            profitPercent = formatDecimals(asset.profitPercent, 0, 2)

            if (asset.profitPercent > 0) {
                profitPercent = "+" + profitPercent
                profitColor = 'success.dark'
            }
            if (asset.profitPercent < 0){
                profitColor = 'error.dark'
            }
        }

        return <Box sx={{marginLeft: "10px"}}>
            {profitPercent &&
                <Box sx={{color: profitColor, fontWeight: 'bold', mx: 0.5, fontSize: 12, textAlign: "center"}}>
                    {profitPercent}%
                </Box>
            }
            <Box sx={{color: "text.secondary", fontSize: 16, fontFamily: "Roboto",}}>
                {asset.quantity}@{asset.purchasePrice}{currency}
            </Box>
        </Box>
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
                                                  financials={data.periods.financials}
                                                  ttm={data.periods.ttm}
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

                            {data.periods.periods.map((period, index) => (
                                <BorderedSection key={period.id}
                                                 title={formatPeriodName(period.name) + " - ending: " + formatEndingMonth(period.endingMonth) + " - report: " + formatDate(period.reportDate)}
                                                 style={{color: 'text.primary'}}>


                                    <div style={{margin: "15px 0 0 0"}}>
                                        <ContentEditor content={period.research} handleUpdate={(value) => updatePeriodResearch(period.id, value)}/>
                                    </div>

                                    {period.financial &&
                                        <>
                                            <Typography sx={{color: 'text.secondary', fontSize: 14}}>
                                                {"Shares: " + formatMillions(period.shares)
                                                    + " | H: " + formatPrice(period.priceHigh, data.company.currency)
                                                    + " | L: " + formatPrice(period.priceLow, data.company.currency)
                                                    + " | Dividend: " + formatMillions(period.financial.dividend)}
                                            </Typography>
                                            <Typography sx={{color: 'text.secondary', fontSize: 14}} >
                                                {"Revenue: " + formatMillions(period.financial.revenue)
                                                    + " | Costs of Goods: " + formatMillions(period.financial.costGoodsSold)
                                                    + " | Op. Exp.: " + formatMillions(period.financial.operatingExpenses)
                                                    + " | Net Income: " + formatMillions(period.financial.netIncome)}
                                            </Typography>
                                        </>
                                    }
                                    {!period.financial &&
                                        <Tooltip title="Add Financials">
                                            <Button sx={{height: "25px"}} onClick={() => {
                                                if (data.periods.periods[index + 1] && data.periods.periods[index + 1].reportDate) {
                                                    const date = new Date(data.periods.periods[index + 1].reportDate)
                                                    date.setDate(date.getDate() + 1)
                                                    period.quoteFirstAfterLastReportDate = date.toISOString().split("T")[0]
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
                                <BorderedSection key={record.id} title={formatDate(record.date)} style={{color: 'text.primary'}}>
                                    <Stack direction="row" justifyContent="flex-start" alignItems="stretch" spacing={2}>
                                        <Box>{data.company.currency}{record.price}</Box>
                                        <Box>PS:{record.priceToRevenues}</Box>
                                        <Box>PG:{record.priceToGrossProfit}</Box>
                                        <Box>PO:{record.priceToOperatingIncome}</Box>
                                        <Box>PE:{record.priceToNetIncome}</Box>
                                        <Box>DY:{record.dividendYield}</Box>
                                        <Box>t:{record.targets}</Box>
                                        <Box>s:{record.strategy}</Box>
                                    </Stack>

                                    {record.asset &&
                                        <Stack direction="row" justifyContent="flex-start" alignItems="stretch" spacing={2}>
                                            <AssetBox key={index} asset={record.asset} currency={data.company.currency}/>
                                        </Stack>
                                    }

                                    <EditableTypography value={record.title} label={"Title"} style={{margin: "12px auto auto 5px"}}
                                                        updateObject={(value) => {return {id: record.id, title: value}}}
                                                        validateInput={(value) => {if (value === "") return "not null"; return ""}}
                                                        handleUpdate={triggerRefresh}
                                    />
                                    <div style={{margin: "15px 0 0 0"}}>
                                        <ContentEditor content={record.content} handleUpdate={(value) => updateRecordContent(record.id, value)}/>
                                    </div>

                                </BorderedSection>
                            ))}
                        </CardContent>
                    </Card>

                </Grid>
            }
        </>
    )
}
export default Research