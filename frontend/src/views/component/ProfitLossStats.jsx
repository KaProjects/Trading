import React from "react";
import {Box, Paper, Typography} from "@mui/material";
import {
    Area,
    AreaChart,
    CartesianGrid,
    ReferenceLine,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from "recharts";
import {useData} from "../../service/BackendService";
import {formatDate, formatDecimals} from "../../service/FormattingService";
import {Loader} from "./Loader";

export function createEventNumberTicks(eventCount) {
    if (eventCount < 1) return [];
    if (eventCount === 1) return [1];

    const ticksCount = Math.min(6, eventCount);
    return [...new Set(Array.from({length: ticksCount}, (_, index) =>
        Math.round(1 + index * (eventCount - 1) / (ticksCount - 1))))];
}

function formatProfit(value, currency) {
    const number = Number(value);
    if (!Number.isFinite(number)) return "-";
    const sign = number > 0 ? "+" : "";
    return `${sign}${formatDecimals(number, 2, 2)}${currency ?? ""}`;
}

function axisValue(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) return "";
    return new Intl.NumberFormat("en-US", {
        notation: Math.abs(number) >= 1000 ? "compact" : "standard",
        maximumFractionDigits: 1,
    }).format(number);
}

function chartDomain([dataMin, dataMax]) {
    const minimum = Math.min(Number(dataMin), 0);
    const maximum = Math.max(Number(dataMax), 0);
    if (minimum === maximum) return [minimum - 1, maximum + 1];

    const padding = (maximum - minimum) * 0.08;
    return [minimum - padding, maximum + padding];
}

function gradientOffset(points) {
    const values = points.map(point => Number(point.cumulativeProfit));
    const maximum = Math.max(...values);
    const minimum = Math.min(...values);
    if (maximum <= 0) return 0;
    if (minimum >= 0) return 1;
    return maximum / (maximum - minimum);
}

export const ProfitLossTooltip = ({active, payload, currency}) => {
    const point = payload?.[0]?.payload;
    if (!active || !point) return null;
    const isDividend = point.type === "DIVIDEND";

    return (
        <Paper elevation={5} sx={{padding: "10px 12px", border: "1px solid rgba(25, 61, 70, 0.2)"}}>
            <Typography sx={{fontSize: 13, fontWeight: 700}}>
                {isDividend ? "Dividend" : "Trade"} - {point.ticker}
            </Typography>
            <Typography sx={{fontSize: 11, color: "text.secondary", marginBottom: "5px"}}>
                Event #{point.eventNumber} | {formatDate(point.date)}
            </Typography>
            <Typography sx={{fontSize: 12}}>
                {isDividend ? "Net dividend" : "Trade P/L"}: {formatProfit(point.amount, currency)}
            </Typography>
            <Typography sx={{fontSize: 12, fontWeight: 700}}>
                Cumulative: {formatProfit(point.cumulativeProfit, currency)}
            </Typography>
        </Paper>
    );
};

export const ProfitLossStats = props => {
    const selectedCurrency = props.currencySelectorValue || props.companySelectorValue?.currency || "";
    const query = new URLSearchParams();
    if (props.companySelectorValue) query.set("companyId", props.companySelectorValue.id);
    if (selectedCurrency) query.set("currency", selectedCurrency);
    if (props.sectorSelectorValue) query.set("sector", props.sectorSelectorValue.key);
    if (props.portfolioSelectorValue) query.set("portfolio", props.portfolioSelectorValue.key);

    const queryString = query.toString();
    const {data, loaded, error} = useData(
        `/stats/profit-loss${queryString ? `?${queryString}` : ""}`);

    if (!loaded) return <Loader error={error}/>;

    if (data.points.length === 0) {
        return (
            <Paper sx={{maxWidth: 720, margin: "24px auto", padding: 4, textAlign: "center"}}>
                <Typography sx={{fontWeight: 700}}>No P/L events</Typography>
                <Typography sx={{color: "text.secondary", fontSize: 13}}>
                    {data.dividendsExcluded
                        ? "There are no closed trades for the selected filters."
                        : "There are no closed trades or dividends for the selected filters."}
                </Typography>
                {data.dividendsExcluded && (
                    <Typography sx={{color: "text.secondary", fontSize: 11, marginTop: "4px"}}>
                        Dividends are excluded while filtering by portfolio.
                    </Typography>
                )}
            </Paper>
        );
    }

    const lastPoint = data.points[data.points.length - 1];
    const ticks = createEventNumberTicks(data.points.length);
    const zeroOffset = gradientOffset(data.points);
    const cumulativeColor = Number(lastPoint.cumulativeProfit) >= 0 ? "#237a57" : "#b8453c";
    const tradesLabel = `${data.tradesCount} closed ${data.tradesCount === 1 ? "trade" : "trades"}`;
    const dividendsLabel = `${data.dividendsCount} ${data.dividendsCount === 1 ? "dividend" : "dividends"}`;

    return (
        <Paper sx={{
            height: "calc(100vh - var(--main-bar-height, 48px) - 48px)",
            minHeight: 380,
            maxWidth: 1500,
            margin: "16px auto",
            padding: {xs: "16px 6px 10px", sm: "20px 24px 16px"},
            background: "linear-gradient(145deg, #f9fbfa 0%, #edf4f1 100%)",
        }}>
            <Box sx={{display: "flex", justifyContent: "space-between", alignItems: "baseline", paddingX: 1}}>
                <Typography sx={{fontFamily: "Georgia, serif", fontSize: {xs: 17, sm: 21}, fontWeight: 700}}>
                    Cumulative P/L
                </Typography>
                <Box sx={{textAlign: "right"}}>
                    <Typography sx={{fontSize: 11, color: "text.secondary"}}>
                        {tradesLabel}{!data.dividendsExcluded && ` + ${dividendsLabel}`}
                    </Typography>
                    {data.dividendsExcluded && (
                        <Typography sx={{fontSize: 10, color: "text.secondary"}}>
                            Dividends excluded by portfolio filter
                        </Typography>
                    )}
                    <Typography sx={{fontSize: {xs: 15, sm: 18}, color: cumulativeColor, fontWeight: 800}}>
                        {formatProfit(lastPoint.cumulativeProfit, data.currency)}
                    </Typography>
                </Box>
            </Box>
            <Box sx={{height: "calc(100% - 52px)", minHeight: 300}}>
                <ResponsiveContainer width="100%" height="100%">
                    <AreaChart
                        data={data.points}
                        margin={{top: 22, right: 20, left: 4, bottom: 8}}
                        accessibilityLayer
                    >
                        <defs>
                            <linearGradient id="profitLossFill" x1="0" y1="0" x2="0" y2="1">
                                <stop offset={zeroOffset} stopColor="#5ca878" stopOpacity={0.35}/>
                                <stop offset={zeroOffset} stopColor="#d36b5f" stopOpacity={0.30}/>
                            </linearGradient>
                        </defs>
                        <CartesianGrid stroke="#b9c9c3" strokeDasharray="3 5" vertical={false}/>
                        <XAxis
                            type="number"
                            dataKey="eventNumber"
                            domain={[1, Math.max(2, data.points.length)]}
                            ticks={ticks}
                            allowDecimals={false}
                            tickLine={false}
                            axisLine={{stroke: "#71877f"}}
                            tick={{fontSize: 11, fill: "#42544e"}}
                        />
                        <YAxis
                            domain={chartDomain}
                            tickFormatter={axisValue}
                            tickLine={false}
                            axisLine={false}
                            tick={{fontSize: 11, fill: "#42544e"}}
                            width={58}
                        />
                        <ReferenceLine y={0} stroke="#263b42" strokeWidth={1.2}/>
                        <Tooltip
                            content={<ProfitLossTooltip currency={data.currency}/>}
                            cursor={{stroke: "#78968b", strokeDasharray: "4 4"}}
                        />
                        <Area
                            type="linear"
                            dataKey="cumulativeProfit"
                            name="Cumulative P/L"
                            stroke="#1e5663"
                            strokeWidth={2.5}
                            fill="url(#profitLossFill)"
                            dot={data.points.length <= 30 ? {r: 2.5, fill: "#1e5663", strokeWidth: 0} : false}
                            activeDot={{r: 5, fill: "#f4b942", stroke: "#1e5663", strokeWidth: 2}}
                        />
                    </AreaChart>
                </ResponsiveContainer>
            </Box>
        </Paper>
    );
};
