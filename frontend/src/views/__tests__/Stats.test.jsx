import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";

const mockUseData = jest.fn();

jest.mock("../../service/BackendService", () => ({
    useData: (...args) => mockUseData(...args),
}));

jest.mock("../../components/Loader", () => (props) => (
    <div data-testid="loader">{props.error ? props.error.message : "loading"}</div>
));

import {Stats} from "../Stats";

function createProps(overrides = {}) {
    return {
        statsTabsIndex: 0,
        yearSelectorValue: "",
        sectorSelectorValue: null,
        companySelectorValue: null,
        showYearSelector: true,
        showCompanySelector: true,
        toggleStatsSelectors: jest.fn(),
        ...overrides,
    };
}

function createCompanyData(overrides = {}) {
    return {
        sorts: ["TICKER", "CURRENCY", "PURCHASES", "SELLS", "DIVIDENDS", "PROFIT", "PROFIT_USD", "PROFIT_PERCENT"],
        companies: [
            {
                ticker: "NVDA",
                currency: "$",
                purchaseSum: "2017",
                sellSum: "2450",
                dividendSum: "135",
                profitSum: "568",
                profitUsdSum: "568",
                profitPercentage: "28.16",
            },
            {
                ticker: "SHELL",
                currency: "€",
                purchaseSum: "2028",
                sellSum: "3009.5",
                dividendSum: "0",
                profitSum: "981.5",
                profitUsdSum: "1079.65",
                profitPercentage: "48.4",
            },
        ],
        aggregates: {
            companies: 2,
            currencies: 2,
            purchaseSum: "4045",
            sellSum: "5459.5",
            dividendSum: "135",
            profitSum: "1549.5",
            profitSumUsd: "1647.65",
            profitPercentage: "38.31",
        },
        years: ["2022", "2024", "2023"],
        ...overrides,
    };
}

function createPeriodData(overrides = {}) {
    return {
        periods: [
            {
                period: "2024-Q2",
                tradesCount: 0,
                tradesProfitSum: "0",
                tradesProfitPercentage: null,
                dividendSum: "0",
            },
            {
                period: "2024-Q1",
                tradesCount: 1,
                tradesProfitSum: "433",
                tradesProfitPercentage: "21.47",
                dividendSum: "0",
            },
        ],
        aggregates: {
            periods: 2,
            tradesCount: 1,
            tradesProfitSum: "433",
            tradesProfitPercentage: "21.47",
            dividendSum: "0",
        },
        ...overrides,
    };
}

function createMonthlyPeriodData() {
    return {
        periods: Array.from({length: 12}, (_, index) => ({
            period: `2024-${String(index + 1).padStart(2, "0")}`,
            tradesCount: index === 11 ? 2 : 0,
            tradesProfitSum: index === 11 ? "123" : "0",
            tradesProfitPercentage: index === 11 ? "12.3" : null,
            dividendSum: index === 11 ? "10" : "0",
        })),
        aggregates: {
            periods: 12,
            tradesCount: 2,
            tradesProfitSum: "123",
            tradesProfitPercentage: "12.3",
            dividendSum: "10",
        },
    };
}

function createYearlyPeriodData() {
    return {
        periods: [
            {
                period: "2025",
                tradesCount: 2,
                tradesProfitSum: "600",
                tradesProfitPercentage: "20",
                dividendSum: "15",
            },
            {
                period: "2024",
                tradesCount: 1,
                tradesProfitSum: "433",
                tradesProfitPercentage: "21.47",
                dividendSum: "0",
            },
        ],
        aggregates: {
            periods: 2,
            tradesCount: 3,
            tradesProfitSum: "1033",
            tradesProfitPercentage: "20.49",
            dividendSum: "15",
        },
    };
}

describe("Stats", () => {
    beforeEach(() => {
        mockUseData.mockReset();
    });

    test("shows loader while company stats are loading", () => {
        mockUseData.mockReturnValue({
            data: null,
            loaded: false,
            error: {message: "failed"},
        });

        render(<Stats {...createProps()}/>);

        expect(screen.getByTestId("loader")).toHaveTextContent("failed");
        expect(screen.queryByText("Ticker")).not.toBeInTheDocument();
    });

    test("renders company stats table and passes query params to useData", () => {
        mockUseData.mockReturnValue({
            data: createCompanyData(),
            loaded: true,
            error: null,
        });

        render(<Stats {...createProps({
            statsTabsIndex: 0,
            yearSelectorValue: "2024",
            sectorSelectorValue: {key: "SEMICONDUCTORS"},
        })}/>);

        expect(mockUseData).toHaveBeenCalledWith("/stats/company?query&year=2024&sector=SEMICONDUCTORS");
        expect(screen.getByText("Ticker")).toBeInTheDocument();
        expect(screen.getByText("NVDA")).toBeInTheDocument();
        expect(screen.getByText("SHELL")).toBeInTheDocument();
        expect(screen.getAllByText("568")).toHaveLength(2);
        expect(screen.getByText("981.5")).toBeInTheDocument();
        expect(screen.getByText("1647.65")).toBeInTheDocument();
    });

    test("updates available years when company stats load and year selector is hidden", async () => {
        mockUseData.mockReturnValue({
            data: createCompanyData(),
            loaded: true,
            error: null,
        });

        const toggleStatsSelectors = jest.fn();

        render(<Stats {...createProps({
            statsTabsIndex: 0,
            showYearSelector: false,
            toggleStatsSelectors,
        })}/>);

        await waitFor(() => expect(toggleStatsSelectors).toHaveBeenCalledWith(["2024", "2023", "2022"], false, true));
        expect(toggleStatsSelectors).toHaveBeenCalledWith(null, false, false);
    });

    test("re-queries company stats when sortable header is clicked", async () => {
        mockUseData.mockReturnValue({
            data: createCompanyData(),
            loaded: true,
            error: null,
        });

        render(<Stats {...createProps({statsTabsIndex: 0})}/>);

        fireEvent.click(screen.getByText("Profit %"));

        await waitFor(() => expect(mockUseData).toHaveBeenLastCalledWith("/stats/company?query&sort=PROFIT_PERCENT"));
    });

    test("re-queries company stats when profit usd header is clicked", async () => {
        mockUseData.mockReturnValue({
            data: createCompanyData(),
            loaded: true,
            error: null,
        });

        render(<Stats {...createProps({statsTabsIndex: 0})}/>);

        fireEvent.click(screen.getByText("Profit $"));

        await waitFor(() => expect(mockUseData).toHaveBeenLastCalledWith("/stats/company?query&sort=PROFIT_USD"));
    });

    test("renders quarterly period stats and enables company selector when hidden", async () => {
        mockUseData.mockReturnValue({
            data: createPeriodData(),
            loaded: true,
            error: null,
        });

        const toggleStatsSelectors = jest.fn();

        render(<Stats {...createProps({
            statsTabsIndex: 2,
            companySelectorValue: {id: "company-1"},
            sectorSelectorValue: {key: "ENERGY_MINERALS"},
            showCompanySelector: false,
            toggleStatsSelectors,
        })}/>);

        expect(mockUseData).toHaveBeenCalledWith("/stats/quarterly?filter&companyId=company-1&sector=ENERGY_MINERALS");
        expect(screen.getByText("Quarter")).toBeInTheDocument();
        expect(screen.getByText("2024/Q1")).toBeInTheDocument();
        expect(screen.getByText("2024/Q2")).toBeInTheDocument();
        expect(screen.getAllByText("433")).toHaveLength(2);

        await waitFor(() => expect(toggleStatsSelectors).toHaveBeenCalledWith(null, true, true));
        expect(toggleStatsSelectors).toHaveBeenCalledWith(null, false, false);
    });

    test("renders quarterly separator styling on the last quarter row", () => {
        mockUseData.mockReturnValue({
            data: createPeriodData({
                periods: [
                    {
                        period: "2024-Q4",
                        tradesCount: 0,
                        tradesProfitSum: "0",
                        tradesProfitPercentage: null,
                        dividendSum: "0",
                    },
                    {
                        period: "2024-Q3",
                        tradesCount: 0,
                        tradesProfitSum: "0",
                        tradesProfitPercentage: null,
                        dividendSum: "0",
                    },
                    {
                        period: "2024-Q2",
                        tradesCount: 0,
                        tradesProfitSum: "0",
                        tradesProfitPercentage: null,
                        dividendSum: "0",
                    },
                    {
                        period: "2024-Q1",
                        tradesCount: 1,
                        tradesProfitSum: "433",
                        tradesProfitPercentage: "21.47",
                        dividendSum: "0",
                    },
                ],
                aggregates: {
                    periods: 4,
                    tradesCount: 1,
                    tradesProfitSum: "433",
                    tradesProfitPercentage: "21.47",
                    dividendSum: "0",
                },
            }),
            loaded: true,
            error: null,
        });

        const {container} = render(<Stats {...createProps({statsTabsIndex: 2})}/>);

        const lastDataRowFirstCell = container.querySelector("tbody tr:nth-child(4) td");
        expect(lastDataRowFirstCell).toHaveStyle("border-bottom: 1px solid black");
    });

    test("renders monthly period stats with formatted month labels and year separator styling", () => {
        mockUseData.mockReturnValue({
            data: createMonthlyPeriodData(),
            loaded: true,
            error: null,
        });

        const {container} = render(<Stats {...createProps({
            statsTabsIndex: 1,
            companySelectorValue: {id: "company-1"},
        })}/>);

        expect(mockUseData).toHaveBeenCalledWith("/stats/monthly?filter&companyId=company-1");
        expect(screen.getByText("Month")).toBeInTheDocument();
        expect(screen.getByText("2024/01")).toBeInTheDocument();
        expect(screen.getByText("2024/12")).toBeInTheDocument();
        expect(screen.getAllByText("123")).toHaveLength(2);

        const lastDataRowFirstCell = container.querySelector("tbody tr:nth-child(12) td");
        expect(lastDataRowFirstCell).toHaveStyle("border-bottom: 1px solid black");
    });

    test("renders yearly period stats with year labels", () => {
        mockUseData.mockReturnValue({
            data: createYearlyPeriodData(),
            loaded: true,
            error: null,
        });

        render(<Stats {...createProps({
            statsTabsIndex: 3,
            sectorSelectorValue: {key: "SEMICONDUCTORS"},
        })}/>);

        expect(mockUseData).toHaveBeenCalledWith("/stats/yearly?filter&sector=SEMICONDUCTORS");
        expect(screen.getByText("Year")).toBeInTheDocument();
        expect(screen.getByText("2025")).toBeInTheDocument();
        expect(screen.getByText("2024")).toBeInTheDocument();
        expect(screen.getByText("1033")).toBeInTheDocument();
        expect(screen.getByText("20.49")).toBeInTheDocument();
    });
});
