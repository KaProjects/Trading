import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";

const mockNavigate = jest.fn();
const mockUseLocation = jest.fn();

jest.mock("../MainBarSelect", () => ({
    MainBarSelect: ({label}) => <div>selector:{label}</div>,
}));

jest.mock("react-router-dom", () => {
    const actual = jest.requireActual("react-router-dom");
    return {
        ...actual,
        useNavigate: () => mockNavigate,
        useLocation: () => mockUseLocation(),
    };
});

import {MainBar} from "../MainBar";

function createProps(overrides = {}) {
    return {
        loaded: false,
        statsTabsIndex: 0,
        setStatsTabsIndex: jest.fn(),
        setYears: jest.fn(),
        setOpenSellTrade: jest.fn(),
        setOpenAddTrade: jest.fn(),
        setOpenAddDividend: jest.fn(),
        setOpenEditCompany: jest.fn(),
        activeSelectorValue: "",
        setActiveSelectorValue: jest.fn(),
        companies: [{id: "company-1", ticker: "NVDA"}],
        companySelectorValue: "",
        setCompanySelectorValue: jest.fn(),
        currencies: ["$", "EUR"],
        currencySelectorValue: "",
        setCurrencySelectorValue: jest.fn(),
        years: ["2024", "2025"],
        yearSelectorValue: "",
        setYearSelectorValue: jest.fn(),
        sectors: [{name: "Technology"}],
        sectorSelectorValue: "",
        setSectorSelectorValue: jest.fn(),
        ...overrides,
    };
}

describe("MainBar", () => {
    beforeEach(() => {
        mockNavigate.mockReset();
        mockUseLocation.mockReturnValue({pathname: "/", state: null});
    });

    test("renders trade selectors and action buttons on trades route", () => {
        mockUseLocation.mockReturnValue({pathname: "/trades"});

        render(<MainBar {...createProps()} />);

        expect(screen.getByText("selector:all")).toBeInTheDocument();
        expect(screen.getByText("selector:companies")).toBeInTheDocument();
        expect(screen.getByText("selector:currencies")).toBeInTheDocument();
        expect(screen.getByText("selector:years")).toBeInTheDocument();
        expect(screen.getByText("selector:sectors")).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "sell trade"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "add trade"})).toBeInTheDocument();
    });

    test("renders stats tabs and company stats selectors on stats route", () => {
        mockUseLocation.mockReturnValue({pathname: "/stats"});

        render(
            <MainBar
                {...createProps({
                    statsTabsIndex: 0,
                })}
            />
        );

        expect(screen.getByRole("tab", {name: "Companies"})).toBeInTheDocument();
        expect(screen.getByRole("tab", {name: "Monthly"})).toBeInTheDocument();
        expect(screen.getByRole("tab", {name: "Quarterly"})).toBeInTheDocument();
        expect(screen.getByRole("tab", {name: "Yearly"})).toBeInTheDocument();
        expect(screen.getByText("selector:years")).toBeInTheDocument();
        expect(screen.getByText("selector:sectors")).toBeInTheDocument();
        expect(screen.queryByText("selector:companies")).not.toBeInTheDocument();
    });

    test("handles navigation, tab change and action buttons", () => {
        mockUseLocation.mockReturnValue({pathname: "/trades"});

        const setStatsTabsIndex = jest.fn();
        const setOpenSellTrade = jest.fn();
        const setOpenAddTrade = jest.fn();

        render(
            <MainBar
                {...createProps({
                    setStatsTabsIndex,
                    setOpenSellTrade,
                    setOpenAddTrade,
                })}
            />
        );

        fireEvent.click(screen.getByLabelText("open drawer"));
        expect(mockNavigate).toHaveBeenCalledWith("/");

        fireEvent.click(screen.getByRole("button", {name: "sell trade"}));
        fireEvent.click(screen.getByRole("button", {name: "add trade"}));

        expect(setOpenSellTrade).toHaveBeenCalledWith(true);
        expect(setOpenAddTrade).toHaveBeenCalledWith(true);
        expect(setStatsTabsIndex).not.toHaveBeenCalled();
    });

    test("handles stats tab change on stats route", () => {
        mockUseLocation.mockReturnValue({pathname: "/stats"});

        const setStatsTabsIndex = jest.fn();

        render(<MainBar {...createProps({setStatsTabsIndex})} />);

        fireEvent.click(screen.getByRole("tab", {name: "Quarterly"}));

        expect(setStatsTabsIndex).toHaveBeenCalledWith(2);
    });

    test("restores selector values from navigation state", async () => {
        mockUseLocation.mockReturnValue({
            pathname: "/trades",
            state: {
                companyId: "company-1",
                tradeState: "only active",
            },
        });

        const company = {id: "company-1", ticker: "NVDA"};
        const setCompanySelectorValue = jest.fn();
        const setActiveSelectorValue = jest.fn();

        render(<MainBar {...createProps({
            companies: [company],
            setCompanySelectorValue,
            setActiveSelectorValue,
        })} />);

        await waitFor(() => expect(setCompanySelectorValue).toHaveBeenCalledWith(company));
        expect(setActiveSelectorValue).toHaveBeenCalledWith("only active");
        expect(mockNavigate).toHaveBeenCalledWith("/trades", {
            replace: true,
            state: {},
        });
    });

    test("preserves unconsumed navigation state for target page", async () => {
        mockUseLocation.mockReturnValue({
            pathname: "/research",
            state: {
                companyId: "company-1",
                showFinancials: true,
            },
        });

        const company = {id: "company-1", ticker: "NVDA"};
        const setCompanySelectorValue = jest.fn();

        render(<MainBar {...createProps({
            companies: [company],
            setCompanySelectorValue,
        })} />);

        await waitFor(() => expect(setCompanySelectorValue).toHaveBeenCalledWith(company));
        expect(mockNavigate).toHaveBeenCalledWith("/research", {
            replace: true,
            state: {showFinancials: true},
        });
    });
});
