import React, {useState} from "react";
import {Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow} from "@mui/material";
import {useData} from "../service/BackendService";
import {Loader} from "./component/Loader";
import {formatDate, formatDecimals} from "../service/FormattingService";
import {AddTradeDialog} from "../dialog/AddTradeDialog";
import {SellTradeDialog} from "../dialog/SellTradeDialog";
import {ACTIVE_STATES} from "./component/MainBar";

const compactColumnStyle = {width: "35px", minWidth: "35px", maxWidth: "35px", boxSizing: "border-box"}
const centeredCompactColumnStyle = {...compactColumnStyle, textAlign: "center", verticalAlign: "middle", paddingLeft: 0, paddingRight: 0}

export const Trades = props => {
    const [refresh, setRefresh] = useState("")
    const {data, loaded, error} = useData("/trade" + constructQueryParams())

    function constructQueryParams(){
        return "?filter" + (props.activeSelectorValue ? "&active=" + (props.activeSelectorValue === ACTIVE_STATES[0]) : "")
            + (props.companySelectorValue ? "&companyId=" + props.companySelectorValue.id : "")
            + (props.currencySelectorValue ? "&currency=" + props.currencySelectorValue : "")
            + (props.yearSelectorValue ? "&year=" + props.yearSelectorValue : "")
            + (props.sectorSelectorValue ? "&sector=" + props.sectorSelectorValue.key : "")
            + (refresh ? "&refresh" + refresh : "")
    }

    function headerStyle(main, index){
        if (main){
            return {textAlign: "center", border: "1px solid lightgrey"}
        } else {
            const borderRight = (index === 4) ? "1px solid lightgrey" : "0px"
            return {textAlign: "center", borderLeft: "1px solid lightgrey", borderRight: borderRight}
        }
    }

    function rowStyle(index, isProfit){
        const fontWeight = ([0, 1, 13, 14].includes(index)) ? "bold" : "normal"
        const textAlign = ([0, 1, 2, 3, 8].includes(index)) ? "center" : "right"
        const borderLeft = "1px solid lightgrey"
        const borderRight = ([0, 1, 2, 7, 12, 13, 14].includes(index)) ? "1px solid lightgrey" : "0px"
        const fontFamily = "Roboto"
        let color = "primary"
        if (props.activeSelectorValue === ACTIVE_STATES[0]){
            color = (index > 6) ? "#adadad" : color
            if (isProfit !== undefined) color = isProfit ? "#99bb99" : "#d99595"
        }
        return {fontWeight: fontWeight, textAlign: textAlign, borderLeft: borderLeft, borderRight: borderRight, fontFamily: fontFamily, color: color}
    }

    function sumRowStyle(index, isProfit){
        return {...rowStyle(index, isProfit), borderTop: "1px solid grey", borderBottom: "1px solid grey"}
    }

    function selectCompany(ticker) {
        props.companies.forEach((company) => {if (company.ticker === ticker) {props.setCompanySelectorValue(company)}})
    }

    function triggerRefresh() {
        setRefresh(new Date().getTime().toString())
    }

    return (
        <>
        {!loaded &&
            <Loader error ={error}/>
        }
        {loaded &&
            <TableContainer component={Paper} sx={{width: {xs: "100%", sm: "max-content"}, margin: "10px auto 10px auto", maxHeight: "calc(100vh - var(--main-bar-height, 48px) - 32px)", overflow: "auto"}}>
                <AddTradeDialog triggerRefresh={triggerRefresh} {...props}/>
                <SellTradeDialog triggerRefresh={triggerRefresh} {...props}/>
                <Table size="small" aria-label="a dense table" stickyHeader sx={{minWidth: {xs: 1100, sm: "unset"}}}>
                    <TableHead>
                        <TableRow>
                            <TableCell key={0} colSpan={1} rowSpan={2} style={headerStyle(true)}>Ticker</TableCell>
                            <TableCell key={1} colSpan={1} rowSpan={2} style={{...headerStyle(true), ...centeredCompactColumnStyle}}>#</TableCell>
                            <TableCell key={2} colSpan={1} rowSpan={2} style={{...headerStyle(true), ...compactColumnStyle, paddingLeft: "7px", paddingRight: "7px"}}>@</TableCell>
                            <TableCell key={3} colSpan={5} rowSpan={1} style={headerStyle(true)}>Purchase</TableCell>
                            <TableCell key={4} colSpan={5} rowSpan={1} style={headerStyle(true)}>Sale</TableCell>
                            <TableCell key={5} colSpan={1} rowSpan={2} style={headerStyle(true)}>Profit</TableCell>
                            <TableCell key={6} colSpan={1} rowSpan={2} style={headerStyle(true)}>Profit %</TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell key={2.0} style={headerStyle(false, 0)}>Date</TableCell>
                            <TableCell key={2.1} style={headerStyle(false, 1)}>Quantity</TableCell>
                            <TableCell key={2.2} style={headerStyle(false, 2)}>Price</TableCell>
                            <TableCell key={2.3} style={headerStyle(false, 3)}>Fees</TableCell>
                            <TableCell key={2.4} style={headerStyle(false, 4)}>Total</TableCell>
                            <TableCell key={3.0} style={headerStyle(false, 0)}>Date</TableCell>
                            <TableCell key={3.1} style={headerStyle(false, 1)}>Quantity</TableCell>
                            <TableCell key={3.2} style={headerStyle(false, 2)}>Price</TableCell>
                            <TableCell key={3.3} style={headerStyle(false, 3)}>Fees</TableCell>
                            <TableCell key={3.4} style={headerStyle(false, 4)}>Total</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {data.trades.map((trade, index) => (
                            <TableRow key={index} hover>
                                <TableCell style={rowStyle(0)} onDoubleClick={() => selectCompany(trade.company.ticker)}>
                                    {trade.company.ticker}
                                </TableCell>
                                <TableCell style={{...rowStyle(1), ...centeredCompactColumnStyle}}>{trade.company.currency}</TableCell>
                                <TableCell style={{...rowStyle(2), ...compactColumnStyle, paddingLeft: "7px", paddingRight: "7px"}} title={trade.portfolio?.name}>{trade.portfolio?.abbreviation ?? "-"}</TableCell>
                                <TableCell style={rowStyle(3)}>{formatDate(trade.purchaseDate)}</TableCell>
                                <TableCell style={rowStyle(4)}>{trade.purchaseQuantity}</TableCell>
                                <TableCell style={rowStyle(5)}>{formatDecimals(trade.purchasePrice, 0, 4)}</TableCell>
                                <TableCell style={rowStyle(6)}>{formatDecimals(trade.purchaseFees, 0, 2)}</TableCell>
                                <TableCell style={rowStyle(7)}>{formatDecimals(trade.purchaseTotal, 0, 2)}</TableCell>
                                <TableCell style={rowStyle(8)}>{formatDate(trade.sellDate)}</TableCell>
                                <TableCell style={rowStyle(9)}>{trade.sellQuantity}</TableCell>
                                <TableCell style={rowStyle(10)}>{formatDecimals(trade.sellPrice, 0, 4)}</TableCell>
                                <TableCell style={rowStyle(11)}>{formatDecimals(trade.sellFees, 0, 2)}</TableCell>
                                <TableCell style={rowStyle(12)}>{formatDecimals(trade.sellTotal, 0, 2)}</TableCell>
                                <TableCell style={rowStyle(13, Number(trade.profit) > 0)}>{formatDecimals(trade.profit, 0, 2)}</TableCell>
                                <TableCell style={rowStyle(14, Number(trade.profitPercentage) > 0)}>{formatDecimals(trade.profitPercentage, 0, 2)}</TableCell>
                            </TableRow>
                        ))}
                        <TableRow key={-1} >
                            <TableCell key={0} style={sumRowStyle(0)}>{data.aggregates.companies}</TableCell>
                            <TableCell key={1} style={{...sumRowStyle(1), ...centeredCompactColumnStyle}}>{data.aggregates.currencies}</TableCell>
                            <TableCell key={2} style={{...sumRowStyle(1), ...compactColumnStyle}}>{data.aggregates.portfolios}</TableCell>
                            <TableCell key={3} style={sumRowStyle(3)}></TableCell>
                            <TableCell key={4} style={sumRowStyle(4)}></TableCell>
                            <TableCell key={5} style={sumRowStyle(5)}></TableCell>
                            <TableCell key={6} style={sumRowStyle(6)}>{formatDecimals(data.aggregates.purchaseFees, 0, 2)}</TableCell>
                            <TableCell key={7} style={sumRowStyle(7)}>{formatDecimals(data.aggregates.purchaseTotal, 0, 2)}</TableCell>
                            <TableCell key={8} style={sumRowStyle(8)}></TableCell>
                            <TableCell key={9} style={sumRowStyle(9)}></TableCell>
                            <TableCell key={10} style={sumRowStyle(10)}></TableCell>
                            <TableCell key={11} style={sumRowStyle(11)}>{formatDecimals(data.aggregates.sellFees, 0, 2)}</TableCell>
                            <TableCell key={12} style={sumRowStyle(12)}>{formatDecimals(data.aggregates.sellTotal, 0, 2)}</TableCell>
                            <TableCell key={13} style={sumRowStyle(13)}>{formatDecimals(data.aggregates.profit, 0, 2)}</TableCell>
                            <TableCell key={14} style={sumRowStyle(14)}>{formatDecimals(data.aggregates.profitPercentage, 0, 2)}</TableCell>
                        </TableRow>
                    </TableBody>
                </Table>
            </TableContainer>
        }
        </>
    )
}
