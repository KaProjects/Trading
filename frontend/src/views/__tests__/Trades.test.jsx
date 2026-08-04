import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";

const mockUseData = jest.fn();
const mockFormatDate = jest.fn((value) => `formatted:${value ?? ""}`);

jest.mock("../../service/BackendService", () => ({
    useData: (...args) => mockUseData(...args),
}));

jest.mock("../../service/FormattingService", () => ({
    ...jest.requireActual("../../service/FormattingService"),
    formatDate: (...args) => mockFormatDate(...args),
}));

jest.mock("../component/Loader", () => ({
    Loader: (props) => (
        <div data-testid="loader">{props.error ? props.error.message : "loading"}</div>
    ),
}));

jest.mock("../../dialog/AddTradeDialog", () => ({
    AddTradeDialog: (props) => (
        <button onClick={props.triggerRefresh}>add-trade-dialog</button>
    )
}));

jest.mock("../../dialog/SellTradeDialog", () => ({
    SellTradeDialog: (props) => (
        <button onClick={props.triggerRefresh}>sell-trade-dialog</button>
    )
}));

import {Trades} from "../Trades";

function createProps(overrides = {}) {
    return {
        activeSelectorValue: "",
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
        trades: [
            {
                id: "trade-1",
                ticker: "NVDA",
                currency: "$",
                company: {
                    ticker: "NVDA",
                    currency: "$",
                },
                portfolio: {
                    key: "PATRIA_MARGIN",
                    name: "Patria - Margin",
                    abbreviation: "Pm",
                },
                purchaseDate: "2024-01-10",
                purchaseQuantity: "5",
                purchasePrice: "400.5",
                purchaseFees: "14.5",
                purchaseTotal: 2017.0,
                sellDate: "2025-01-05",
                sellQuantity: "5",
                sellPrice: "500.0",
                sellFees: "14.5",
                sellTotal: 2485.5,
                profit: 468.5,
                profitPercentage: 23.23,
            },
            {
                id: "trade-2",
                ticker: "SHELL",
                currency: "€",
                company: {
                    ticker: "SHELL",
                    currency: "€",
                },
                purchaseDate: "2023-03-15",
                purchaseQuantity: "8",
                purchasePrice: "28.0",
                purchaseFees: "10.0",
                purchaseTotal: 234.0,
                sellDate: null,
                sellQuantity: null,
                sellPrice: null,
                sellFees: null,
                sellTotal: null,
                profit: null,
                profitPercentage: null,
            },
        ],
        aggregates: {
            companies: 2,
            currencies: 2,
            portfolios: 1,
            purchaseFees: "24.5",
            purchaseTotal: 2251.0,
            sellFees: "14.5",
            sellTotal: 2485.5,
            profit: 468.5,
            profitPercentage: 23.23,
        },
        ...overrides,
    };
}

describe("Trades", () => {
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

        render(<Trades {...createProps()}/>);

        expect(screen.getByTestId("loader")).toHaveTextContent("failed");
        expect(screen.queryByText("Ticker")).not.toBeInTheDocument();
    });

    test("renders trades table and passes filter query to useData", () => {
        mockUseData.mockReturnValue({
            data: createData(),
            loaded: true,
            error: null,
        });

        render(<Trades {...createProps({
            activeSelectorValue: "Closed",
            companySelectorValue: {id: "company-1"},
            currencySelectorValue: "$",
            yearSelectorValue: "2024",
            sectorSelectorValue: {key: "SEMICONDUCTORS"},
        })}/>);

        expect(mockUseData).toHaveBeenCalledWith("/trade?filter&active=false&companyId=company-1&currency=$&year=2024&sector=SEMICONDUCTORS");
        expect(screen.getByText("NVDA")).toBeInTheDocument();
        expect(screen.getByText("SHELL")).toBeInTheDocument();
        expect(screen.getByText("@")).toBeInTheDocument();
        expect(screen.getByText("#")).toHaveStyle({width: "35px", minWidth: "35px", maxWidth: "35px", textAlign: "center", verticalAlign: "middle", paddingLeft: "0", paddingRight: "0"});
        expect(screen.getByText("@")).toHaveStyle({width: "35px", minWidth: "35px", maxWidth: "35px"});
        expect(screen.getByText("Pm")).toHaveAttribute("title", "Patria - Margin");
        expect(screen.getByText("Pm")).toHaveStyle({width: "35px", minWidth: "35px", maxWidth: "35px", paddingLeft: "7px", paddingRight: "7px"});
        const sumRowCells = screen.getAllByRole("row").at(-1).querySelectorAll("td");
        expect(sumRowCells[2]).toHaveStyle({fontWeight: "bold", textAlign: "center", width: "35px"});
        expect(mockFormatDate).toHaveBeenCalledWith("2024-01-10");
        expect(mockFormatDate).toHaveBeenCalledWith("2025-01-05");
        expect(mockFormatDate).toHaveBeenCalledWith(null);
        expect(screen.getAllByText("468.5")).toHaveLength(2);
        expect(screen.getAllByText("23.23")).toHaveLength(2);
        expect(screen.getByText("2,251")).toBeInTheDocument();
        expect(screen.getAllByText("2,485.5")).toHaveLength(2);
    });

    test("selects company on ticker double click", () => {
        mockUseData.mockReturnValue({
            data: createData(),
            loaded: true,
            error: null,
        });

        const nvidia = {id: "company-1", ticker: "NVDA"};
        const shell = {id: "company-2", ticker: "SHELL"};
        const setCompanySelectorValue = jest.fn();

        render(<Trades {...createProps({
            companies: [nvidia, shell],
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

        render(<Trades {...createProps()}/>);

        fireEvent.click(screen.getByText("add-trade-dialog"));

        await waitFor(() => expect(mockUseData).toHaveBeenLastCalledWith("/trade?filter&refresh12345"));

        getTimeSpy.mockRestore();
    });
});
