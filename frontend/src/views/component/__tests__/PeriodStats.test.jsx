import React from "react";
import {render, screen} from "@testing-library/react";

const mockUseData = jest.fn();

jest.mock("../../../service/BackendService", () => ({
    useData: (...args) => mockUseData(...args),
}));

jest.mock("../Loader", () => ({
    Loader: (props) => (
        <div data-testid="loader">{props.error ? props.error.message : "loading"}</div>
    ),
}));

import {PeriodStats} from "../PeriodStats";

function createProps(overrides = {}) {
    return {
        type: "quarterly",
        sectorSelectorValue: null,
        companySelectorValue: null,
        ...overrides,
    };
}

function createPeriodData(overrides = {}) {
    return {
        periods: [
            {
                period: "2024-Q2",
                tradesCount: 0,
                tradesProfitSum: 0,
                tradesProfitPercentage: null,
                dividendSum: 0,
            },
            {
                period: "2024-Q1",
                tradesCount: 1,
                tradesProfitSum: 433,
                tradesProfitPercentage: 21.47,
                dividendSum: 0,
            },
        ],
        aggregates: {
            periods: 2,
            tradesCount: 1,
            tradesProfitSum: 433,
            tradesProfitPercentage: 21.47,
            dividendSum: 0,
        },
        ...overrides,
    };
}

function createMonthlyPeriodData() {
    return {
        periods: Array.from({length: 12}, (_, index) => ({
            period: `2024-${String(index + 1).padStart(2, "0")}`,
            tradesCount: index === 11 ? 2 : 0,
            tradesProfitSum: index === 11 ? 123 : 0,
            tradesProfitPercentage: index === 11 ? 12.3 : null,
            dividendSum: index === 11 ? 10 : 0,
        })),
        aggregates: {
            periods: 12,
            tradesCount: 2,
            tradesProfitSum: 123,
            tradesProfitPercentage: 12.3,
            dividendSum: 10,
        },
    };
}

function createYearlyPeriodData() {
    return {
        periods: [
            {
                period: "2025",
                tradesCount: 2,
                tradesProfitSum: 600,
                tradesProfitPercentage: 20,
                dividendSum: 15,
            },
            {
                period: "2024",
                tradesCount: 1,
                tradesProfitSum: 433,
                tradesProfitPercentage: 21.47,
                dividendSum: 0,
            },
        ],
        aggregates: {
            periods: 2,
            tradesCount: 3,
            tradesProfitSum: 1033,
            tradesProfitPercentage: 20.49,
            dividendSum: 15,
        },
    };
}

describe("PeriodStats", () => {
    beforeEach(() => {
        mockUseData.mockReset();
    });

    test("shows loader while period stats are loading", () => {
        mockUseData.mockReturnValue({
            data: null,
            loaded: false,
            error: {message: "failed"},
        });

        render(<PeriodStats {...createProps()}/>);

        expect(screen.getByTestId("loader")).toHaveTextContent("failed");
        expect(screen.queryByText("Trades")).not.toBeInTheDocument();
    });

    test("renders quarterly period stats", () => {
        mockUseData.mockReturnValue({
            data: createPeriodData(),
            loaded: true,
            error: null,
        });

        render(<PeriodStats {...createProps({
            companySelectorValue: {id: "company-1"},
            sectorSelectorValue: {key: "ENERGY_MINERALS"},
        })}/>);

        expect(mockUseData).toHaveBeenCalledWith("/stats/quarterly?filter&companyId=company-1&sector=ENERGY_MINERALS");
        expect(screen.getByText("Quarter")).toBeInTheDocument();
        expect(screen.getByText("2024/Q1")).toBeInTheDocument();
        expect(screen.getByText("2024/Q2")).toBeInTheDocument();
        expect(screen.getAllByText("433")).toHaveLength(2);
    });

    test("renders quarterly separator styling on the last quarter row", () => {
        mockUseData.mockReturnValue({
            data: createPeriodData({
                periods: [
                    {
                        period: "2024-Q4",
                        tradesCount: 0,
                        tradesProfitSum: 0,
                        tradesProfitPercentage: null,
                        dividendSum: 0,
                    },
                    {
                        period: "2024-Q3",
                        tradesCount: 0,
                        tradesProfitSum: 0,
                        tradesProfitPercentage: null,
                        dividendSum: 0,
                    },
                    {
                        period: "2024-Q2",
                        tradesCount: 0,
                        tradesProfitSum: 0,
                        tradesProfitPercentage: null,
                        dividendSum: 0,
                    },
                    {
                        period: "2024-Q1",
                        tradesCount: 1,
                        tradesProfitSum: 433,
                        tradesProfitPercentage: 21.47,
                        dividendSum: 0,
                    },
                ],
                aggregates: {
                    periods: 4,
                    tradesCount: 1,
                    tradesProfitSum: 433,
                    tradesProfitPercentage: 21.47,
                    dividendSum: 0,
                },
            }),
            loaded: true,
            error: null,
        });

        const {container} = render(<PeriodStats {...createProps()}/>);

        const lastDataRowFirstCell = container.querySelector("tbody tr:nth-child(4) td");
        expect(lastDataRowFirstCell).toHaveStyle("border-bottom: 1px solid black");
    });

    test("renders monthly period stats with formatted month labels and year separator styling", () => {
        mockUseData.mockReturnValue({
            data: createMonthlyPeriodData(),
            loaded: true,
            error: null,
        });

        const {container} = render(<PeriodStats {...createProps({
            type: "monthly",
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

        render(<PeriodStats {...createProps({
            type: "yearly",
            sectorSelectorValue: {key: "SEMICONDUCTORS"},
        })}/>);

        expect(mockUseData).toHaveBeenCalledWith("/stats/yearly?filter&sector=SEMICONDUCTORS");
        expect(screen.getByText("Year")).toBeInTheDocument();
        expect(screen.getByText("2025")).toBeInTheDocument();
        expect(screen.getByText("2024")).toBeInTheDocument();
        expect(screen.getByText("1,033")).toBeInTheDocument();
        expect(screen.getByText("20.49")).toBeInTheDocument();
    });
});
