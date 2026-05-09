import React, {useEffect, useState} from "react";
import {useData} from "../service/BackendService";
import Loader from "../components/Loader";
import {Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow} from '@mui/material';
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';


export const Stats = props => {
    const types = {company: "company", monthly: "monthly", quarterly: "quarterly", yearly: "yearly"}
    const headerStyle = {textAlign: "center", border: "1px solid lightgrey"}

    useEffect(() => {
        props.toggleStatsSelectors(null, false, false)
        // eslint-disable-next-line
    }, [])

    function CompanyStats(props) {
        const {type} = props
        const [sort, setSort] = useState(null)
        const {data, loaded, error} = useData("/stats/" + type + constructQueryParams())

        function constructQueryParams(){
            return "?query"
                + (props.yearSelectorValue ? "&year=" + props.yearSelectorValue : "")
                + (props.sectorSelectorValue ? "&sector=" + props.sectorSelectorValue.key : "")
                + (sort ? "&sort=" + sort : "")
        }

        useEffect(() => {
            if (data && !props.showYearSelector){
                props.toggleStatsSelectors([...data.years].sort().reverse(), false, true)
            }
            // eslint-disable-next-line
        }, [data])

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
            return <TableCell key={index} style={headerStyle}
                              onClick={() => {
                                  if ([6,7].includes(index)) setSort(index)
                              }}
            >
                {value}
                {sort === index && <ArrowDropDownIcon sx={{ height: "18px", marginRight: "-15px", marginBottom: "-5px"}}/>}
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
                                    <BodyCell index={2} value={row.purchaseSum}/>
                                    <BodyCell index={3} value={row.sellSum}/>
                                    <BodyCell index={4} value={row.dividendSum}/>
                                    <BodyCell index={5} value={row.profitSum}/>
                                    <BodyCell index={6} value={row.profitUsdSum}/>
                                    <BodyCell index={7} value={row.profitPercentage}/>
                                </TableRow>
                            ))}
                            <TableRow key={-1}>
                                <SumCell index={0} value={data.aggregates.companies}/>
                                <SumCell index={1} value={data.aggregates.currencies}/>
                                <SumCell index={2} value={data.aggregates.purchaseSum}/>
                                <SumCell index={3} value={data.aggregates.sellSum}/>
                                <SumCell index={4} value={data.aggregates.dividendSum}/>
                                <SumCell index={5} value={data.aggregates.profitSum}/>
                                <SumCell index={6} value={data.aggregates.profitSumUsd}/>
                                <SumCell index={7} value={data.aggregates.profitPercentage}/>
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

            const isCurrentYear = (rIndex !== undefined && Number(data.periods[0].period.substring(0,4)) === new Date().getFullYear())
            if (type === types.monthly){
                if ((rIndex + (isCurrentYear ? (12 - new Date().getMonth()) : 1)) % 12 === 0){
                    style = Object.assign(style, {borderBottom: "1px solid black"})
                }
            }
            if (type === types.quarterly){
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
                case types.monthly: return "Month"
                case types.yearly: return "Year"
                case types.quarterly: return "Quarter"
                default: return ""
            }
        }

        function formatPeriod(period) {
            switch (type) {
                case types.monthly: return period.substring(0,4) + "/" + period.substring(5,7)
                case types.yearly: return period
                case types.quarterly: return period.substring(0,4) + "/" + period.substring(5,7)
                default: return period
            }
        }

        return (
            <>
            {!loaded && <Loader error ={error}/>}
            {loaded &&
                <TableContainer component={Paper} sx={{ width: "max-content", margin: "10px auto 10px auto", maxHeight: "calc(100vh - 70px)"}}>
                    <Table size="small" aria-label="a dense table" stickyHeader>
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
                                    <BodyCell index={2} row={index} value={row.tradesProfitSum}/>
                                    <BodyCell index={3} row={index} value={row.tradesProfitPercentage}/>
                                    <BodyCell index={4} row={index} value={row.dividendSum}/>
                                </TableRow>
                            ))}
                            <TableRow key={-1} >
                                <SumCell index={0} value={data.aggregates.periods}/>
                                <SumCell index={1} value={data.aggregates.tradesCount}/>
                                <SumCell index={2} value={data.aggregates.tradesProfitSum}/>
                                <SumCell index={3} value={data.aggregates.tradesProfitPercentage}/>
                                <SumCell index={4} value={data.aggregates.dividendSum}/>
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
                <CompanyStats type={types.company} {...props} />
            )}
            {props.statsTabsIndex === 1 && (
                <PeriodStats type={types.monthly} {...props} />
            )}
            {props.statsTabsIndex === 2 && (
                <PeriodStats type={types.quarterly} {...props} />
            )}
            {props.statsTabsIndex === 3 && (
                <PeriodStats type={types.yearly} {...props} />
            )}
        </>
    )
}
