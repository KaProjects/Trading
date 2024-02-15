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
import AddFinancialDialog from "../dialog/AddFinancialDialog";


const Financials = props => {
    const {financials, expand, setExpand} = props
    const [showExpand, setShowExpand] = useState(false)
    const [openAddFinancialDialog, setOpenAddFinancialDialog] = useState(false)

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

        <AddFinancialDialog open={openAddFinancialDialog}
                            handleClose={() => setOpenAddFinancialDialog(false)}
                            companyId={props.companySelectorValue.id}
                            {...props}
        />

        <Grid container direction="row" justifyContent="flex-start" alignItems="stretch">
            {financials.ttm && financial(financials.ttm.revenue + props.companySelectorValue.currency, financials.ttmLabels[0])}
            {financials.ttm && financial(financials.ttm.netIncome + props.companySelectorValue.currency, financials.ttmLabels[1])}
            {financials.ttm && financial(financials.ttm.netMargin + "%", financials.ttmLabels[2])}
            {financials.ttm && financial(financials.ttm.eps + props.companySelectorValue.currency, financials.ttmLabels[3])}
            {financials.ttm && financial(financials.ttm.ttmPe, financials.ttmLabels[4])}
            {financials.ttm && financial(financials.ttm.forwardPe, financials.ttmLabels[5])}
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
                        {financials.headers.map((column, index) => (
                            <TableCell key={index}>{column}</TableCell>
                        ))}
                    </TableRow>
                </TableHead>
                <TableBody>
                    {financials.values.map((financial, index) => (
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