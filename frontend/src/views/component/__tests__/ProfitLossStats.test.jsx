import React from "react";
import {fireEvent, render, screen} from "@testing-library/react";

const mockUseData = jest.fn();

jest.mock("../../../service/BackendService", () => ({
    useData: (...args) => mockUseData(...args),
}));

jest.mock("../Loader", () => ({
    Loader: ({error}) => <div data-testid="loader">{error?.message ?? "loading"}</div>,
}));

jest.mock("recharts", () => ({
    ResponsiveContainer: ({children}) => <div>{children}</div>,
    AreaChart: ({children, data}) => (
        <svg data-testid="profit-loss-chart" data-points={JSON.stringify(data)}>{children}</svg>
    ),
    Area: () => null,
    CartesianGrid: () => null,
    ReferenceLine: ({y}) => <g data-testid="reference-line"><text>{y}</text></g>,
    Tooltip: () => null,
    XAxis: ({ticks, dataKey, label}) => (
        <g data-testid="x-axis-ticks" data-key={dataKey} data-label={label?.value ?? ""}>
            <text>{ticks.join(",")}</text>
        </g>
    ),
    YAxis: () => null,
}));

import {
    buildDailyPoints,
    computeAnnualizedReturn,
    computeReturnPer100Events,
    createDayOffsetTicks,
    createEventNumberTicks,
    ProfitLossStats,
    ProfitLossTooltip,
} from "../ProfitLossStats";

const points = Array.from({length: 10}, (_, index) => ({
    eventNumber: index + 1,
    type: index === 4 ? "DIVIDEND" : "TRADE",
    sourceId: index + 100,
    date: `2026-01-${String(index + 1).padStart(2, "0")}`,
    ticker: "NVDA",
    amount: index === 9 ? 25 : 10,
    cumulativeProfit: index === 9 ? 115 : (index + 1) * 10,
}));

beforeEach(() => mockUseData.mockReset());

test("shows the loader while profit and loss data is loading", () => {
    mockUseData.mockReturnValue({data: null, loaded: false, error: {message: "failed"}});

    render(<ProfitLossStats/>);

    expect(screen.getByTestId("loader")).toHaveTextContent("failed");
});

test("renders cumulative profit and loss with evenly distributed event ticks", () => {
    mockUseData.mockReturnValue({
        data: {
            currency: "€",
            tradesCount: 9,
            dividendsCount: 1,
            dividendsExcluded: false,
            points,
        },
        loaded: true,
        error: null,
    });

    render(<ProfitLossStats
        companySelectorValue={{id: 15, ticker: "ASML", currency: "€"}}
        currencySelectorValue=""
        sectorSelectorValue={{key: "SEMICONDUCTORS"}}
    />);

    expect(mockUseData).toHaveBeenCalledWith(
        "/stats/profit-loss?companyId=15&currency=%E2%82%AC&sector=SEMICONDUCTORS");
    expect(screen.getByText("9 closed trades + 1 dividend")).toBeInTheDocument();
    expect(screen.getByText("+115.00€")).toBeInTheDocument();
    expect(screen.getByTestId("x-axis-ticks")).toHaveTextContent("1,3,5,6,8,10");
    expect(screen.getByTestId("x-axis-ticks")).toHaveAttribute("data-key", "eventNumber");
    expect(screen.getByTestId("x-axis-ticks")).toHaveAttribute("data-label", "");
    expect(screen.getByTestId("reference-line")).toHaveTextContent("0");
    expect(JSON.parse(screen.getByTestId("profit-loss-chart").dataset.points)).toEqual(points);
});

test("uses the selected currency and renders an empty state", () => {
    mockUseData.mockReturnValue({
        data: {
            currency: "£",
            tradesCount: 0,
            dividendsCount: 0,
            dividendsExcluded: false,
            points: [],
        },
        loaded: true,
        error: null,
    });

    render(<ProfitLossStats currencySelectorValue="£"/>);

    expect(mockUseData).toHaveBeenCalledWith("/stats/profit-loss?currency=%C2%A3");
    expect(screen.getByText("No P/L events")).toBeInTheDocument();
    expect(screen.queryByTestId("profit-loss-chart")).not.toBeInTheDocument();
});

test("explains that portfolio-filtered profit and loss excludes dividends", () => {
    mockUseData.mockReturnValue({
        data: {
            currency: "$",
            tradesCount: 1,
            dividendsCount: 0,
            dividendsExcluded: true,
            points: [points[0]],
        },
        loaded: true,
        error: null,
    });

    render(<ProfitLossStats portfolioSelectorValue={{key: "PATRIA_MARGIN"}}/>);

    expect(mockUseData).toHaveBeenCalledWith("/stats/profit-loss?portfolio=PATRIA_MARGIN");
    expect(screen.getByText("1 closed trade")).toBeInTheDocument();
    expect(screen.getByText("Dividends excluded by portfolio filter")).toBeInTheDocument();
});

test("identifies dividend events in the chart tooltip", () => {
    render(<ProfitLossTooltip
        active
        currency="€"
        payload={[{payload: points[4]}]}
    />);

    expect(screen.getByText("Dividend - NVDA")).toBeInTheDocument();
    expect(screen.getByText("Net dividend: +10.00€")).toBeInTheDocument();
    expect(screen.getByText(/Event #5/)).toBeInTheDocument();
});

test("creates compact ticks for small and large datasets", () => {
    expect(createEventNumberTicks(0)).toEqual([]);
    expect(createEventNumberTicks(1)).toEqual([1]);
    expect(createEventNumberTicks(4)).toEqual([1, 2, 3, 4]);
    expect(createEventNumberTicks(10)).toEqual([1, 3, 5, 6, 8, 10]);
});

test("creates compact day-offset ticks", () => {
    expect(createDayOffsetTicks(0)).toEqual([0]);
    expect(createDayOffsetTicks(3)).toEqual([0, 1, 2, 3]);
    expect(createDayOffsetTicks(100)).toEqual([0, 20, 40, 60, 80, 100]);
});

test("builds day-offset points starting from the first transaction's own date and extends a flat point to today", () => {
    const twoPoints = [
        {date: "2020-03-15", cumulativeProfit: 50, eventNumber: 1},
        {date: "2020-03-20", cumulativeProfit: 80, eventNumber: 2},
    ];

    const {dailyPoints, chartStart, maxDayOffset} = buildDailyPoints(twoPoints);

    expect(chartStart).toEqual(new Date(2020, 2, 15));
    expect(dailyPoints[0]).toEqual({...twoPoints[0], dayOffset: 0});
    expect(dailyPoints[1]).toEqual({...twoPoints[1], dayOffset: 5});

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const expectedTodayOffset = Math.round((today.getTime() - new Date(2020, 2, 15).getTime()) / 86400000);
    expect(dailyPoints[2]).toEqual({...twoPoints[1], dayOffset: expectedTodayOffset, isToday: true});
    expect(maxDayOffset).toEqual(expectedTodayOffset);
});

test("returns an empty day-offset result when there are no points", () => {
    expect(buildDailyPoints([])).toEqual({dailyPoints: [], chartStart: null, maxDayOffset: 0});
});

test("shows a simplified tooltip for the synthetic today point", () => {
    render(<ProfitLossTooltip
        active
        currency="€"
        payload={[{payload: {isToday: true, cumulativeProfit: 42}}]}
    />);

    expect(screen.getByText("Today")).toBeInTheDocument();
    expect(screen.getByText("Cumulative: +42.00€")).toBeInTheDocument();
});

test("computes the annualized return assuming a 10000 starting capital over the actual elapsed time", () => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const firstDate = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 200);
    const isoDate = `${firstDate.getFullYear()}-${String(firstDate.getMonth() + 1).padStart(2, "0")}-${String(firstDate.getDate()).padStart(2, "0")}`;
    const twoPoints = [
        {date: isoDate, cumulativeProfit: 500},
        {date: isoDate, cumulativeProfit: 2000},
    ];

    const years = 200 / 365.25;
    const expected = (Math.pow(12000 / 10000, 1 / years) - 1) * 100;

    expect(computeAnnualizedReturn(twoPoints, 10000)).toBeCloseTo(expected, 6);
});

test("returns null for the annualized return when the first event is today or the loss exceeds the starting capital", () => {
    const now = new Date();
    const todayIso = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;

    expect(computeAnnualizedReturn([{date: todayIso, cumulativeProfit: 100}], 10000)).toBeNull();
    expect(computeAnnualizedReturn([{date: "2020-01-01", cumulativeProfit: -20000}], 10000)).toBeNull();
    expect(computeAnnualizedReturn([], 10000)).toBeNull();
});

test("computes the compounded return per 100 events assuming a 10000 starting capital", () => {
    const expected = (Math.pow(10115 / 10000, 100 / 10) - 1) * 100;

    expect(computeReturnPer100Events(points, 10000)).toBeCloseTo(expected, 10);
    expect(computeReturnPer100Events([], 10000)).toBeNull();
    expect(computeReturnPer100Events([{date: "2020-01-01", cumulativeProfit: -20000}], 10000)).toBeNull();
});

test("switches the summary line between per-100-events and per-annum when the view mode toggles", () => {
    mockUseData.mockReturnValue({
        data: {currency: "€", tradesCount: 9, dividendsCount: 1, dividendsExcluded: false, points},
        loaded: true,
        error: null,
    });

    render(<ProfitLossStats/>);

    expect(screen.getByText(/Per 100 events:/)).toBeInTheDocument();
    expect(screen.queryByText(/Per annum:/)).not.toBeInTheDocument();

    fireEvent.click(screen.getByText("Day"));

    expect(screen.getByText(/Per annum:/)).toBeInTheDocument();
    expect(screen.queryByText(/Per 100 events:/)).not.toBeInTheDocument();
});
