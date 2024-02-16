import React, {useEffect, useState} from "react";
import {useData} from "../fetch";
import Loader from "../components/Loader";
import {Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow} from "@mui/material";
import AddDividendDialog from "../dialog/AddDividendDialog";


const headerStyle = {textAlign: "center", border: "1px solid lightgrey"}

function rowStyle(index){
    const fontWeight = ([5].includes(index)) ? "bold" : "normal"
    const textAlign = ([0, 1, 2].includes(index)) ? "center" : "right"
    const border = "1px solid lightgrey"
    const fontFamily = "Roboto"
    const color = "primary"
    return {fontWeight: fontWeight, textAlign: textAlign, borderLeft: border, borderRight: border, fontFamily: fontFamily, color: color}
}

const Dividends = props => {
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
        if (data) {
            const years = new Set([])
            data.dividends.forEach((dividend) => {
                years.add(dividend.date.split(".")[2])
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

    return (
        <>
            {!loaded &&
                <Loader error ={error}/>
            }
            {loaded &&
                <>
                <AddDividendDialog triggerRefresh={triggerRefresh} {...props}/>
                <TableContainer component={Paper} sx={{ width: "max-content", margin: "10px auto 10px auto", maxHeight: "calc(100vh - 70px)"}}>
                    <Table size="small" aria-label="a dense table" stickyHeader>
                        <TableHead>
                            <TableRow>
                                {data.columns.map((column, index) => (
                                    <TableCell key={index} style={headerStyle}>
                                        {column}
                                    </TableCell>
                                ))}
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {data.dividends.map((dividend, index) => (
                                <TableRow key={dividend.id} hover>
                                    <TableCell style={rowStyle(0)} onDoubleClick={() => selectCompany(dividend.ticker)}>
                                        {dividend.ticker}
                                    </TableCell>
                                    <TableCell style={rowStyle(1)}>{dividend.currency}</TableCell>
                                    <TableCell style={rowStyle(2)}>{dividend.date}</TableCell>
                                    <TableCell style={rowStyle(3)}>{dividend.dividend}</TableCell>
                                    <TableCell style={rowStyle(4)}>{dividend.tax}</TableCell>
                                    <TableCell style={rowStyle(5)}>{dividend.total}</TableCell>
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
export default Dividends