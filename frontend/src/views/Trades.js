import React, {useEffect} from "react";
import {Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow} from "@mui/material";
import {useData} from "../fetch";
import Loader from "../components/Loader";


function headerStyle(main, index){
    if (main){
        return {textAlign: "center", border: "1px solid lightgrey"}
    } else {
        const borderRight = (index === 4) ? "1px solid lightgrey" : "0px"
        return {textAlign: "center", borderLeft: "1px solid lightgrey", borderRight: borderRight}
    }
}

function rowStyle(index){
    const fontWeight = ([0, 1, 12, 13].includes(index)) ? "bold" : "normal"
    const textAlign = ([0, 1, 2, 7].includes(index)) ? "center" : "right"
    const borderLeft = "1px solid lightgrey"
    const borderRight = ([0, 1, 6, 11, 12, 13].includes(index)) ? "1px solid lightgrey" : "0px"
    const fontFamily = "Roboto"

    return {fontWeight: fontWeight, textAlign: textAlign, borderLeft: borderLeft, borderRight: borderRight, fontFamily: fontFamily}
}

const activeStates = ["only active", "only closed"]

const Trades = props => {

    const {data, loaded, error} = useData("/trade" + constructQueryParams())

    function constructQueryParams(){
        return "?filter" + (props.activeSelectorValue ? "&active=" + (props.activeSelectorValue === activeStates[0]) : "")
            + (props.companySelectorValue ? "&companyId="+props.companySelectorValue.id : "")
            + (props.currencySelectorValue ? "&currency="+props.currencySelectorValue : "")
            + (props.yearSelectorValue ? "&year="+props.yearSelectorValue : "")
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
    }, [data]);

    return (
        <>
        {!loaded &&
            <Loader error ={error}/>
        }
        {loaded &&
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
                            <TableRow key={index} hover>
                                <TableCell style={rowStyle(0)}>{trade.ticker}</TableCell>
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
                                <TableCell style={rowStyle(12)}>{trade.profit}</TableCell>
                                <TableCell style={rowStyle(13)}>{trade.profitPercentage}</TableCell>
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
        }
        </>
    )
}
export default Trades;