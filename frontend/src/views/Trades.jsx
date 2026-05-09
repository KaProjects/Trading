import React, {useEffect, useState} from "react";
import {Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow} from "@mui/material";
import {useData} from "../service/BackendService";
import Loader from "../components/Loader";
import AddTradeDialog from "../dialog/AddTradeDialog";
import SellTradeDialog from "../dialog/SellTradeDialog";
import {formatDate} from "../service/FormattingService";


export const Trades = props => {
    const [refresh, setRefresh] = useState("")
    const {data, loaded, error} = useData("/trade" + constructQueryParams())

    function constructQueryParams(){
        return "?filter" + (props.activeSelectorValue ? "&active=" + (props.activeSelectorValue === props.activeStates[0]) : "")
            + (props.companySelectorValue ? "&companyId=" + props.companySelectorValue.id : "")
            + (props.currencySelectorValue ? "&currency=" + props.currencySelectorValue : "")
            + (props.yearSelectorValue ? "&year=" + props.yearSelectorValue : "")
            + (props.sectorSelectorValue ? "&sector=" + props.sectorSelectorValue.key : "")
            + (refresh ? "&refresh" + refresh : "")
    }

    useEffect(() => {
        if (data && !props.showYearSelector) {
            const years = new Set([])
            data.trades.forEach((trade) => {
                years.add(trade.purchaseDate.split(".")[2])
                if (trade.sellDate) years.add(trade.sellDate.split(".")[2])
            })
            props.toggleTradesSelectors([...years].sort().reverse())
        }
        // eslint-disable-next-line
    }, [data])

    function headerStyle(main, index){
        if (main){
            return {textAlign: "center", border: "1px solid lightgrey"}
        } else {
            const borderRight = (index === 4) ? "1px solid lightgrey" : "0px"
            return {textAlign: "center", borderLeft: "1px solid lightgrey", borderRight: borderRight}
        }
    }

    function rowStyle(index, isProfit){
        const fontWeight = ([0, 1, 12, 13].includes(index)) ? "bold" : "normal"
        const textAlign = ([0, 1, 2, 7].includes(index)) ? "center" : "right"
        const borderLeft = "1px solid lightgrey"
        const borderRight = ([0, 1, 6, 11, 12, 13].includes(index)) ? "1px solid lightgrey" : "0px"
        const fontFamily = "Roboto"
        let color = "primary"
        if (props.activeSelectorValue === props.activeStates[0]){
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
            <TableContainer component={Paper} sx={{ width: "max-content", margin: "10px auto 10px auto", maxHeight: "calc(100vh - 70px)"}}>
                <AddTradeDialog triggerRefresh={triggerRefresh} {...props}/>
                <SellTradeDialog triggerRefresh={triggerRefresh} {...props}/>
                <Table size="small" aria-label="a dense table" stickyHeader>
                    <TableHead>
                        <TableRow>
                            <TableCell key={0} colSpan={1} rowSpan={2} style={headerStyle(true)}>Ticker</TableCell>
                            <TableCell key={1} colSpan={1} rowSpan={2} style={headerStyle(true)}>#</TableCell>
                            <TableCell key={2} colSpan={5} rowSpan={1} style={headerStyle(true)}>Purchase</TableCell>
                            <TableCell key={3} colSpan={5} rowSpan={1} style={headerStyle(true)}>Sale</TableCell>
                            <TableCell key={4} colSpan={1} rowSpan={2} style={headerStyle(true)}>Profit</TableCell>
                            <TableCell key={5} colSpan={1} rowSpan={2} style={headerStyle(true)}>Profit %</TableCell>
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
                        {data.trades.map((trade) => (
                            <TableRow key={trade.id} hover>
                                <TableCell style={rowStyle(0)} onDoubleClick={() => selectCompany(trade.ticker)}>
                                    {trade.ticker}
                                </TableCell>
                                <TableCell style={rowStyle(1)}>{trade.currency}</TableCell>
                                <TableCell style={rowStyle(2)}>{formatDate(trade.purchaseDate)}</TableCell>
                                <TableCell style={rowStyle(3)}>{trade.purchaseQuantity}</TableCell>
                                <TableCell style={rowStyle(4)}>{trade.purchasePrice}</TableCell>
                                <TableCell style={rowStyle(5)}>{trade.purchaseFees}</TableCell>
                                <TableCell style={rowStyle(6)}>{trade.purchaseTotal}</TableCell>
                                <TableCell style={rowStyle(7)}>{formatDate(trade.sellDate)}</TableCell>
                                <TableCell style={rowStyle(8)}>{trade.sellQuantity}</TableCell>
                                <TableCell style={rowStyle(9)}>{trade.sellPrice}</TableCell>
                                <TableCell style={rowStyle(10)}>{trade.sellFees}</TableCell>
                                <TableCell style={rowStyle(11)}>{trade.sellTotal}</TableCell>
                                <TableCell style={rowStyle(12, Number(trade.profit) > 0)}>{trade.profit}</TableCell>
                                <TableCell style={rowStyle(13, Number(trade.profitPercentage) > 0)}>{trade.profitPercentage}</TableCell>
                            </TableRow>
                        ))}
                        <TableRow key={-1} >
                            <TableCell key={0} style={sumRowStyle(0)}>{data.aggregates.companies}</TableCell>
                            <TableCell key={1} style={sumRowStyle(1)}>{data.aggregates.currencies}</TableCell>
                            <TableCell key={2} style={sumRowStyle(2)}></TableCell>
                            <TableCell key={3} style={sumRowStyle(3)}></TableCell>
                            <TableCell key={4} style={sumRowStyle(4)}></TableCell>
                            <TableCell key={5} style={sumRowStyle(5)}>{data.aggregates.purchaseFees}</TableCell>
                            <TableCell key={6} style={sumRowStyle(6)}>{data.aggregates.purchaseTotal}</TableCell>
                            <TableCell key={7} style={sumRowStyle(7)}></TableCell>
                            <TableCell key={8} style={sumRowStyle(8)}></TableCell>
                            <TableCell key={9} style={sumRowStyle(9)}></TableCell>
                            <TableCell key={10} style={sumRowStyle(10)}>{data.aggregates.sellFees}</TableCell>
                            <TableCell key={11} style={sumRowStyle(11)}>{data.aggregates.sellTotal}</TableCell>
                            <TableCell key={12} style={sumRowStyle(12)}>{data.aggregates.profit}</TableCell>
                            <TableCell key={13} style={sumRowStyle(13)}>{data.aggregates.profitPercentage}</TableCell>
                        </TableRow>
                    </TableBody>
                </Table>
            </TableContainer>
        }
        </>
    )
}