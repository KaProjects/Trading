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
import {formatDecimals, formatMillions, formatPeriodName} from "../../service/FormattingService";


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

    function FinancialTableCell({value, margin, align, format}) {
        return (
            <TableCell sx={{textAlign: align}}>
                {margin === undefined || margin === null ? (
                    format(value)
                ) : (
                    <Box sx={{display: 'flex', flexWrap: 'nowrap', justifyContent: 'flex-end', alignItems: 'flex-end'}}>
                        <Box sx={{fontSize: 14, textAlign: "right", marginRight: "3px"}}>
                            {format(value)}
                        </Box>
                        <Box sx={{fontSize: 12, textAlign: "right", marginBottom: "0px"}}>
                            ({formatDecimals(margin, 0, 0)}%)
                        </Box>
                    </Box>
                )}
            </TableCell>
        )
    }

    return(
        <Paper elevation={0}  sx={sx} onMouseEnter={() => setShowExpand(true)} onMouseLeave={() => setShowExpand(false)}>

        <Grid container direction="row" justifyContent="flex-start" alignItems="stretch">
            {ttm && (
                <>
                    <FinancialSummaryItem value={ttm.revenue} label="revenue" margin={100}/>
                    <FinancialSummaryItem value={ttm.grossProfit} label="gross profit" margin={ttm.grossMargin}/>
                    <FinancialSummaryItem value={ttm.operatingIncome} label="operating income" margin={ttm.operatingMargin}/>
                    <FinancialSummaryItem value={ttm.netIncome} label="net income" margin={ttm.netMargin}/>
                    {(isNarrowScreen || showExpand || expand) &&
                        <Button sx={{height: "25px"}} onClick={() => setExpand(!expand)}>
                            <>{!expand && <ArrowDropDownIcon/>}{expand && <ArrowDropUpIcon/>}</>
                        </Button>
                    }
                </>
            )}
        </Grid>

        {expand && ttm &&
            <TableContainer sx={{ width: "max-content", maxHeight: "200px"}}>
            <Table size="small" aria-label="a dense table" stickyHeader>
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
                            <FinancialTableCell value={financial.period} align="center" format={formatPeriodName}/>
                            <FinancialTableCell value={financial.revenue} align="right" format={formatMillions}/>
                            <FinancialTableCell value={financial.grossProfit} margin={financial.grossMargin} align="right" format={formatMillions}/>
                            <FinancialTableCell value={financial.operatingIncome} margin={financial.operatingMargin} align="right" format={formatMillions}/>
                            <FinancialTableCell value={financial.netIncome} margin={financial.netMargin} align="right" format={formatMillions}/>
                            <FinancialTableCell value={financial.dividend} align="right" format={formatMillions}/>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
            </TableContainer>
        }

        </Paper>
    )
}
