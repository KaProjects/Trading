import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";

const mockUseData = jest.fn();

jest.mock("../../service/BackendService", () => ({
    useData: (...args) => mockUseData(...args),
}));

jest.mock("../../components/Loader", () => (props) => (
    <div data-testid="loader">{props.error ? props.error.message : "loading"}</div>
));

jest.mock("../../dialog/AddDividendDialog", () => (props) => (
    <button onClick={props.triggerRefresh}>add-dividend-dialog</button>
));

import {Dividends} from "../Dividends";

function createProps(overrides = {}) {
    return {
        companySelectorValue: null,
        currencySelectorValue: "",
        yearSelectorValue: "",
        sectorSelectorValue: null,
        showYearSelector: true,
        toggleDividendsSelectors: jest.fn(),
        companies: [],
        setCompanySelectorValue: jest.fn(),
        ...overrides,
    };
}

function createData(overrides = {}) {
    return {
        dividends: [
            {
                id: "dividend-1",
                ticker: "NVDA",
                currency: "$",
                company: {
                    ticker: "NVDA",
                    currency: "$",
                },
                date: "01.12.2022",
                dividend: "80",
                tax: "8",
                net: "72",
            },
            {
                id: "dividend-2",
                ticker: "CEZ",
                currency: "K",
                company: {
                    ticker: "CEZ",
                    currency: "K",
                },
                date: "01.12.2021",
                dividend: "1000",
                tax: "100",
                net: "900",
            },
        ],
        aggregates: {
            companies: 2,
            currencies: 2,
            dividendSum: "1080",
            taxSum: "108",
            netSum: "972",
        },
        ...overrides,
    };
}

describe("Dividends", () => {
    beforeEach(() => {
        mockUseData.mockReset();
    });

    test("shows loader while data is loading", () => {
        mockUseData.mockReturnValue({
            data: null,
            loaded: false,
            error: {message: "failed"},
        });

        render(<Dividends {...createProps()}/>);

        expect(screen.getByTestId("loader")).toHaveTextContent("failed");
        expect(screen.queryByText("Ticker")).not.toBeInTheDocument();
    });

    test("renders dividends table and passes filter query to useData", () => {
        mockUseData.mockReturnValue({
            data: createData(),
            loaded: true,
            error: null,
        });

        render(<Dividends {...createProps({
            companySelectorValue: {id: "company-1"},
            currencySelectorValue: "$",
            yearSelectorValue: "2024",
            sectorSelectorValue: {key: "SEMICONDUCTORS"},
        })}/>);

        expect(mockUseData).toHaveBeenCalledWith("/dividend?filter&companyId=company-1&currency=$&year=2024&sector=SEMICONDUCTORS");
        expect(screen.getByText("NVDA")).toBeInTheDocument();
        expect(screen.getByText("CEZ")).toBeInTheDocument();
        expect(screen.getByText("01.12.2022")).toBeInTheDocument();
        expect(screen.getByText("72")).toBeInTheDocument();
        expect(screen.getByText("972")).toBeInTheDocument();
    });

    test("collects years from dividends when year selector is hidden", async () => {
        mockUseData.mockReturnValue({
            data: createData({
                dividends: [
                    {
                        id: "dividend-1",
                        ticker: "NVDA",
                        currency: "$",
                        company: {
                            ticker: "NVDA",
                            currency: "$",
                        },
                        date: "01.06.2021",
                        dividend: "70",
                        tax: "7",
                        net: "63",
                    },
                    {
                        id: "dividend-2",
                        ticker: "CEZ",
                        currency: "K",
                        company: {
                            ticker: "CEZ",
                            currency: "K",
                        },
                        date: "01.12.2021",
                        dividend: "1000",
                        tax: "100",
                        net: "900",
                    },
                    {
                        id: "dividend-3",
                        ticker: "ABCD",
                        currency: "$",
                        company: {
                            ticker: "ABCD",
                            currency: "$",
                        },
                        date: "10.01.2025",
                        dividend: "10",
                        tax: "1",
                        net: "9",
                    },
                ],
            }),
            loaded: true,
            error: null,
        });

        const toggleDividendsSelectors = jest.fn();

        render(<Dividends {...createProps({
            showYearSelector: false,
            toggleDividendsSelectors,
        })}/>);

        await waitFor(() => expect(toggleDividendsSelectors).toHaveBeenCalledWith(["2025", "2021"]));
    });

    test("selects company on ticker double click", () => {
        mockUseData.mockReturnValue({
            data: createData(),
            loaded: true,
            error: null,
        });

        const nvidia = {id: "company-1", ticker: "NVDA"};
        const cez = {id: "company-2", ticker: "CEZ"};
        const setCompanySelectorValue = jest.fn();

        render(<Dividends {...createProps({
            companies: [nvidia, cez],
            setCompanySelectorValue,
        })}/>);

        fireEvent.doubleClick(screen.getByText("NVDA"));

        expect(setCompanySelectorValue).toHaveBeenCalledWith(nvidia);
    });

    test("refreshes the data path when dialog triggers refresh", async () => {
        mockUseData.mockReturnValue({
            data: createData(),
            loaded: true,
            error: null,
        });

        const getTimeSpy = jest.spyOn(Date.prototype, "getTime").mockReturnValue(12345);

        render(<Dividends {...createProps()}/>);

        fireEvent.click(screen.getByText("add-dividend-dialog"));

        await waitFor(() => expect(mockUseData).toHaveBeenLastCalledWith("/dividend?filter&refresh12345"));

        getTimeSpy.mockRestore();
    });
});
