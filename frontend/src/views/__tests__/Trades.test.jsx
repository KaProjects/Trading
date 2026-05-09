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

jest.mock("../../components/Loader", () => (props) => (
    <div data-testid="loader">{props.error ? props.error.message : "loading"}</div>
));

jest.mock("../../dialog/AddTradeDialog", () => (props) => (
    <button onClick={props.triggerRefresh}>add-trade-dialog</button>
));

jest.mock("../../dialog/SellTradeDialog", () => (props) => (
    <button onClick={props.triggerRefresh}>sell-trade-dialog</button>
));

import {Trades} from "../Trades";

function createProps(overrides = {}) {
    return {
        activeSelectorValue: "",
        activeStates: ["Active", "Closed"],
        companySelectorValue: null,
        currencySelectorValue: "",
        yearSelectorValue: "",
        sectorSelectorValue: null,
        showYearSelector: true,
        toggleTradesSelectors: jest.fn(),
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
                purchaseDate: "10.01.2024",
                purchaseQuantity: "5",
                purchasePrice: "400.5",
                purchaseFees: "14.5",
                purchaseTotal: "2017.0",
                sellDate: "05.01.2025",
                sellQuantity: "5",
                sellPrice: "500.0",
                sellFees: "14.5",
                sellTotal: "2485.5",
                profit: "468.5",
                profitPercentage: "23.23",
            },
            {
                id: "trade-2",
                ticker: "SHELL",
                currency: "€",
                purchaseDate: "15.03.2023",
                purchaseQuantity: "8",
                purchasePrice: "28.0",
                purchaseFees: "10.0",
                purchaseTotal: "234.0",
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
            purchaseFees: "24.5",
            purchaseTotal: "2251.0",
            sellFees: "14.5",
            sellTotal: "2485.5",
            profit: "468.5",
            profitPercentage: "23.23",
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
        expect(mockFormatDate).toHaveBeenCalledWith("10.01.2024");
        expect(mockFormatDate).toHaveBeenCalledWith("05.01.2025");
        expect(screen.getAllByText("468.5")).toHaveLength(2);
        expect(screen.getAllByText("23.23")).toHaveLength(2);
        expect(screen.getByText("2251.0")).toBeInTheDocument();
        expect(screen.getAllByText("2485.5")).toHaveLength(2);
    });

    test("collects years from trade dates when year selector is hidden", async () => {
        mockUseData.mockReturnValue({
            data: createData({
                trades: [
                    {
                        id: "trade-1",
                        ticker: "NVDA",
                        currency: "$",
                        purchaseDate: "10.01.2024",
                        purchaseQuantity: "5",
                        purchasePrice: "400.5",
                        purchaseFees: "14.5",
                        purchaseTotal: "2017.0",
                        sellDate: "05.01.2025",
                        sellQuantity: "5",
                        sellPrice: "500.0",
                        sellFees: "14.5",
                        sellTotal: "2485.5",
                        profit: "468.5",
                        profitPercentage: "23.23",
                    },
                    {
                        id: "trade-2",
                        ticker: "SHELL",
                        currency: "€",
                        purchaseDate: "15.03.2023",
                        purchaseQuantity: "8",
                        purchasePrice: "28.0",
                        purchaseFees: "10.0",
                        purchaseTotal: "234.0",
                        sellDate: null,
                        sellQuantity: null,
                        sellPrice: null,
                        sellFees: null,
                        sellTotal: null,
                        profit: null,
                        profitPercentage: null,
                    },
                    {
                        id: "trade-3",
                        ticker: "CEZ",
                        currency: "K",
                        purchaseDate: "01.11.2025",
                        purchaseQuantity: "2",
                        purchasePrice: "30.0",
                        purchaseFees: "2.0",
                        purchaseTotal: "62.0",
                        sellDate: "10.12.2025",
                        sellQuantity: "2",
                        sellPrice: "40.0",
                        sellFees: "1.0",
                        sellTotal: "79.0",
                        profit: "17.0",
                        profitPercentage: "27.42",
                    },
                ],
            }),
            loaded: true,
            error: null,
        });

        const toggleTradesSelectors = jest.fn();

        render(<Trades {...createProps({
            showYearSelector: false,
            toggleTradesSelectors,
        })}/>);

        await waitFor(() => expect(toggleTradesSelectors).toHaveBeenCalledWith(["2025", "2024", "2023"]));
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
