import React from "react";
import {render, screen} from "@testing-library/react";

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

import {createEventNumberTicks, ProfitLossStats, ProfitLossTooltip} from "../ProfitLossStats";

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
