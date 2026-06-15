import React, {useEffect, useState} from "react";
import {useData} from "../service/BackendService";
import {Loader} from "./component/Loader";
import {Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow} from "@mui/material";
import {AddDividendDialog} from "../dialog/AddDividendDialog";


export const Dividends = props => {
    const [refresh, setRefresh] = useState("")
    const {data, loaded, error} = useData("/dividend" + constructQueryParams())

    function constructQueryParams(){
        return "?filter" + (props.companySelectorValue ? "&companyId="+props.companySelectorValue.id : "")
            + (props.currencySelectorValue ? "&currency="+props.currencySelectorValue : "")
            + (props.yearSelectorValue ? "&year="+props.yearSelectorValue : "")
            + (props.sectorSelectorValue ? "&sector=" + props.sectorSelectorValue.key : "")
            + (refresh ? "&refresh" + refresh : "")
    }

    useEffect(() => {
        if (data && !props.showYearSelector) {
            const years = new Set([])
            data.dividends.forEach((dividend) => {
                years.add(dividend.date.substring(0, 4))
            })
            props.toggleDividendsSelectors([...years].sort().reverse())
        }
        // eslint-disable-next-line
    }, [data])

    function selectCompany(ticker) {
        props.companies.forEach((company) => {if (company.ticker === ticker) {props.setCompanySelectorValue(company)}})
    }

    function triggerRefresh() {
        setRefresh(new Date().getTime().toString())
    }

    const headerStyle = {textAlign: "center", border: "1px solid lightgrey"}

    function rowStyle(index){
        const fontWeight = ([5].includes(index)) ? "bold" : "normal"
        const textAlign = ([0, 1, 2].includes(index)) ? "center" : "right"
        const border = "1px solid lightgrey"
        const fontFamily = "Roboto"
        const color = "primary"
        return {fontWeight: fontWeight, textAlign: textAlign, borderLeft: border, borderRight: border, fontFamily: fontFamily, color: color}
    }

    function sumRowStyle(index){
        return {...rowStyle(index), borderTop: "1px solid grey", borderBottom: "1px solid grey"}
    }

    return (
        <>
        {!loaded && <Loader error ={error}/>}
        {loaded &&
            <TableContainer component={Paper} sx={{ width: "max-content", margin: "10px auto 10px auto", maxHeight: "calc(100vh - 70px)"}}>
                <AddDividendDialog triggerRefresh={triggerRefresh} {...props}/>
                <Table size="small" aria-label="a dense table" stickyHeader>
                    <TableHead>
                        <TableRow>
                            <TableCell key={0} style={headerStyle}>Ticker</TableCell>
                            <TableCell key={1} style={headerStyle}>#</TableCell>
                            <TableCell key={2} style={headerStyle}>Date</TableCell>
                            <TableCell key={3} style={headerStyle}>Dividend</TableCell>
                            <TableCell key={4} style={headerStyle}>Tax</TableCell>
                            <TableCell key={5} style={headerStyle}>Total</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {data.dividends.map((dividend, index) => (
                            <TableRow key={index} hover>
                                <TableCell style={rowStyle(0)} onDoubleClick={() => selectCompany(dividend.company.ticker)}>
                                    {dividend.company.ticker}
                                </TableCell>
                                <TableCell style={rowStyle(1)}>{dividend.company.currency}</TableCell>
                                <TableCell style={rowStyle(2)}>{dividend.date}</TableCell>
                                <TableCell style={rowStyle(3)}>{dividend.dividend}</TableCell>
                                <TableCell style={rowStyle(4)}>{dividend.tax}</TableCell>
                                <TableCell style={rowStyle(5)}>{dividend.net}</TableCell>
                            </TableRow>
                        ))}
                        <TableRow key={-1}>
                            <TableCell key={0} style={sumRowStyle(0)}>{data.aggregates.companies}</TableCell>
                            <TableCell key={1} style={sumRowStyle(1)}>{data.aggregates.currencies}</TableCell>
                            <TableCell key={2} style={sumRowStyle(2)}></TableCell>
                            <TableCell key={3} style={sumRowStyle(3)}>{data.aggregates.dividendSum}</TableCell>
                            <TableCell key={4} style={sumRowStyle(4)}>{data.aggregates.taxSum}</TableCell>
                            <TableCell key={5} style={sumRowStyle(5)}>{data.aggregates.netSum}</TableCell>
                        </TableRow>
                    </TableBody>
                </Table>
            </TableContainer>
        }
        </>
    )
}
