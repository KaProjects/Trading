import React, {useEffect, useState} from "react";
import {Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow} from "@mui/material";
import {useData} from "../fetch";
import Loader from "../components/Loader";
import AddTradeDialog from "../components/AddTradeDialog";
import SellTradeDialog from "../components/SellTradeDialog";


function headerStyle(main, index){
    if (main){
        return {textAlign: "center", border: "1px solid lightgrey"}
    } else {
        const borderRight = (index === 4) ? "1px solid lightgrey" : "0px"
        return {textAlign: "center", borderLeft: "1px solid lightgrey", borderRight: borderRight}
    }
}

const activeStates = ["only active", "only closed"]

const Trades = props => {
    const [refresh, setRefresh] = useState("")
    const {data, loaded, error} = useData("/trade" + constructQueryParams())

    function constructQueryParams(){
        return "?filter" + (props.activeSelectorValue ? "&active=" + (props.activeSelectorValue === activeStates[0]) : "")
            + (props.companySelectorValue ? "&companyId="+props.companySelectorValue.id : "")
            + (props.currencySelectorValue ? "&currency="+props.currencySelectorValue : "")
            + (props.yearSelectorValue ? "&year="+props.yearSelectorValue : "")
            + (refresh ? "&refresh" + refresh : "")
    }

    useEffect(() => {
        if (data) {
            const years = new Set([]);
            const currencies = new Set([]);
            data.trades.forEach((trade) => {
                years.add(trade.purchaseDate.split(".")[2])
                if (trade.sellDate) years.add(trade.sellDate.split(".")[2])
                currencies.add(trade.currency)
            })
            props.toggleTradesSelectors(activeStates, [...currencies],[...years].sort().reverse())
        }
        // eslint-disable-next-line
    }, [data])

    function rowStyle(index, isProfit){
        const fontWeight = ([0, 1, 12, 13].includes(index)) ? "bold" : "normal"
        const textAlign = ([0, 1, 2, 7].includes(index)) ? "center" : "right"
        const borderLeft = "1px solid lightgrey"
        const borderRight = ([0, 1, 6, 11, 12, 13].includes(index)) ? "1px solid lightgrey" : "0px"
        const fontFamily = "Roboto"
        let color = "primary"
        if (props.activeSelectorValue === activeStates[0]){
            color = (index > 6) ? "#adadad" : color
            if (isProfit !== undefined) color = isProfit ? "#99bb99" : "#d99595"
        }
        return {fontWeight: fontWeight, textAlign: textAlign, borderLeft: borderLeft, borderRight: borderRight, fontFamily: fontFamily, color: color}
    }

    function selectCompany(ticker) {
        props.companies.forEach((company) => {if (company.ticker === ticker) {props.setCompanySelectorValue(company)}})
    }

    function handleAddTradeDialogClose() {
        setRefresh(new Date().getTime().toString())
        props.setOpenAddTrade(false)
    }

    function handleSellTradeDialogClose() {
        setRefresh(new Date().getTime().toString())
        props.setOpenSellTrade(false)
    }

    return (
        <>
        {!loaded &&
            <Loader error ={error}/>
        }
        {loaded &&
            <>
            <AddTradeDialog open={props.openAddTrade}
                            handleClose={() => handleAddTradeDialogClose()}
                            {...props}
            />
            <SellTradeDialog open={props.openSellTrade}
                            handleClose={() => handleSellTradeDialogClose()}
                            {...props}
            />
            <TableContainer component={Paper} sx={{ width: "max-content", margin: "10px auto 10px auto", maxHeight: "calc(100vh - 70px)"}}>
                <Table size="small" aria-label="a dense table" stickyHeader>
                    <TableHead>
                        <TableRow>
                        {data.columns.map((column, index) => (
                            <TableCell key={index} style={headerStyle(true)}
                                       colSpan={column.subColumns.length !== 0 ? column.subColumns.length : 1}
                                       rowSpan={column.subColumns.length === 0 ? 2 : 1}
                            >
                                {column.name}
                            </TableCell>
                        ))}
                        </TableRow>
                        <TableRow>
                        {data.columns.map((column, index) => (
                            <React.Fragment key={index}>
                                {column.subColumns.map((subColumn, index) => (
                                    <TableCell key={index} style={headerStyle(false, index)}>{subColumn}</TableCell>
                                ))}
                            </React.Fragment>
                        ))}
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {data.trades.map((trade, index) => (
                            <TableRow key={trade.id} hover>
                                <TableCell style={rowStyle(0)} onDoubleClick={() => selectCompany(trade.ticker)}>
                                    {trade.ticker}
                                </TableCell>
                                <TableCell style={rowStyle(1)}>{trade.currency}</TableCell>
                                <TableCell style={rowStyle(2)}>{trade.purchaseDate}</TableCell>
                                <TableCell style={rowStyle(3)}>{trade.purchaseQuantity}</TableCell>
                                <TableCell style={rowStyle(4)}>{trade.purchasePrice}</TableCell>
                                <TableCell style={rowStyle(5)}>{trade.purchaseFees}</TableCell>
                                <TableCell style={rowStyle(6)}>{trade.purchaseTotal}</TableCell>
                                <TableCell style={rowStyle(7)}>{trade.sellDate}</TableCell>
                                <TableCell style={rowStyle(8)}>{trade.sellQuantity}</TableCell>
                                <TableCell style={rowStyle(9)}>{trade.sellPrice}</TableCell>
                                <TableCell style={rowStyle(10)}>{trade.sellFees}</TableCell>
                                <TableCell style={rowStyle(11)}>{trade.sellTotal}</TableCell>
                                <TableCell style={rowStyle(12, Number(trade.profit) > 0)}>{trade.profit}</TableCell>
                                <TableCell style={rowStyle(13, Number(trade.profitPercentage) > 0)}>{trade.profitPercentage}</TableCell>
                            </TableRow>
                        ))}
                        <TableRow key={-1} >
                            {data.sums.map((sum, index) => (
                                <TableCell key={index} style={Object.assign(rowStyle(index), {borderTop: "1px solid grey", borderBottom: "1px solid grey"})}>
                                    {sum}
                                </TableCell>
                            ))}
                        </TableRow>
                    </TableBody>
                </Table>
            </TableContainer>
            </>
        }
        </>
    )
}
export default Trades;