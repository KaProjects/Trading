import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";

const mockUseData = jest.fn();

jest.mock("../../../service/BackendService", () => ({
    useData: (...args) => mockUseData(...args),
}));

jest.mock("../Loader", () => ({
    Loader: (props) => (
        <div data-testid="loader">{props.error ? props.error.message : "loading"}</div>
    ),
}));

import {CompanyStats} from "../CompanyStats";

function createProps(overrides = {}) {
    return {
        type: "company",
        yearSelectorValue: "",
        sectorSelectorValue: null,
        setYears: jest.fn(),
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
                purchaseSum: 2017,
                sellSum: 2450,
                dividendSum: 135,
                profitSum: 568,
                profitUsdSum: 568,
                profitPercentage: 28.16,
            },
            {
                ticker: "SHELL",
                currency: "€",
                purchaseSum: 2028,
                sellSum: 3009.5,
                dividendSum: 0,
                profitSum: 981.5,
                profitUsdSum: 1079.65,
                profitPercentage: 48.4,
            },
        ],
        aggregates: {
            companies: 2,
            currencies: 2,
            purchaseSum: 4045,
            sellSum: 5459.5,
            dividendSum: 135,
            profitSum: 1549.5,
            profitSumUsd: 1647.65,
            profitPercentage: 38.31,
        },
        years: ["2022", "2024", "2023"],
        ...overrides,
    };
}

describe("CompanyStats", () => {
    beforeEach(() => {
        mockUseData.mockReset();
    });

    test("shows loader while company stats are loading", () => {
        mockUseData.mockReturnValue({
            data: null,
            loaded: false,
            error: {message: "failed"},
        });

        render(<CompanyStats {...createProps()}/>);

        expect(screen.getByTestId("loader")).toHaveTextContent("failed");
        expect(screen.queryByText("Ticker")).not.toBeInTheDocument();
    });

    test("renders company stats table and passes query params to useData", () => {
        mockUseData.mockReturnValue({
            data: createCompanyData(),
            loaded: true,
            error: null,
        });

        render(<CompanyStats {...createProps({
            yearSelectorValue: "2024",
            sectorSelectorValue: {key: "SEMICONDUCTORS"},
        })}/>);

        expect(mockUseData).toHaveBeenCalledWith("/stats/company?query&year=2024&sector=SEMICONDUCTORS");
        expect(screen.getByText("Ticker")).toBeInTheDocument();
        expect(screen.getByText("NVDA")).toBeInTheDocument();
        expect(screen.getByText("SHELL")).toBeInTheDocument();
        expect(screen.getAllByText("568")).toHaveLength(2);
        expect(screen.getByText("982")).toBeInTheDocument();
        expect(screen.getByText("1,648")).toBeInTheDocument();
    });

    test("publishes available years when company stats load", async () => {
        mockUseData.mockReturnValue({
            data: createCompanyData(),
            loaded: true,
            error: null,
        });

        const setYears = jest.fn();

        render(<CompanyStats {...createProps({setYears})}/>);

        await waitFor(() => expect(setYears).toHaveBeenCalledWith(["2024", "2023", "2022"]));
    });

    test("keeps component mounted when parent rerenders after years are published", async () => {
        mockUseData.mockReturnValue({
            data: createCompanyData(),
            loaded: true,
            error: null,
        });

        const setYears = jest.fn();
        const {rerender} = render(<CompanyStats {...createProps({setYears})}/>);

        await waitFor(() => expect(setYears).toHaveBeenCalledWith(["2024", "2023", "2022"]));

        fireEvent.click(screen.getByText("Profit %"));

        rerender(<CompanyStats {...createProps({setYears})}/>);

        expect(mockUseData).toHaveBeenLastCalledWith("/stats/company?query&sort=PROFIT_PERCENT");
    });

    test("re-queries company stats when sortable header is clicked", async () => {
        mockUseData.mockReturnValue({
            data: createCompanyData(),
            loaded: true,
            error: null,
        });

        render(<CompanyStats {...createProps()}/>);

        fireEvent.click(screen.getByText("Profit %"));

        await waitFor(() => expect(mockUseData).toHaveBeenLastCalledWith("/stats/company?query&sort=PROFIT_PERCENT"));
    });

    test("re-queries company stats when profit usd header is clicked", async () => {
        mockUseData.mockReturnValue({
            data: createCompanyData(),
            loaded: true,
            error: null,
        });

        render(<CompanyStats {...createProps()}/>);

        fireEvent.click(screen.getByText("Profit $"));

        await waitFor(() => expect(mockUseData).toHaveBeenLastCalledWith("/stats/company?query&sort=PROFIT_USD"));
    });
});
