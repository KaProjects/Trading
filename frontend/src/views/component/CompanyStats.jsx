import React, {useState} from "react";
import {Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow} from "@mui/material";
import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import {useData} from "../../service/BackendService";
import {Loader} from "./Loader";
import {formatDecimals} from "../../service/FormattingService";

const headerStyle = {textAlign: "center", border: "1px solid lightgrey"}

export const CompanyStats = props => {
    const {type} = props
    const [sort, setSort] = useState(null)
    const {data, loaded, error} = useData("/stats/" + type + constructQueryParams())

    function constructQueryParams(){
        return "?query"
            + (props.yearSelectorValue ? "&year=" + props.yearSelectorValue : "")
            + (props.sectorSelectorValue ? "&sector=" + props.sectorSelectorValue.key : "")
            + (sort ? "&sort=" + sort : "")
    }

    function rowStyle(index) {
        const fontWeight = ([6, 7].includes(index)) ? "bold" : "normal"
        const textAlign = ([0, 1].includes(index)) ? "center" : "right"
        const border = "1px solid lightgrey"
        const fontFamily = "Roboto"
        const color = "primary"
        return {fontWeight: fontWeight, textAlign: textAlign, borderLeft: border, borderRight: border, fontFamily: fontFamily, color: color}
    }

    function BodyCell({index, value}) {
        return <TableCell key={index} style={rowStyle(index)}>{value}</TableCell>
    }

    function HeaderCell({index, value}) {
        return <TableCell key={index} style={headerStyle} onClick={() => setSort(data.sorts[index])}>
            {value}
            {sort === data.sorts[index] && <ArrowDropDownIcon sx={{ height: "18px", marginRight: "-15px", marginBottom: "-5px"}}/>}
        </TableCell>
    }

    function SumCell({index, value}) {
        const style = {...rowStyle(index), borderTop: "1px solid grey", borderBottom: "1px solid grey"}
        return <TableCell key={index} style={style}>{value}</TableCell>
    }

    return (
        <>
        {!loaded && <Loader error ={error}/>}
        {loaded &&
            <TableContainer component={Paper} sx={{ width: "max-content", margin: "10px auto 10px auto", maxHeight: "calc(100vh - 70px)"}}>
                <Table size="small" aria-label="a dense table" stickyHeader>
                    <TableHead>
                        <TableRow>
                            <HeaderCell index={0} value={"Ticker"}/>
                            <HeaderCell index={1} value={"#"}/>
                            <HeaderCell index={2} value={"Purchases"}/>
                            <HeaderCell index={3} value={"Sells"}/>
                            <HeaderCell index={4} value={"Dividends"}/>
                            <HeaderCell index={5} value={"Profit"}/>
                            <HeaderCell index={6} value={"Profit $"}/>
                            <HeaderCell index={7} value={"Profit %"}/>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {data.companies.map((row, index) => (
                            <TableRow key={index} hover>
                                <BodyCell index={0} value={row.ticker}/>
                                <BodyCell index={1} value={row.currency}/>
                                <BodyCell index={2} value={formatDecimals(row.purchaseSum)}/>
                                <BodyCell index={3} value={formatDecimals(row.sellSum)}/>
                                <BodyCell index={4} value={formatDecimals(row.dividendSum, 2, 2)}/>
                                <BodyCell index={5} value={formatDecimals(row.profitSum)}/>
                                <BodyCell index={6} value={formatDecimals(row.profitUsdSum)}/>
                                <BodyCell index={7} value={formatDecimals(row.profitPercentage, 2, 2)}/>
                            </TableRow>
                        ))}
                        <TableRow key={-1}>
                            <SumCell index={0} value={data.aggregates.companies}/>
                            <SumCell index={1} value={data.aggregates.currencies}/>
                            <SumCell index={2} value={formatDecimals(data.aggregates.purchaseSum)}/>
                            <SumCell index={3} value={formatDecimals(data.aggregates.sellSum)}/>
                            <SumCell index={4} value={formatDecimals(data.aggregates.dividendSum, 2, 2)}/>
                            <SumCell index={5} value={formatDecimals(data.aggregates.profitSum)}/>
                            <SumCell index={6} value={formatDecimals(data.aggregates.profitSumUsd)}/>
                            <SumCell index={7} value={formatDecimals(data.aggregates.profitPercentage, 2, 2)}/>
                        </TableRow>
                    </TableBody>
                </Table>
            </TableContainer>
        }
        </>
    )
}
