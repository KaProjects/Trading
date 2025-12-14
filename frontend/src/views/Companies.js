import {useData} from "../service/BackendService";
import React, {useEffect, useState} from "react";
import Loader from "../components/Loader";
import {IconButton, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow} from "@mui/material";
import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import EditNoteIcon from '@mui/icons-material/EditNote';
import EditCompanyDialog from "../dialog/EditCompanyDialog";
import {recordEvent} from "../service/utils";


function headerStyle(index){
    const border = "1px solid lightgrey"
    const borderRight = (index === 9) ? border : "0px"
    return {textAlign: "center", borderLeft: border, borderRight: borderRight, borderBottom: border, borderTop: border}
}

function rowStyle(index){
    const fontWeight = ([].includes(index)) ? "bold" : "normal"
    const textAlign = ([0, 1, 2, 3].includes(index)) ? "left" : "right"
    const borderLeft = "1px solid lightgrey"
    const borderRight = ([9].includes(index)) ? "1px solid lightgrey" : "0px"
    const fontFamily = "Roboto"
    let color = "primary"
    return {fontWeight: fontWeight, textAlign: textAlign, borderLeft: borderLeft, borderRight: borderRight, fontFamily: fontFamily, color: color}
}

const Companies = props => {
    const [sort, setSort] = useState(null)
    const [refresh, setRefresh] = useState("")
    const {data, loaded, error} = useData("/company" + constructQueryParams())

    function constructQueryParams(){
        return "?query"
            + (props.currencySelectorValue ? "&currency=" + props.currencySelectorValue : "")
            + (props.sectorSelectorValue ? "&sector=" + props.sectorSelectorValue.key : "")
            + (sort ? "&sort=" + sort : "")
            + (refresh ? "&refresh" + refresh : "")
    }

    useEffect(() => {
        if (data) {
            props.toggleCompaniesSelectors()
        }
        // eslint-disable-next-line
    }, [data])

    function triggerRefresh() {
        setRefresh(new Date().getTime().toString())
    }

    function TableCellWithAction(props) {
        const {index, action} = props
        const [showAction, setShowAction] = useState(false)
        return(
            <TableCell style={rowStyle(index)}
                       onMouseEnter={() => setShowAction(true)}
                       onMouseLeave={() => setShowAction(false)}
            >
                {index > 3 && showAction &&
                    <IconButton style={{height: "18px", width: "18px", marginRight: "1px"}} onClick={action}>
                        <OpenInNewIcon sx={{width: 16}}/>
                    </IconButton>
                }
                {props.children}
                {index === 0 && showAction &&
                    <IconButton style={{height: "18px", width: "18px", marginRight: "-10px"}} onClick={action}>
                        <EditNoteIcon sx={{width: 16}}/>
                    </IconButton>
                }
            </TableCell>
        )
    }

    function redirect(companyId, href, tradeState, showFinancials) {
        sessionStorage.setItem('companyId', companyId);
        if (tradeState) sessionStorage.setItem('tradeState', tradeState);
        if (showFinancials) sessionStorage.setItem('showFinancials', showFinancials);
        recordEvent(window.location.pathname + "#redirect:" + href);
        window.location.href=href
    }

    return (
        <>
            {!loaded &&
                <Loader error={error}/>
            }
            {loaded &&
                <>
                    <EditCompanyDialog triggerRefresh={triggerRefresh} {...props}/>
                    <TableContainer component={Paper} sx={{ width: "max-content", margin: "10px auto 10px auto", maxHeight: "calc(100vh - 70px)"}}>
                        <Table size="small" aria-label="a dense table" stickyHeader>
                            <TableHead>
                                <TableRow>
                                    {data.columns.map((column, index) => (
                                        <TableCell key={index} style={headerStyle(index)}
                                                   onClick={() => setSort(data.sorts[index])}
                                        >
                                            {column}
                                            {data && data.sorts.indexOf(sort) === index && <ArrowDropDownIcon sx={{ height: "18px", marginRight: "-15px", marginBottom: "-5px"}}/>}
                                        </TableCell>
                                    ))}
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {data.companies.map((company, index) => (
                                    <TableRow key={company.id} hover>
                                        <TableCellWithAction index={0} action={() => props.setOpenEditCompany(company)}>{company.ticker}</TableCellWithAction>
                                        <TableCell style={rowStyle(1)}>{company.currency}</TableCell>
                                        <TableCell style={rowStyle(2)}>{company.watching ? '*' : ''}</TableCell>
                                        <TableCell style={rowStyle(3)}>{company.sector ? company.sector.name : ''}</TableCell>
                                        <TableCellWithAction index={4} action={() => redirect(company.id, '/trades')}>{company.totalTrades}</TableCellWithAction>
                                        <TableCellWithAction index={5} action={() => redirect(company.id, '/trades', props.activeStates[0])}>{company.activeTrades}</TableCellWithAction>
                                        <TableCellWithAction index={6} action={() => redirect(company.id, '/dividends')}>{company.dividends}</TableCellWithAction>
                                        <TableCellWithAction index={7} action={() => redirect(company.id, '/research')}>{company.records}</TableCellWithAction>
                                        <TableCellWithAction index={8} action={() => redirect(company.id, '/research', null, true)}>{company.financials}</TableCellWithAction>
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
