import React, {useState} from "react";
import {Box, Paper, ToggleButton, ToggleButtonGroup, Typography} from "@mui/material";
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
import {formatDate, formatDecimals, formatPercent} from "../../service/FormattingService";
import {Loader} from "./Loader";

const MS_PER_DAY = 24 * 60 * 60 * 1000;

export function createEventNumberTicks(eventCount) {
    if (eventCount < 1) return [];
    if (eventCount === 1) return [1];

    const ticksCount = Math.min(6, eventCount);
    return [...new Set(Array.from({length: ticksCount}, (_, index) =>
        Math.round(1 + index * (eventCount - 1) / (ticksCount - 1))))];
}

function parseDateOnly(date) {
    const [year, month, day] = date.split("-").map(Number);
    return new Date(year, month - 1, day);
}

function daysBetween(from, to) {
    return Math.round((to.getTime() - from.getTime()) / MS_PER_DAY);
}

export function buildDailyPoints(points) {
    if (points.length === 0) return {dailyPoints: [], chartStart: null, maxDayOffset: 0};

    const chartStart = parseDateOnly(points[0].date);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayOffset = daysBetween(chartStart, today);

    const dailyPoints = points.map(point => ({
        ...point,
        dayOffset: daysBetween(chartStart, parseDateOnly(point.date)),
    }));

    const lastOffset = dailyPoints[dailyPoints.length - 1].dayOffset;
    if (todayOffset > lastOffset) {
        dailyPoints.push({
            ...points[points.length - 1],
            dayOffset: todayOffset,
            isToday: true,
        });
    }

    return {dailyPoints, chartStart, maxDayOffset: Math.max(todayOffset, lastOffset)};
}

export function createDayOffsetTicks(maxDayOffset) {
    if (maxDayOffset < 1) return [0];

    const ticksCount = Math.min(6, maxDayOffset + 1);
    return [...new Set(Array.from({length: ticksCount}, (_, index) =>
        Math.round(index * maxDayOffset / (ticksCount - 1))))];
}

function formatDayOffsetTick(chartStart) {
    return dayOffset => {
        if (!chartStart) return "";
        const date = new Date(chartStart.getTime() + dayOffset * MS_PER_DAY);
        return date.toLocaleDateString("en-US", {month: "short", year: "2-digit"});
    };
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

const ASSUMED_STARTING_CAPITAL = 10000;

export function computeAnnualizedReturn(points, startingCapital = ASSUMED_STARTING_CAPITAL) {
    if (points.length === 0) return null;

    const firstDate = parseDateOnly(points[0].date);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const years = daysBetween(firstDate, today) / 365.25;
    if (years <= 0) return null;

    const endingValue = startingCapital + Number(points[points.length - 1].cumulativeProfit);
    if (endingValue <= 0) return null;

    return (Math.pow(endingValue / startingCapital, 1 / years) - 1) * 100;
}

export function computeReturnPer100Events(points, startingCapital = ASSUMED_STARTING_CAPITAL) {
    if (points.length === 0) return null;

    const endingValue = startingCapital + Number(points[points.length - 1].cumulativeProfit);
    if (endingValue <= 0) return null;

    return (Math.pow(endingValue / startingCapital, 100 / points.length) - 1) * 100;
}

export const ProfitLossTooltip = ({active, payload, currency}) => {
    const point = payload?.[0]?.payload;
    if (!active || !point) return null;
    const isDividend = point.type === "DIVIDEND";

    if (point.isToday) {
        return (
            <Paper elevation={5} sx={{padding: "10px 12px", border: "1px solid rgba(25, 61, 70, 0.2)"}}>
                <Typography sx={{fontSize: 13, fontWeight: 700}}>Today</Typography>
                <Typography sx={{fontSize: 12, fontWeight: 700, marginTop: "5px"}}>
                    Cumulative: {formatProfit(point.cumulativeProfit, currency)}
                </Typography>
            </Paper>
        );
    }

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
    const [viewMode, setViewMode] = useState("event");
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
    const isDayMode = viewMode === "day";
    const {dailyPoints, chartStart, maxDayOffset} = buildDailyPoints(data.points);
    const chartData = isDayMode ? dailyPoints : data.points;
    const ticks = isDayMode ? createDayOffsetTicks(maxDayOffset) : createEventNumberTicks(data.points.length);
    const zeroOffset = gradientOffset(chartData);
    const cumulativeColor = Number(lastPoint.cumulativeProfit) >= 0 ? "#237a57" : "#b8453c";
    const tradesLabel = `${data.tradesCount} closed ${data.tradesCount === 1 ? "trade" : "trades"}`;
    const dividendsLabel = `${data.dividendsCount} ${data.dividendsCount === 1 ? "dividend" : "dividends"}`;
    const annualizedReturn = computeAnnualizedReturn(data.points);
    const returnPer100Events = computeReturnPer100Events(data.points);

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
                <Box sx={{display: "flex", alignItems: "baseline", gap: 1.5}}>
                    <Typography sx={{fontFamily: "Georgia, serif", fontSize: {xs: 17, sm: 21}, fontWeight: 700}}>
                        Cumulative P/L
                    </Typography>
                    <ToggleButtonGroup
                        size="small"
                        value={viewMode}
                        exclusive
                        onChange={(event, value) => value && setViewMode(value)}
                        sx={{"& .MuiToggleButton-root": {fontSize: 11, padding: "2px 8px"}}}
                    >
                        <ToggleButton value="event">Event</ToggleButton>
                        <ToggleButton value="day">Day</ToggleButton>
                    </ToggleButtonGroup>
                </Box>
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
                    <Typography sx={{fontSize: 11, color: "text.secondary"}}>
                        {isDayMode
                            ? <>Per annum: {annualizedReturn === null ? "-" : formatPercent(annualizedReturn, true)}</>
                            : <>Per 100 events: {returnPer100Events === null ? "-" : formatPercent(returnPer100Events, true)}</>
                        }
                        {" "}(assuming {formatDecimals(ASSUMED_STARTING_CAPITAL, 0, 0)}{data.currency} start)
                    </Typography>
                </Box>
            </Box>
            <Box sx={{height: "calc(100% - 52px)", minHeight: 300}}>
                <ResponsiveContainer width="100%" height="100%">
                    <AreaChart
                        data={chartData}
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
                            dataKey={isDayMode ? "dayOffset" : "eventNumber"}
                            domain={isDayMode ? [0, Math.max(1, maxDayOffset)] : [1, Math.max(2, data.points.length)]}
                            ticks={ticks}
                            tickFormatter={isDayMode ? formatDayOffsetTick(chartStart) : undefined}
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
                            type={isDayMode ? "stepAfter" : "linear"}
                            dataKey="cumulativeProfit"
                            name="Cumulative P/L"
                            stroke="#1e5663"
                            strokeWidth={2.5}
                            fill="url(#profitLossFill)"
                            dot={chartData.length <= 30 ? {r: 2.5, fill: "#1e5663", strokeWidth: 0} : false}
                            activeDot={{r: 5, fill: "#f4b942", stroke: "#1e5663", strokeWidth: 2}}
                        />
                    </AreaChart>
                </ResponsiveContainer>
            </Box>
        </Paper>
    );
};
