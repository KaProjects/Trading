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
        props.refreshCompanyLists?.()
    }

    function headerStyle(index){
        const border = "1px solid lightgrey"
        const borderRight = (index === 11) ? border : "0px"
        return {textAlign: "center", borderLeft: border, borderRight: borderRight, borderBottom: border, borderTop: border}
    }

    function rowStyle(index){
        const fontWeight = ([].includes(index)) ? "bold" : "normal"
        const textAlign = ([0, 1, 7, 9, 10, 11].includes(index)) ? "left" : (index === 8 ? "center" : "right")
        const borderLeft = "1px solid lightgrey"
        const borderRight = ([11].includes(index)) ? "1px solid lightgrey" : "0px"
        const fontFamily = "Roboto"
        let color = "primary"
        return {fontWeight: fontWeight, textAlign: textAlign, borderLeft: borderLeft, borderRight: borderRight, fontFamily: fontFamily, color: color}
    }

    function redirect(companyTicker, href, tradeState, researchTab) {
        recordEvent(window.location.pathname + "#redirect:" + href);
        const state = {}
        if (href === "/trades") state.tradeState = tradeState ?? ""
        if (researchTab !== undefined) state.researchTab = researchTab

        navigate({
            pathname: href,
            search: `?${new URLSearchParams({company: companyTicker})}`,
        }, {
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
                {index !== 0 && showAction &&
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

    function HeaderCell({index, value, sortValue}) {
        return <TableCell
            key={index}
            style={{...headerStyle(index), cursor: sortValue ? "pointer" : "default"}}
            onClick={() => {if (sortValue) setSort(sortValue)}}
        >
            {value}
            {sort === sortValue && <ArrowDropDownIcon sx={{ height: "18px", marginRight: "-15px", marginBottom: "-5px"}}/>}
        </TableCell>
    }

    return (
        <>
            {!loaded && <Loader error={error}/>}
            {loaded &&
                <>
                    <EditCompanyDialog triggerRefresh={triggerRefresh} {...props}/>
                    <TableContainer component={Paper} sx={{width: {xs: "100%", sm: "max-content"}, margin: "10px auto 10px auto", maxHeight: "calc(100vh - var(--main-bar-height, 48px) - 32px)", overflow: "auto"}}>
                        <Table size="small" aria-label="a dense table" stickyHeader sx={{minWidth: {xs: 1450, sm: "unset"}}}>
                            <TableHead>
                                <TableRow>
                                    <HeaderCell index={0} value={"Ticker"} sortValue={data.sorts[0]}/>
                                    <HeaderCell index={1} value={"#"} sortValue={data.sorts[1]}/>
                                    <HeaderCell index={2} value={"Total Trades"} sortValue={data.sorts[3]}/>
                                    <HeaderCell index={3} value={"Active Trades"} sortValue={data.sorts[4]}/>
                                    <HeaderCell index={4} value={"Dividends"} sortValue={data.sorts[5]}/>
                                    <HeaderCell index={5} value={"Records"} sortValue={data.sorts[6]}/>
                                    <HeaderCell index={6} value={"Periods"} sortValue={data.sorts[7]}/>
                                    <HeaderCell index={7} value={"Name"}/>
                                    <HeaderCell index={8} value={"Logo"}/>
                                    <HeaderCell index={9} value={"Sector"} sortValue={data.sorts[2]}/>
                                    <HeaderCell index={10} value={"Website"}/>
                                    <HeaderCell index={11} value={"Description"}/>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {data.companies.map((company, index) => (
                                    <TableRow key={company.id} hover>
                                        <TableCellWithAction index={0} action={() => props.setOpenEditCompany(company)} value={company.ticker}/>
                                        <TableCellWithAction index={1} value={company.currency}/>
                                        <TableCellWithAction index={2} value={company.totalTrades} action={() => redirect(company.ticker, '/trades')}/>
                                        <TableCellWithAction index={3} value={company.activeTrades} action={() => redirect(company.ticker, '/trades', ACTIVE_STATES[0])}/>
                                        <TableCellWithAction index={4} value={company.dividends} action={() => redirect(company.ticker, '/dividends')}/>
                                        <TableCellWithAction index={5} value={company.records} action={() => redirect(company.ticker, '/research', null, RESEARCH_TAB.records)}/>
                                        <TableCellWithAction index={6} value={company.periods} action={() => redirect(company.ticker, '/research', null, RESEARCH_TAB.research)}/>
                                        <TableCellWithAction index={7} value={company.name ?? ""}/>
                                        <TableCellWithAction index={8} value={company.logoUrl
                                            ? <img src={company.logoUrl} alt={`${company.ticker} logo`} style={{display: "block", width: 28, height: 28, objectFit: "contain", margin: "auto"}}/>
                                            : ""}/>
                                        <TableCellWithAction index={9} value={company.sector ? company.sector.name : ''}/>
                                        <TableCellWithAction index={10} value={company.website
                                            ? <a href={company.website} target="_blank" rel="noreferrer">{company.website}</a>
                                            : ""}/>
                                        <TableCellWithAction index={11} value={company.description ?? ""}/>
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
