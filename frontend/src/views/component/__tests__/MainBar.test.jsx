import React from "react";
import {fireEvent, render, screen} from "@testing-library/react";

const mockNavigate = jest.fn();

jest.mock("../MainBarSelect", () => ({
    MainBarSelect: ({label}) => <div>selector:{label}</div>,
}));

jest.mock("react-router-dom", () => {
    const actual = jest.requireActual("react-router-dom");
    return {
        ...actual,
        useNavigate: () => mockNavigate,
    };
});

import {MainBar} from "../MainBar";

function createProps(overrides = {}) {
    return {
        showStatsTabs: false,
        statsTabsIndex: 0,
        setStatsTabsIndex: jest.fn(),
        showSellTradeButton: false,
        setOpenSellTrade: jest.fn(),
        showAddTradeButton: false,
        setOpenAddTrade: jest.fn(),
        showAddDividendButton: false,
        setOpenAddDividend: jest.fn(),
        showAddCompanyButton: false,
        setOpenEditCompany: jest.fn(),
        showActiveSelector: false,
        activeStates: ["all", "only active"],
        activeSelectorValue: "",
        setActiveSelectorValue: jest.fn(),
        showCompanySelector: false,
        companies: [{id: "company-1", ticker: "NVDA"}],
        companySelectorValue: "",
        setCompanySelectorValue: jest.fn(),
        showCurrencySelector: false,
        currencies: ["$", "EUR"],
        currencySelectorValue: "",
        setCurrencySelectorValue: jest.fn(),
        showYearSelector: null,
        yearSelectorValue: "",
        setYearSelectorValue: jest.fn(),
        showSectorSelector: false,
        sectors: [{name: "Technology"}],
        sectorSelectorValue: "",
        setSectorSelectorValue: jest.fn(),
        ...overrides,
    };
}

describe("MainBar", () => {
    beforeEach(() => {
        mockNavigate.mockReset();
    });

    test("renders enabled selectors and tabs", () => {
        render(
            <MainBar
                {...createProps({
                    showStatsTabs: true,
                    showActiveSelector: true,
                    showCompanySelector: true,
                    showCurrencySelector: true,
                    showYearSelector: ["2024", "2025"],
                    showSectorSelector: true,
                })}
            />
        );

        expect(screen.getByRole("tab", {name: "Companies"})).toBeInTheDocument();
        expect(screen.getByRole("tab", {name: "Monthly"})).toBeInTheDocument();
        expect(screen.getByRole("tab", {name: "Quarterly"})).toBeInTheDocument();
        expect(screen.getByRole("tab", {name: "Yearly"})).toBeInTheDocument();
        expect(screen.getByText("selector:all")).toBeInTheDocument();
        expect(screen.getByText("selector:companies")).toBeInTheDocument();
        expect(screen.getByText("selector:currencies")).toBeInTheDocument();
        expect(screen.getByText("selector:years")).toBeInTheDocument();
        expect(screen.getByText("selector:sectors")).toBeInTheDocument();
    });

    test("handles navigation, tab change and action buttons", () => {
        const setStatsTabsIndex = jest.fn();
        const setOpenSellTrade = jest.fn();
        const setOpenAddTrade = jest.fn();
        const setOpenAddDividend = jest.fn();
        const setOpenEditCompany = jest.fn();

        render(
            <MainBar
                {...createProps({
                    showStatsTabs: true,
                    setStatsTabsIndex,
                    showSellTradeButton: true,
                    setOpenSellTrade,
                    showAddTradeButton: true,
                    setOpenAddTrade,
                    showAddDividendButton: true,
                    setOpenAddDividend,
                    showAddCompanyButton: true,
                    setOpenEditCompany,
                })}
            />
        );

        fireEvent.click(screen.getByLabelText("open drawer"));
        expect(mockNavigate).toHaveBeenCalledWith("/");

        fireEvent.click(screen.getByRole("tab", {name: "Quarterly"}));
        expect(setStatsTabsIndex).toHaveBeenCalledWith(2);

        fireEvent.click(screen.getByRole("button", {name: "sell trade"}));
        fireEvent.click(screen.getByRole("button", {name: "add trade"}));
        fireEvent.click(screen.getByRole("button", {name: "add dividend"}));
        fireEvent.click(screen.getByRole("button", {name: "add company"}));

        expect(setOpenSellTrade).toHaveBeenCalledWith(true);
        expect(setOpenAddTrade).toHaveBeenCalledWith(true);
        expect(setOpenAddDividend).toHaveBeenCalledWith(true);
        expect(setOpenEditCompany).toHaveBeenCalledWith({});
    });
});
