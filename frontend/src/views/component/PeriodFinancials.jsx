import {
    Box,
    Button,
    Grid,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    useMediaQuery
} from "@mui/material";
import React, {useState} from "react";
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';
import ArrowDropUpIcon from '@mui/icons-material/ArrowDropUp';
import {
    formatDecimals,
    formatMillions,
    formatPercent,
    formatPeriodName,
    isNotAValue
} from "../../service/FormattingService";


export const PeriodFinancials = props => {
    const {financials, ttm, expand, setExpand, sx} = props
    const [showExpand, setShowExpand] = useState(false)
    const isNarrowScreen = useMediaQuery("(max-width:1599px)")

    const headers = ["Period", "Revenue", "Gross Profit", "Operating Income", "Net Income", "Dividend"]

    function FinancialSummaryItem({value, label, margin}) {
        return <Box sx={{marginLeft: "10px"}}>
            <Box sx={{fontSize: 9, textAlign: "center", marginBottom: "0px"}}>({formatDecimals(margin, 0, 0)}%)</Box>
            <Box sx={{fontWeight: 'bold', fontSize: 13, textAlign: "center"}}>{formatMillions(value)}</Box>
            <Box sx={{color: 'lightgrey', fontWeight: 'bold', mx: 0.5, fontSize: 12, textAlign: "center"}}>{label}</Box>
        </Box>
    }

    function FinancialTableCell({value, margin, yoy, qoq}) {
        yoy = formatPercent(yoy, true, 0)
        qoq = formatPercent(qoq, true, 0)
        let changesValue = ""
        if (yoy || qoq) {
            changesValue = (yoy ? yoy : "") + ((yoy && qoq) ? " / " : "") + (qoq ? qoq + "" : "");
        }
        return (
            <TableCell sx={{textAlign: "center"}}>
                {!isNotAValue(margin) ?
                    <Box sx={{display: "flex", flexDirection: "column"}}>
                        <Box sx={{fontSize: 9, marginBottom: "-3px", color: "grey"}}>
                            ({formatPercent(margin, false, 0)})
                        </Box>
                        <Box sx={{fontSize: 14}}>
                            {value}
                        </Box>
                        <Box sx={{fontSize: 9, marginTop: "-3px", color: "grey"}}>
                            {changesValue}
                        </Box>
                    </Box>
                    :
                    <Box sx={{fontSize: 14}}>
                        {value}
                    </Box>
                }
            </TableCell>
        )
    }

    return(
        <Paper elevation={0}  sx={sx} onMouseEnter={() => setShowExpand(true)} onMouseLeave={() => setShowExpand(false)}>

        <Grid container direction="row" justifyContent="flex-start" alignItems="stretch" sx={{width: "100%"}}>
            {ttm && (
                <>
                    <FinancialSummaryItem value={ttm.revenue.value} label="revenue" margin={ttm.revenue.margin}/>
                    <FinancialSummaryItem value={ttm.grossProfit.value} label="gross profit" margin={ttm.grossProfit.margin}/>
                    <FinancialSummaryItem value={ttm.operatingIncome.value} label="operating income" margin={ttm.operatingIncome.margin}/>
                    <FinancialSummaryItem value={ttm.netIncome.value} label="net income" margin={ttm.netIncome.margin}/>
                    {(isNarrowScreen || showExpand || expand) &&
                        <Button sx={{height: "25px"}} onClick={() => setExpand(!expand)}>
                            <>{!expand && <ArrowDropDownIcon/>}{expand && <ArrowDropUpIcon/>}</>
                        </Button>
                    }
                </>
            )}
        </Grid>

        {expand && ttm &&
            <TableContainer sx={{width: "100%", maxWidth: "100%", maxHeight: "200px", overflowX: "auto"}}>
            <Table size="small" aria-label="a dense table" stickyHeader sx={{minWidth: 650}}>
                <TableHead>
                    <TableRow>
                        {headers.map((column) => (
                            <TableCell key={column}>{column}</TableCell>
                        ))}
                    </TableRow>
                </TableHead>
                <TableBody>
                    {financials.map((financial) => (
                        <TableRow key={formatPeriodName(financial.period)}>
                            <FinancialTableCell value={formatPeriodName(financial.period)}/>
                            <FinancialTableCell value={formatMillions(financial.revenue.value)} margin={financial.revenue.margin} yoy={financial.revenue.yoy} qoq={financial.revenue.qoq}/>
                            <FinancialTableCell value={formatMillions(financial.grossProfit.value)} margin={financial.grossProfit.margin} yoy={financial.grossProfit.yoy} qoq={financial.grossProfit.qoq}/>
                            <FinancialTableCell value={formatMillions(financial.operatingIncome.value)} margin={financial.operatingIncome.margin} yoy={financial.operatingIncome.yoy} qoq={financial.operatingIncome.qoq}/>
                            <FinancialTableCell value={formatMillions(financial.netIncome.value)} margin={financial.netIncome.margin} yoy={financial.netIncome.yoy} qoq={financial.netIncome.qoq}/>
                            <FinancialTableCell value={formatMillions(financial.dividend)}/>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
            </TableContainer>
        }

        </Paper>
    )
}
