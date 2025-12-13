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
    TableRow
} from "@mui/material";
import React, {useState} from "react";
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';
import ArrowDropUpIcon from '@mui/icons-material/ArrowDropUp';
import {formatDecimals, formatMillions, formatPeriodName} from "../service/FormattingService";


const PeriodFinancials = props => {
    const {financials, ttm, expand, setExpand} = props
    const [showExpand, setShowExpand] = useState(false)

    const headers = ["Period", "Revenue", "Gross Profit", "Operating Income", "Net Income", "Dividend"]
    const labels = ["revenue", "gross profit", "operating income", "net income"]

    function withMarginAndLabel(value, label, margin){
        return <Box sx={{marginLeft: "10px"}}>
            <Box sx={{fontSize: 9, textAlign: "center", marginBottom: "0px"}}>({formatDecimals(margin, 0, 0)}%)</Box>
            <Box sx={{fontWeight: 'bold', fontSize: 13, textAlign: "center"}}>{formatMillions(value)}</Box>
            <Box sx={{color: 'lightgrey', fontWeight: 'bold', mx: 0.5, fontSize: 12, textAlign: "center"}}>{label}</Box>
        </Box>
    }

    function withMargin(value, margin) {
        return <Box sx={{display: 'flex', flexWrap: 'nowrap', justifyContent: 'flex-end', alignItems: 'flex-end'}}>
            <Box sx={{fontSize: 14, textAlign: "right", marginRight: "3px"}}>{formatMillions(value)}</Box>
            <Box sx={{fontSize: 12, textAlign: "right", marginBottom: "0px"}}>({formatDecimals(margin, 0, 0)}%)</Box>
        </Box>
    }

    return(
        <Paper elevation={0}  sx={props.sx} onMouseEnter={() => setShowExpand(true)} onMouseLeave={() => setShowExpand(false)}>

        <Grid container direction="row" justifyContent="flex-start" alignItems="stretch">
            {ttm && withMarginAndLabel(ttm.revenue, labels[0], 100)}
            {ttm && withMarginAndLabel(ttm.grossProfit, labels[1], ttm.grossMargin)}
            {ttm && withMarginAndLabel(ttm.operatingIncome, labels[2], ttm.operatingMargin)}
            {ttm && withMarginAndLabel(ttm.netIncome, labels[3], ttm.netMargin)}
            {(showExpand || expand) && ttm &&
                <Button sx={{height: "25px"}} onClick={() => setExpand(!expand)}>
                    <>{!expand && <ArrowDropDownIcon/>}{expand && <ArrowDropUpIcon/>}</>
                </Button>
            }
        </Grid>

        {expand && ttm &&
            <TableContainer sx={{ width: "max-content", maxHeight: "200px"}}>
            <Table size="small" aria-label="a dense table" stickyHeader>
                <TableHead>
                    <TableRow>
                        {headers.map((column, index) => (
                            <TableCell key={index}>{column}</TableCell>
                        ))}
                    </TableRow>
                </TableHead>
                <TableBody>
                    {financials.map((financial, index) => (
                        <TableRow key={index}>
                            <TableCell key={0} sx={{textAlign: "center"}}>{formatPeriodName(financial.period)}</TableCell>
                            <TableCell key={1} sx={{textAlign: "right"}}>{formatMillions(financial.revenue)}</TableCell>
                            <TableCell key={2} sx={{textAlign: "right"}}>{withMargin(financial.grossProfit, financial.grossMargin)}</TableCell>
                            <TableCell key={3} sx={{textAlign: "right"}}>{withMargin(financial.operatingIncome, financial.operatingMargin)}</TableCell>
                            <TableCell key={4} sx={{textAlign: "right"}}>{withMargin(financial.netIncome, financial.netMargin)}</TableCell>
                            <TableCell key={5} sx={{textAlign: "right"}}>{formatMillions(financial.dividend)}</TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
            </TableContainer>
        }

        </Paper>
    )
}
export default PeriodFinancials