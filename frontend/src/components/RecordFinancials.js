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
import AddRecordFinancialDialog from "../dialog/AddRecordFinancialDialog";


const RecordFinancials = props => {
    const {financials, expand, setExpand} = props
    const [showExpand, setShowExpand] = useState(false)
    const [openAddFinancialDialog, setOpenAddFinancialDialog] = useState(false)

    const headers = ["Quarter", "Revenue", "Gross Profit", "Operating Income", "Net Income"]
    const labels = ["revenue", "gross profit", "operating income", "net income"]

    function boxWithMarginAndLabel(value, label, margin){
        return <Box sx={{marginLeft: "10px"}}>
            <Box sx={{fontSize: 9, textAlign: "center", marginBottom: "0px"}}>({margin}%)</Box>
            <Box sx={{fontWeight: 'bold', fontSize: 13, textAlign: "center"}}>{value}</Box>
            <Box sx={{color: 'lightgrey', fontWeight: 'bold', mx: 0.5, fontSize: 12, textAlign: "center"}}>{label}</Box>
        </Box>
    }

    function cellWithMargin(value, margin) {
        return <Box sx={{display: 'flex', flexWrap: 'nowrap', justifyContent: 'flex-end', alignItems: 'flex-end'}}>
            <Box sx={{fontSize: 14, textAlign: "right", marginRight: "3px"}}>{value}</Box>
            <Box sx={{fontSize: 12, textAlign: "right", marginBottom: "0px"}}>({margin}%)</Box>
        </Box>
    }

    return(
        <Paper elevation={0}  sx={props.sx} onMouseEnter={() => setShowExpand(true)} onMouseLeave={() => setShowExpand(false)}>

            <AddRecordFinancialDialog open={openAddFinancialDialog}
                                handleClose={() => setOpenAddFinancialDialog(false)}
                                companyId={props.companySelectorValue.id}
                                {...props}
            />

            <Grid container direction="row" justifyContent="flex-start" alignItems="stretch">
                {financials.ttm && boxWithMarginAndLabel(financials.ttm.revenue, labels[0], 100)}
                {financials.ttm && boxWithMarginAndLabel(financials.ttm.grossProfit, labels[1], financials.ttm.grossMargin)}
                {financials.ttm && boxWithMarginAndLabel(financials.ttm.operatingIncome, labels[2], financials.ttm.operatingMargin)}
                {financials.ttm && boxWithMarginAndLabel(financials.ttm.netIncome, labels[3], financials.ttm.netMargin)}
                {(showExpand || expand) && financials.ttm &&
                    <Button sx={{height: "25px"}} onClick={() => setExpand(!expand)}>
                        <>{!expand && <ArrowDropDownIcon/>}{expand && <ArrowDropUpIcon/>}</>
                    </Button>
                }
                {(!financials.ttm || expand) &&
                    <Tooltip title="Add Quarter Financials">
                        <Button sx={{height: "25px"}} onClick={() => setOpenAddFinancialDialog(true)}>
                            <ControlPointIcon sx={{color: 'lightgreen'}}/>
                        </Button>
                    </Tooltip>
                }
            </Grid>

            {expand && financials.ttm &&
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
                            {financials.values.map((financial, index) => (
                                <TableRow key={index}>
                                    <TableCell key={0} sx={{textAlign: "center"}}>{financial.quarter}</TableCell>
                                    <TableCell key={1} sx={{textAlign: "right"}}>{financial.revenue}</TableCell>
                                    <TableCell key={2} sx={{textAlign: "right"}}>{cellWithMargin(financial.grossProfit, financial.grossMargin)}</TableCell>
                                    <TableCell key={3} sx={{textAlign: "right"}}>{cellWithMargin(financial.operatingIncome, financial.operatingMargin)}</TableCell>
                                    <TableCell key={4} sx={{textAlign: "right"}}>{cellWithMargin(financial.netIncome, financial.netMargin)}</TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </TableContainer>
            }

        </Paper>
    )
}
export default RecordFinancials