import React from "react";
import {fireEvent, render, screen, waitFor, within} from "@testing-library/react";

const mockUseData = jest.fn();
const mockRecordEvent = jest.fn();

jest.mock("../../service/BackendService", () => ({
    useData: (...args) => mockUseData(...args),
}));

jest.mock("../../service/utils", () => ({
    recordEvent: (...args) => mockRecordEvent(...args),
}));

jest.mock("../../components/Loader", () => (props) => (
    <div data-testid="loader">{props.error ? props.error.message : "loading"}</div>
));

jest.mock("../../dialog/EditCompanyDialog", () => (props) => (
    <button onClick={props.triggerRefresh}>edit-company-dialog</button>
));

import {Companies} from "../Companies";

function createProps(overrides = {}) {
    return {
        currencySelectorValue: "",
        sectorSelectorValue: null,
        toggleCompaniesSelectors: jest.fn(),
        setOpenEditCompany: jest.fn(),
        activeStates: ["ACTIVE", "CLOSED"],
        ...overrides,
    };
}

function createData(overrides = {}) {
    return {
        sorts: ["TICKER", "CURRENCY", "WATCHING", "SECTOR", "ALL_TRADES", "ACTIVE_TRADES", "DIVIDENDS", "RECORDS", "PERIODS"],
        companies: [
            {
                id: "company-1",
                ticker: "NVDA",
                currency: "$",
                watching: true,
                sector: {key: "SEMICONDUCTORS", name: "Semiconductors"},
                totalTrades: 11,
                activeTrades: 7,
                dividends: 5,
                records: 3,
                periods: 2,
            },
            {
                id: "company-2",
                ticker: "SHELL",
                currency: "€",
                watching: false,
                sector: {key: "ENERGY_MINERALS", name: "Energy Minerals"},
                totalTrades: 4,
                activeTrades: 1,
                dividends: 0,
                records: 2,
                periods: 0,
            },
        ],
        ...overrides,
    };
}

describe("Companies", () => {
    const originalLocation = window.location;

    beforeAll(() => {
        delete window.location;
        window.location = {pathname: "/companies", href: "/companies"};
    });

    afterAll(() => {
        window.location = originalLocation;
    });

    beforeEach(() => {
        mockUseData.mockReset();
        mockRecordEvent.mockReset();
        sessionStorage.clear();
    });

    test("shows loader while company data is loading", () => {
        mockUseData.mockReturnValue({
            data: null,
            loaded: false,
            error: {message: "failed"},
        });

        render(<Companies {...createProps()}/>);

        expect(screen.getByTestId("loader")).toHaveTextContent("failed");
        expect(screen.queryByText("Ticker")).not.toBeInTheDocument();
    });

    test("renders companies table and passes filters to useData", async () => {
        mockUseData.mockReturnValue({
            data: createData(),
            loaded: true,
            error: null,
        });

        const toggleCompaniesSelectors = jest.fn();

        render(<Companies {...createProps({
            currencySelectorValue: "$",
            sectorSelectorValue: {key: "SEMICONDUCTORS"},
            toggleCompaniesSelectors,
        })}/>);

        expect(mockUseData).toHaveBeenCalledWith("/company?query&currency=$&sector=SEMICONDUCTORS");
        expect(screen.getByText("Ticker")).toBeInTheDocument();
        expect(screen.getByText("NVDA")).toBeInTheDocument();
        expect(screen.getByText("SHELL")).toBeInTheDocument();
        expect(screen.getByText("Semiconductors")).toBeInTheDocument();
        expect(screen.getByText("Energy Minerals")).toBeInTheDocument();

        await waitFor(() => expect(toggleCompaniesSelectors).toHaveBeenCalled());
    });

    test("re-queries companies when sortable header is clicked", async () => {
        mockUseData.mockReturnValue({
            data: createData(),
            loaded: true,
            error: null,
        });

        render(<Companies {...createProps()}/>);

        fireEvent.click(screen.getByText("Active Trades"));

        await waitFor(() => expect(mockUseData).toHaveBeenLastCalledWith("/company?query&sort=ACTIVE_TRADES"));
    });

    test("refreshes company query when dialog triggers refresh", async () => {
        mockUseData.mockReturnValue({
            data: createData(),
            loaded: true,
            error: null,
        });

        const getTimeSpy = jest.spyOn(Date.prototype, "getTime").mockReturnValue(12345);

        render(<Companies {...createProps()}/>);

        fireEvent.click(screen.getByText("edit-company-dialog"));

        await waitFor(() => expect(mockUseData).toHaveBeenLastCalledWith("/company?query&refresh12345"));

        getTimeSpy.mockRestore();
    });

    test("redirects from trade aggregates and stores company context", async () => {
        const data = createData();

        mockUseData.mockReturnValue({
            data,
            loaded: true,
            error: null,
        });

        render(<Companies {...createProps()}/>);

        const totalTradesCell = screen.getByText("11").closest("td");

        fireEvent.mouseEnter(totalTradesCell);
        fireEvent.click(within(totalTradesCell).getByRole("button"));

        expect(sessionStorage.getItem("companyId")).toBe("company-1");
        expect(sessionStorage.getItem("tradeState")).toBeNull();
        expect(sessionStorage.getItem("showFinancials")).toBeNull();
        expect(mockRecordEvent).toHaveBeenCalledWith("/companies#redirect:/trades");
        expect(window.location.href).toBe("/trades");
    });

    test("opens company edit action from ticker cell", () => {
        const data = createData();

        mockUseData.mockReturnValue({
            data,
            loaded: true,
            error: null,
        });

        const setOpenEditCompany = jest.fn();

        render(<Companies {...createProps({setOpenEditCompany})}/>);

        const tickerCell = screen.getByText("NVDA").closest("td");

        fireEvent.mouseEnter(tickerCell);
        fireEvent.click(within(tickerCell).getByRole("button"));

        expect(setOpenEditCompany).toHaveBeenCalledWith(data.companies[0]);
    });
});
