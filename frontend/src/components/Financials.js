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
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import Tooltip from "@mui/material/Tooltip";


const Financials = props => {
    const {financialsHeaders, financials, ttmFinancialLabels, ttmFinancial} = props
    const [expand, setExpand] = useState(false);
    const [showExpand, setShowExpand] = useState(false);

    function financial(value, label){
        return <Box sx={{marginLeft: "10px"}}>
            <Box sx={{color: 'text.primary', fontWeight: 'bold', mx: 0.5, fontSize: 12, textAlign: "center"}}>
                {value}
            </Box>
            <Box sx={{color: 'lightgrey', fontWeight: 'bold', mx: 0.5, fontSize: 12, textAlign: "center"}}>
                {label}
            </Box>
        </Box>
    }

    return(
        <Paper elevation={0}  sx={props.sx} onMouseEnter={() => setShowExpand(true)} onMouseLeave={() => setShowExpand(false)}>

        <Grid container direction="row" justifyContent="flex-start" alignItems="stretch">
            {(!ttmFinancial || expand) &&
                <Tooltip title="Add Quarter Financials">
                    <Button sx={{height: "25px"}} onClick={() => props.setOpenAddDividend(true)}>
                        <ControlPointIcon sx={{color: 'lightgreen'}}/>
                    </Button>
                </Tooltip>
            }
            {ttmFinancial && financial(ttmFinancial.revenue + props.companySelectorValue.currency, ttmFinancialLabels[0])}
            {ttmFinancial && financial(ttmFinancial.netIncome + props.companySelectorValue.currency, ttmFinancialLabels[1])}
            {ttmFinancial && financial(ttmFinancial.netMargin + "%", ttmFinancialLabels[2])}
            {ttmFinancial && financial(ttmFinancial.eps + props.companySelectorValue.currency, ttmFinancialLabels[3])}
            {ttmFinancial && financial(ttmFinancial.ttmPe, ttmFinancialLabels[4])}
            {ttmFinancial && financial(ttmFinancial.forwardPe, ttmFinancialLabels[5])}
            {(showExpand || expand) && ttmFinancial &&
                <Button sx={{height: "25px", width: "150px"}} onClick={() => setExpand(!expand)}>
                    <>{!expand && <ArrowDropDownIcon/>}{expand && <ArrowDropUpIcon/>}</>
                </Button>
            }
        </Grid>

        {expand && ttmFinancial &&
            <TableContainer sx={{ width: "max-content", maxHeight: "200px"}}>
            <Table size="small" aria-label="a dense table" stickyHeader>
                <TableHead>
                    <TableRow>
                        {financialsHeaders.map((column, index) => (
                            <TableCell key={index}>{column}</TableCell>
                        ))}
                    </TableRow>
                </TableHead>
                <TableBody>
                    {financials.map((financial, index) => (
                        <TableRow key={index}>
                            <TableCell key={0} sx={{textAlign: "center"}}>{financial.quarter}</TableCell>
                            <TableCell key={1} sx={{textAlign: "right"}}>{financial.revenue}</TableCell>
                            <TableCell key={2} sx={{textAlign: "right"}}>{financial.netIncome}</TableCell>
                            <TableCell key={3} sx={{textAlign: "right"}}>{financial.netMargin}</TableCell>
                            <TableCell key={4} sx={{textAlign: "right"}}>{financial.eps}</TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
            </TableContainer>
        }

        </Paper>
    )
}
export default Financials