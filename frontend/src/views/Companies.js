import {useData} from "../fetch";
import React, {useState} from "react";
import Loader from "../components/Loader";
import {IconButton, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow} from "@mui/material";
import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import OpenInNewIcon from '@mui/icons-material/OpenInNew';


function headerStyle(index){
    const border = "1px solid lightgrey"
    const borderRight = (index === 8) ? border : "0px"
    return {textAlign: "center", borderLeft: border, borderRight: borderRight, borderBottom: border, borderTop: border}
}

function rowStyle(index){
    const fontWeight = ([].includes(index)) ? "bold" : "normal"
    const textAlign = ([0, 1, 2, 3].includes(index)) ? "left" : "right"
    const borderLeft = "1px solid lightgrey"
    const borderRight = ([8].includes(index)) ? "1px solid lightgrey" : "0px"
    const fontFamily = "Roboto"
    let color = "primary"
    return {fontWeight: fontWeight, textAlign: textAlign, borderLeft: borderLeft, borderRight: borderRight, fontFamily: fontFamily, color: color}
}

const sorts = ["COMPANY", "CURRENCY", "WATCHING", "SECTOR", "ALL_TRADES", "ACTIVE_TRADES", "DIVIDENDS", "RECORDS", "FINANCIALS"]

const Companies = props => {
    const [sort, setSort] = useState(sorts[0])
    const [refresh, setRefresh] = useState("")
    const {data, loaded, error} = useData("/company/aggregate" + constructQueryParams())

    function constructQueryParams(){
        return "?sort=" + sort
            + (props.currencySelectorValue ? "&currency=" + props.currencySelectorValue : "")
            // + (props.sectorSelectorValue ? "&sector=" + props.sectorSelectorValue : "")
            + (refresh ? "&refresh" + refresh : "")
    }

    function triggerRefresh() {
        setRefresh(new Date().getTime().toString())
    }

    function handleAddCompanyDialogClose() {
        // props.setOpenAddCompany(false)
    }

    function TableCellRedirect(props) {
        const [showRedirect, setShowRedirect] = useState(false)
        return(
            <TableCell style={props.style}
                       onMouseEnter={() => setShowRedirect(true)}
                       onMouseLeave={() => setShowRedirect(false)}
            >
                {showRedirect &&
                    <IconButton style={{height: "2px", width: "25px"}}
                        onClick={props.redirect}
                    >
                        <OpenInNewIcon sx={{width: 18}}/>
                    </IconButton>
                }
                {props.children}
            </TableCell>
        )
    }

    function redirect(companyId, href, tradeState, showFinancials) {
        sessionStorage.setItem('companyId', companyId);
        if (tradeState) sessionStorage.setItem('tradeState', tradeState);
        if (showFinancials) sessionStorage.setItem('showFinancials', showFinancials);
        window.location.href=href
    }

    return (
        <>
            {!loaded &&
                <Loader error ={error}/>
            }
            {loaded &&
                <>
                    {/*<AddTradeDialog open={props.openAddTrade}*/}
                    {/*                handleClose={() => handleAddTradeDialogClose()}*/}
                    {/*                triggerRefresh={triggerRefresh}*/}
                    {/*                {...props}*/}
                    {/*/>*/}
                    <TableContainer component={Paper} sx={{ width: "max-content", margin: "10px auto 10px auto", maxHeight: "calc(100vh - 70px)"}}>
                        <Table size="small" aria-label="a dense table" stickyHeader>
                            <TableHead>
                                <TableRow>
                                    {data.columns.map((column, index) => (
                                        <TableCell key={index} style={headerStyle(index)}
                                                   onClick={() => setSort(sorts[index])}
                                        >
                                            {column}
                                            {sorts.indexOf(sort) === index && <ArrowDropDownIcon sx={{ height: "18px", marginRight: "-15px", marginBottom: "-5px"}}/>}
                                        </TableCell>
                                    ))}
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {data.companies.map((company, index) => (
                                    <TableRow key={company.id} hover>
                                        <TableCell style={rowStyle(0)}>{company.ticker}</TableCell>
                                        <TableCell style={rowStyle(1)}>{company.currency}</TableCell>
                                        <TableCell style={rowStyle(2)}>{company.watching ? '*' : ''}</TableCell>
                                        <TableCell style={rowStyle(3)}>{company.sector}</TableCell>
                                        <TableCellRedirect style={rowStyle(4)} redirect={() => redirect(company.id, '/trades')}>{company.totalTrades}</TableCellRedirect>
                                        <TableCellRedirect style={rowStyle(5)} redirect={() => redirect(company.id, '/trades', props.activeStates[0])}>{company.activeTrades}</TableCellRedirect>
                                        <TableCellRedirect style={rowStyle(6)} redirect={() => redirect(company.id, '/dividends')}>{company.dividends}</TableCellRedirect>
                                        <TableCellRedirect style={rowStyle(7)} redirect={() => redirect(company.id, '/records')}>{company.records}</TableCellRedirect>
                                        <TableCellRedirect style={rowStyle(8)} redirect={() => redirect(company.id, '/records', null, true)}>{company.financials}</TableCellRedirect>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </TableContainer>
                </>
            }
        </>
    )
}
export default Companies
