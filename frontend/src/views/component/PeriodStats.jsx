import React from "react";
import {Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow} from "@mui/material";
import {useData} from "../../service/BackendService";
import {Loader} from "./Loader";
import {formatDecimals} from "../../service/FormattingService";

const STATS_TYPES = {monthly: "monthly", quarterly: "quarterly", yearly: "yearly"}
const headerStyle = {textAlign: "center", border: "1px solid lightgrey"}

export const PeriodStats = props => {
    const {type} = props
    const {data, loaded, error} = useData("/stats/" + type + constructQueryParams())

    function constructQueryParams(){
        return "?filter" + (props.companySelectorValue ? "&companyId=" + props.companySelectorValue.id : "")
            + (props.sectorSelectorValue ? "&sector=" + props.sectorSelectorValue.key : "")
    }

    function rowStyle(index, rIndex){
        const fontWeight = ([].includes(index)) ? "bold" : "normal"
        const textAlign = ([0, 1].includes(index)) ? "center" : "right"
        const border = "1px solid lightgrey"
        const fontFamily = "Roboto"
        const color = "primary"
        let style = {fontWeight: fontWeight, textAlign: textAlign, borderLeft: border, borderRight: border ,fontFamily: fontFamily, color: color}

        const isCurrentYear = (rIndex !== undefined && Number(data.periods[0].period.substring(0,4)) === new Date().getFullYear())
        if (type === STATS_TYPES.monthly){
            if ((rIndex + (isCurrentYear ? (12 - new Date().getMonth()) : 1)) % 12 === 0){
                style = Object.assign(style, {borderBottom: "1px solid black"})
            }
        }
        if (type === STATS_TYPES.quarterly){
            if ((rIndex + (isCurrentYear ? (4 - new Date().getMonth()) : 1)) % 4 === 0){
                style = Object.assign(style, {borderBottom: "1px solid black"})
            }
        }
        return style
    }

    function BodyCell({index, row, value}) {
        const style = rowStyle(index, row)
        return <TableCell key={index} style={style}>{value}</TableCell>
    }

    function SumCell({index, value}) {
        const style = {...rowStyle(index), borderTop: "1px solid grey", borderBottom: "1px solid grey"}
        return <TableCell key={index} style={style}>{value}</TableCell>
    }

    function getTitle() {
        switch (type) {
            case STATS_TYPES.monthly: return "Month"
            case STATS_TYPES.yearly: return "Year"
            case STATS_TYPES.quarterly: return "Quarter"
            default: return ""
        }
    }

    function formatPeriod(period) {
        switch (type) {
            case STATS_TYPES.monthly: return period.substring(0,4) + "/" + period.substring(5,7)
            case STATS_TYPES.yearly: return period
            case STATS_TYPES.quarterly: return period.substring(0,4) + "/" + period.substring(5,7)
            default: return period
        }
    }

    return (
        <>
        {!loaded && <Loader error ={error}/>}
        {loaded &&
            <TableContainer component={Paper} sx={{width: {xs: "100%", sm: "max-content"}, margin: "10px auto 10px auto", maxHeight: "calc(100vh - var(--main-bar-height, 48px) - 32px)", overflow: "auto"}}>
                <Table size="small" aria-label="a dense table" stickyHeader sx={{minWidth: {xs: 560, sm: "unset"}}}>
                    <TableHead>
                        <TableRow>
                            <TableCell key={0} style={headerStyle} colSpan={1} rowSpan={2}>{getTitle()}</TableCell>
                            <TableCell key={1} style={headerStyle} colSpan={3} rowSpan={1}>Trades</TableCell>
                            <TableCell key={2} style={headerStyle} colSpan={1} rowSpan={2}>Dividends $</TableCell>
                        </TableRow>
                        <TableRow>
                            <TableCell key={1.0} style={headerStyle}>Count</TableCell>
                            <TableCell key={1.1} style={headerStyle}>Profit $</TableCell>
                            <TableCell key={1.2} style={headerStyle}>Profit %</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {data.periods.map((row, index) => (
                            <TableRow key={index} hover>
                                <BodyCell index={0} row={index} value={formatPeriod(row.period)}/>
                                <BodyCell index={1} row={index} value={row.tradesCount}/>
                                <BodyCell index={2} row={index} value={formatDecimals(row.tradesProfitSum)}/>
                                <BodyCell index={3} row={index} value={formatDecimals(row.tradesProfitPercentage, 2, 2)}/>
                                <BodyCell index={4} row={index} value={formatDecimals(row.dividendSum, 2, 2)}/>
                            </TableRow>
                        ))}
                        <TableRow key={-1} >
                            <SumCell index={0} value={data.aggregates.periods}/>
                            <SumCell index={1} value={data.aggregates.tradesCount}/>
                            <SumCell index={2} value={formatDecimals(data.aggregates.tradesProfitSum)}/>
                            <SumCell index={3} value={formatDecimals(data.aggregates.tradesProfitPercentage, 2, 2)}/>
                            <SumCell index={4} value={formatDecimals(data.aggregates.dividendSum, 2, 2)}/>
                        </TableRow>
                    </TableBody>
                </Table>
            </TableContainer>
        }
        </>
    )
}
