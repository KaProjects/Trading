import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";

const mockUseData = jest.fn();
const mockFormatDate = jest.fn((value) => `formatted:${value ?? ""}`);

jest.mock("../../service/BackendService", () => ({
    useData: (...args) => mockUseData(...args),
}));

jest.mock("../../service/FormattingService", () => ({
    formatDate: (...args) => mockFormatDate(...args),
}));

jest.mock("../component/Loader", () => ({
    Loader: (props) => (
        <div data-testid="loader">{props.error ? props.error.message : "loading"}</div>
    ),
}));

jest.mock("../../dialog/AddDividendDialog", () => ({
    AddDividendDialog: (props) => (
        <div>
            <div>recently-owned:{props.recentlyOwnedCompanies.map(company => company.ticker).join(",")}</div>
            <button onClick={props.triggerRefresh}>add-dividend-dialog</button>
        </div>
    )
}));

import {Dividends} from "../Dividends";

function createProps(overrides = {}) {
    return {
        companySelectorValue: null,
        currencySelectorValue: "",
        yearSelectorValue: "",
        sectorSelectorValue: null,
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
                date: "2022-12-01",
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
                date: "2021-12-01",
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

function mockLoadedData() {
    mockUseData.mockImplementation(path => path === "/company/recently-owned"
        ? {
            data: [{id: "company-1", ticker: "NVDA"}],
            loaded: true,
            error: null,
        }
        : {
            data: createData(),
            loaded: true,
            error: null,
        }
    );
}

describe("Dividends", () => {
    beforeEach(() => {
        mockUseData.mockReset();
        mockFormatDate.mockClear();
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
        mockLoadedData();

        render(<Dividends {...createProps({
            companySelectorValue: {id: "company-1"},
            currencySelectorValue: "$",
            yearSelectorValue: "2024",
            sectorSelectorValue: {key: "SEMICONDUCTORS"},
        })}/>);

        expect(mockUseData).toHaveBeenCalledWith("/dividend?filter&companyId=company-1&currency=$&year=2024&sector=SEMICONDUCTORS");
        expect(mockUseData).toHaveBeenCalledWith("/company/recently-owned");
        expect(screen.getByText("recently-owned:NVDA")).toBeInTheDocument();
        expect(screen.getByText("NVDA")).toBeInTheDocument();
        expect(screen.getByText("CEZ")).toBeInTheDocument();
        expect(mockFormatDate).toHaveBeenCalledWith("2022-12-01");
        expect(mockFormatDate).toHaveBeenCalledWith("2021-12-01");
        expect(screen.getByText("72")).toBeInTheDocument();
        expect(screen.getByText("972")).toBeInTheDocument();
    });

    test("selects company on ticker double click", () => {
        mockLoadedData();

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
        mockLoadedData();

        const getTimeSpy = jest.spyOn(Date.prototype, "getTime").mockReturnValue(12345);

        render(<Dividends {...createProps()}/>);

        fireEvent.click(screen.getByText("add-dividend-dialog"));

        await waitFor(() => expect(mockUseData).toHaveBeenCalledWith("/dividend?filter&refresh12345"));

        getTimeSpy.mockRestore();
    });
});
