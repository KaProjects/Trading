import React, {useEffect, useState} from "react";
import {useData} from "../fetch";
import Loader from "../components/Loader";
import {Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow} from '@mui/material';
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';

const types = ["company", "monthly", "yearly"]

const headerStyle = {textAlign: "center", border: "1px solid lightgrey"}

const Stats = props => {

    useEffect(() => {
        props.toggleStatsSelectors(null, false, false)
        // eslint-disable-next-line
    }, [])

    function CompanyStats(props) {
        const {type} = props
        const [sort, setSort] = useState(6)
        const {data, loaded, error} = useData("/stats/" + type + constructQueryParams())

        function constructQueryParams(){
            return "?query "
                + (props.yearSelectorValue ? "&year=" + props.yearSelectorValue : "")
                + (props.sectorSelectorValue ? "&sector=" + props.sectorSelectorValue.key : "")
                + (sort === 7 ? "&sort=percentage" : "")
        }

        useEffect(() => {
            if (data && !props.showYearSelector){
                props.toggleStatsSelectors([...data.years].sort().reverse(), false, true)
            }
            // eslint-disable-next-line
        }, [data])

        function rowStyle(index){
            const fontWeight = ([6, 7].includes(index)) ? "bold" : "normal"
            const textAlign = ([0, 1].includes(index)) ? "center" : "right"
            const border = "1px solid lightgrey"
            const fontFamily = "Roboto"
            const color = "primary"
            return {fontWeight: fontWeight, textAlign: textAlign, borderLeft: border, borderRight: border, fontFamily: fontFamily, color: color}
        }

        return (
            <>
            {!loaded && <Loader error ={error}/>}
            {loaded &&
                <TableContainer component={Paper} sx={{ width: "max-content", margin: "10px auto 10px auto", maxHeight: "calc(100vh - 70px)"}}>
                <Table size="small" aria-label="a dense table" stickyHeader>
                    <TableHead>
                        <TableRow>
                            {data.columns.map((column, index) => (
                                <TableCell key={index} style={headerStyle} onClick={() => {if ([6,7].includes(index)) setSort(index)}}>
                                    {column}
                                    {sort === index && <ArrowDropDownIcon sx={{ height: "18px", marginRight: "-15px", marginBottom: "-5px"}}/>}
                                </TableCell>
                            ))}
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {data.rows.map((row, index) => (
                            <TableRow key={index} hover>
                                <TableCell style={rowStyle(0)}>{row.ticker}</TableCell>
                                <TableCell style={rowStyle(1)}>{row.currency}</TableCell>
                                <TableCell style={rowStyle(2)}>{row.purchaseSum}</TableCell>
                                <TableCell style={rowStyle(3)}>{row.sellSum}</TableCell>
                                <TableCell style={rowStyle(4)}>{row.dividendSum}</TableCell>
                                <TableCell style={rowStyle(5)}>{row.profit}</TableCell>
                                <TableCell style={rowStyle(6)}>{row.profitUsd}</TableCell>
                                <TableCell style={rowStyle(7)}>{row.profitPercentage}</TableCell>
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

    function PeriodStats(props) {
        const {type} = props
        const {data, loaded, error} = useData("/stats/" + type + constructQueryParams())

        function constructQueryParams(){
            return "?filter" + (props.companySelectorValue ? "&companyId=" + props.companySelectorValue.id : "")
                + (props.sectorSelectorValue ? "&sector=" + props.sectorSelectorValue.key : "")
        }

        useEffect(() => {
            if (data && !props.showCompanySelector){
                props.toggleStatsSelectors(null, true, true)
            }
            // eslint-disable-next-line
        }, [data])

        function rowStyle(index, rIndex){
            const fontWeight = ([].includes(index)) ? "bold" : "normal"
            const textAlign = ([0, 1].includes(index)) ? "center" : "right"
            const border = "1px solid lightgrey"
            const fontFamily = "Roboto"
            const color = "primary"
            let style = {fontWeight: fontWeight, textAlign: textAlign, borderLeft: border, borderRight: border ,fontFamily: fontFamily, color: color}

            if (type === types[1]){
                const isCurrentYear = (rIndex !== undefined && Number(data.rows[0].period.split(".")[1]) === new Date().getFullYear())
                if ((rIndex + (isCurrentYear ? (12 - new Date().getMonth()) : 1)) % 12 === 0){
                    style = Object.assign(style, {borderBottom: "1px solid black"})
                }
            }
            return style
        }

        return (
            <>
                {!loaded && <Loader error ={error}/>}
                {loaded &&
                    <TableContainer component={Paper} sx={{ width: "max-content", margin: "10px auto 10px auto", maxHeight: "calc(100vh - 70px)"}}>
                        <Table size="small" aria-label="a dense table" stickyHeader>
                            <TableHead>
                                <TableRow>
                                    {data.columns.map((column, index) => (
                                        <TableCell key={index} style={headerStyle}
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
                                                <TableCell key={index} style={headerStyle}>{subColumn}</TableCell>
                                            ))}
                                        </React.Fragment>
                                    ))}
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {data.rows.map((row, index) => (
                                    <TableRow key={index} hover>
                                        <TableCell style={rowStyle(0, index)}>{row.period}</TableCell>
                                        <TableCell style={rowStyle(1, index)}>{row.tradesCount}</TableCell>
                                        <TableCell style={rowStyle(2, index)}>{row.tradesProfit}</TableCell>
                                        <TableCell style={rowStyle(3, index)}>{row.tradesProfitPercentage}</TableCell>
                                        <TableCell style={rowStyle(4, index)}>{row.dividendSum}</TableCell>
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

    return (
        <>
            {props.statsTabsIndex === 0 && (
                <CompanyStats type={types[0]} {...props} />
            )}
            {props.statsTabsIndex === 1 && (
                <PeriodStats type={types[1]} {...props} />
            )}
            {props.statsTabsIndex === 2 && (
                <PeriodStats type={types[2]} {...props} />
            )}
        </>
    )
}
export default Stats