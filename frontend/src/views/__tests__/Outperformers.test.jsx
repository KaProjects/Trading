import {fireEvent, render, screen} from "@testing-library/react";

const mockUseData = jest.fn();

jest.mock("../../service/BackendService", () => ({
    useData: (...args) => mockUseData(...args),
}));

jest.mock("../component/Loader", () => ({
    Loader: ({error}) => <div data-testid="loader">{error?.message ?? "loading"}</div>,
}));

import {Outperformers} from "../Outperformers";

const data = {
    epsEstimates: [
        {ticker: "NVDA", ttmEps: 4.5, quarter1Change: 6.25, quarter2Change: 18.75, quarter3Change: 37.5, quarter4Change: 62.5},
        {ticker: "AMD", ttmEps: 2, quarter1Change: 10, quarter2Change: 20, quarter3Change: 30, quarter4Change: 80},
    ],
    epsEstimatesDisqualified: [
        {ticker: "INTC", reason: "estimate data is stale (last updated 2026-01-01)"},
    ],
    margins: [
        {ticker: "NVDA", revenue: 2500, grossMargin: 60, operatingMargin: 30, netMargin: 20},
    ],
    marginsDisqualified: [],
    sentiment: [
        {ticker: "NVDA", articleCount: 12, positiveCount: 8, neutralCount: 3, negativeCount: 1, weightedSentimentSum: 19, sentimentScore: 5.48},
    ],
    sentimentDisqualified: [
        {ticker: "QCOM", reason: "only 3 articles in the last 31 days (need 5)"},
    ],
    targets: [
        {ticker: "NVDA", price: 200, averageTarget: 211, percentDiff: 5.5},
    ],
    targetsDisqualified: [
        {ticker: "AMD", reason: "cached price is stale (last updated 2026-01-01)"},
    ],
};

beforeEach(() => mockUseData.mockReset());

test("shows the loader while data is loading", () => {
    mockUseData.mockReturnValue({data: null, loaded: false, error: null});

    render(<Outperformers/>);

    expect(screen.getByTestId("loader")).toBeInTheDocument();
});

test("shows an error through the loader", () => {
    mockUseData.mockReturnValue({data: null, loaded: false, error: {message: "failed"}});

    render(<Outperformers/>);

    expect(screen.getByTestId("loader")).toHaveTextContent("failed");
});

test("renders the EPS tab by default, sorted by the +4Q change, with all four change columns", () => {
    mockUseData.mockReturnValue({data, loaded: true, error: null});

    render(<Outperformers outperformersTabsIndex={0}/>);

    const rows = screen.getAllByRole("row").slice(1, 3);
    expect(rows[0]).toHaveTextContent("AMD");
    expect(rows[1]).toHaveTextContent("NVDA");
    expect(screen.getByText("+80%")).toBeInTheDocument();
    expect(screen.getByText("+6.25%")).toBeInTheDocument();
    expect(screen.getByText("+37.5%")).toBeInTheDocument();
    expect(screen.getByText("4.5")).toBeInTheDocument();
    expect(screen.getByText("INTC")).toBeInTheDocument();
    expect(screen.getByText("estimate data is stale (last updated 2026-01-01)")).toBeInTheDocument();
});

test("shows the hard and soft rating criteria, and the disqualified table's updated title", () => {
    mockUseData.mockReturnValue({data, loaded: true, error: null});

    render(<Outperformers outperformersTabsIndex={0}/>);

    expect(screen.getByText("Rating criteria")).toBeInTheDocument();
    expect(screen.getByText("To be considered at all:")).toBeInTheDocument();
    expect(screen.getByText(
        "The company has at least one period, and an estimate snapshot has been recorded for its latest period."
    )).toBeInTheDocument();
    expect(screen.getByText("To qualify for the ranking above:")).toBeInTheDocument();
    expect(screen.getByText("The estimate snapshot is no more than 3 months old.")).toBeInTheDocument();
    expect(screen.getByText("Met the basic criteria, but not currently qualified")).toBeInTheDocument();
});

test("renders the margins tab with revenue and its qualified companies", () => {
    mockUseData.mockReturnValue({data, loaded: true, error: null});

    render(<Outperformers outperformersTabsIndex={1}/>);

    expect(screen.getByText("2.5B")).toBeInTheDocument();
    expect(screen.getByText("60%")).toBeInTheDocument();
    expect(screen.getByText("30%")).toBeInTheDocument();
    expect(screen.getByText("20%")).toBeInTheDocument();
    expect(screen.queryByText("Met the basic criteria, but not currently qualified")).not.toBeInTheDocument();
});

test("renders the sentiment tab with a positive/neutral/negative breakdown", () => {
    mockUseData.mockReturnValue({data, loaded: true, error: null});

    render(<Outperformers outperformersTabsIndex={2}/>);

    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText("8")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByText("19")).toBeInTheDocument();
    expect(screen.getByText("5.48")).toBeInTheDocument();
    expect(screen.getByText("QCOM")).toBeInTheDocument();
    expect(screen.getByText("only 3 articles in the last 31 days (need 5)")).toBeInTheDocument();
});

test("renders the targets tab with price, average target and the percentage diff", () => {
    mockUseData.mockReturnValue({data, loaded: true, error: null});

    render(<Outperformers outperformersTabsIndex={3}/>);

    expect(screen.getByText("200")).toBeInTheDocument();
    expect(screen.getByText("211")).toBeInTheDocument();
    expect(screen.getByText("+5.5%")).toBeInTheDocument();
});

test("reverses sort order when a column header is clicked twice", () => {
    mockUseData.mockReturnValue({data, loaded: true, error: null});

    render(<Outperformers outperformersTabsIndex={0}/>);

    let rows = screen.getAllByRole("row").slice(1, 3);
    expect(rows[0]).toHaveTextContent("AMD");

    fireEvent.click(screen.getByText("+4Q change"));
    rows = screen.getAllByRole("row").slice(1, 3);
    expect(rows[0]).toHaveTextContent("NVDA");

    fireEvent.click(screen.getByText("+4Q change"));
    rows = screen.getAllByRole("row").slice(1, 3);
    expect(rows[0]).toHaveTextContent("AMD");
});
