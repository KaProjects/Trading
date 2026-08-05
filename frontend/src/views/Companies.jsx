import {useData} from "../service/BackendService";
import React, {useState} from "react";
import {Loader} from "./component/Loader";
import {IconButton, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow} from "@mui/material";
import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import EditNoteIcon from '@mui/icons-material/EditNote';
import {recordEvent} from "../service/utils";
import {EditCompanyDialog} from "../dialog/EditCompanyDialog";
import {ACTIVE_STATES, RESEARCH_TAB} from "./component/MainBar";
import {useNavigate} from "react-router-dom";


export const Companies = props => {
    const navigate = useNavigate()
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

    function triggerRefresh() {
        setRefresh(new Date().getTime().toString())
    }

    function headerStyle(index){
        const border = "1px solid lightgrey"
        const borderRight = (index === 7) ? border : "0px"
        return {textAlign: "center", borderLeft: border, borderRight: borderRight, borderBottom: border, borderTop: border}
    }

    function rowStyle(index){
        const fontWeight = ([].includes(index)) ? "bold" : "normal"
        const textAlign = ([0, 1, 2].includes(index)) ? "left" : "right"
        const borderLeft = "1px solid lightgrey"
        const borderRight = ([7].includes(index)) ? "1px solid lightgrey" : "0px"
        const fontFamily = "Roboto"
        let color = "primary"
        return {fontWeight: fontWeight, textAlign: textAlign, borderLeft: borderLeft, borderRight: borderRight, fontFamily: fontFamily, color: color}
    }

    function redirect(companyId, href, tradeState, researchTab) {
        recordEvent(window.location.pathname + "#redirect:" + href);
        const state = {companyId}
        if (href === "/trades") state.tradeState = tradeState ?? ""
        if (researchTab !== undefined) state.researchTab = researchTab

        navigate(href, {
            state,
        })
    }

    function TableCellWithAction({index, value, action}) {
        const [showAction, setShowAction] = useState(false)
        return(
            <TableCell style={rowStyle(index)}
                       onMouseEnter={() => {if (action) setShowAction(true)}}
                       onMouseLeave={() => {if (action) setShowAction(false)}}
            >
                {index > 2 && showAction &&
                    <IconButton style={{height: "18px", width: "18px", marginRight: "1px"}} onClick={action}>
                        <OpenInNewIcon sx={{width: 16}}/>
                    </IconButton>
                }
                {value}
                {index === 0 && showAction &&
                    <IconButton style={{height: "18px", width: "18px", marginRight: "-10px"}} onClick={action}>
                        <EditNoteIcon sx={{width: 16}}/>
                    </IconButton>
                }
            </TableCell>
        )
    }

    function HeaderCell({index, value}) {
        return <TableCell key={index} style={headerStyle(index)} onClick={() => setSort(data.sorts[index])}>
            {value}
            {sort === data.sorts[index] && <ArrowDropDownIcon sx={{ height: "18px", marginRight: "-15px", marginBottom: "-5px"}}/>}
        </TableCell>
    }

    return (
        <>
            {!loaded && <Loader error={error}/>}
            {loaded &&
                <>
                    <EditCompanyDialog triggerRefresh={triggerRefresh} {...props}/>
                    <TableContainer component={Paper} sx={{width: {xs: "100%", sm: "max-content"}, margin: "10px auto 10px auto", maxHeight: "calc(100vh - var(--main-bar-height, 48px) - 32px)", overflow: "auto"}}>
                        <Table size="small" aria-label="a dense table" stickyHeader sx={{minWidth: {xs: 760, sm: "unset"}}}>
                            <TableHead>
                                <TableRow>
                                    <HeaderCell index={0} value={"Ticker"}/>
                                    <HeaderCell index={1} value={"#"}/>
                                    <HeaderCell index={2} value={"Sector"}/>
                                    <HeaderCell index={3} value={"Total Trades"}/>
                                    <HeaderCell index={4} value={"Active Trades"}/>
                                    <HeaderCell index={5} value={"Dividends"}/>
                                    <HeaderCell index={6} value={"Records"}/>
                                    <HeaderCell index={7} value={"Periods"}/>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {data.companies.map((company, index) => (
                                    <TableRow key={company.id} hover>
                                        <TableCellWithAction index={0} action={() => props.setOpenEditCompany(company)} value={company.ticker}/>
                                        <TableCellWithAction index={1} value={company.currency}/>
                                        <TableCellWithAction index={2} value={company.sector ? company.sector.name : ''}/>
                                        <TableCellWithAction index={3} value={company.totalTrades} action={() => redirect(company.id, '/trades')}/>
                                        <TableCellWithAction index={4} value={company.activeTrades} action={() => redirect(company.id, '/trades', ACTIVE_STATES[0])}/>
                                        <TableCellWithAction index={5} value={company.dividends} action={() => redirect(company.id, '/dividends')}/>
                                        <TableCellWithAction index={6} value={company.records} action={() => redirect(company.id, '/research', null, RESEARCH_TAB.records)}/>
                                        <TableCellWithAction index={7} value={company.periods} action={() => redirect(company.id, '/research', null, RESEARCH_TAB.research)}/>
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
