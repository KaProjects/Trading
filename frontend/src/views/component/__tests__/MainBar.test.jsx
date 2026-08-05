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
        researchTabsIndex: 0,
        setResearchTabsIndex: jest.fn(),
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
        expect(screen.queryByRole("button", {name: "go to trades"})).not.toBeInTheDocument();
        expect(screen.getByRole("button", {name: "go to dividends"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "go to research"})).toBeInTheDocument();
    });

    test("hides currency and sector selectors when company is selected", () => {
        mockUseLocation.mockReturnValue({pathname: "/trades"});

        render(<MainBar {...createProps({
            companySelectorValue: {id: "company-1", ticker: "NVDA"},
            currencySelectorValue: "$",
            sectorSelectorValue: {key: "TECH", name: "Technology"},
        })} />);

        expect(screen.getByText("selector:all")).toBeInTheDocument();
        expect(screen.getByText("selector:companies")).toBeInTheDocument();
        expect(screen.getByText("selector:years")).toBeInTheDocument();
        expect(screen.queryByText("selector:currencies")).not.toBeInTheDocument();
        expect(screen.queryByText("selector:sectors")).not.toBeInTheDocument();
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

    test("handles research tab change on research route", () => {
        mockUseLocation.mockReturnValue({pathname: "/research", state: null});

        const setResearchTabsIndex = jest.fn();

        render(<MainBar {...createProps({
            companySelectorValue: {id: "company-1", ticker: "NVDA"},
            setResearchTabsIndex,
        })} />);

        expect(screen.getByRole("tab", {name: "Research"})).toBeInTheDocument();
        expect(screen.getByRole("tab", {name: "Records"})).toBeInTheDocument();

        fireEvent.click(screen.getByRole("tab", {name: "Records"}));

        expect(setResearchTabsIndex).toHaveBeenCalledWith(1);
    });

    test("hides research tabs until company is selected", () => {
        mockUseLocation.mockReturnValue({pathname: "/research", state: null});

        render(<MainBar {...createProps()} />);

        expect(screen.queryByRole("tab", {name: "Research"})).not.toBeInTheDocument();
        expect(screen.queryByRole("tab", {name: "Records"})).not.toBeInTheDocument();
    });

    test("restores selector values from navigation state", async () => {
        mockUseLocation.mockReturnValue({
            pathname: "/trades",
            state: {
                companyId: "company-1",
                tradeState: "only active",
                currency: "$",
                year: "2024",
                sector: "TECH",
            },
        });

        const company = {id: "company-1", ticker: "NVDA"};
        const sector = {key: "TECH", name: "Technology"};
        const setCompanySelectorValue = jest.fn();
        const setActiveSelectorValue = jest.fn();
        const setCurrencySelectorValue = jest.fn();
        const setYearSelectorValue = jest.fn();
        const setSectorSelectorValue = jest.fn();

        render(<MainBar {...createProps({
            companies: [company],
            sectors: [sector],
            setCompanySelectorValue,
            setActiveSelectorValue,
            setCurrencySelectorValue,
            setYearSelectorValue,
            setSectorSelectorValue,
        })} />);

        await waitFor(() => expect(setCompanySelectorValue).toHaveBeenCalledWith(company));
        expect(setActiveSelectorValue).toHaveBeenCalledWith("only active");
        expect(setCurrencySelectorValue).toHaveBeenCalledWith("$");
        expect(setYearSelectorValue).toHaveBeenCalledWith("2024");
        expect(setSectorSelectorValue).toHaveBeenCalledWith(sector);
        expect(mockNavigate).toHaveBeenCalledWith("/trades", {
            replace: true,
            state: {},
        });
    });

    test("clears the active trade filter when navigation state contains an empty value", async () => {
        mockUseLocation.mockReturnValue({
            pathname: "/trades",
            state: {companyId: "company-1", tradeState: ""},
        });

        const company = {id: "company-1", ticker: "NVDA"};
        const setActiveSelectorValue = jest.fn();

        render(<MainBar {...createProps({
            companies: [company],
            activeSelectorValue: "only active",
            setActiveSelectorValue,
        })} />);

        await waitFor(() => expect(setActiveSelectorValue).toHaveBeenCalledWith(""));
        expect(mockNavigate).toHaveBeenCalledWith("/trades", {
            replace: true,
            state: {},
        });
    });

    test("restores the research tab from navigation state", async () => {
        mockUseLocation.mockReturnValue({
            pathname: "/research",
            state: {companyId: "company-1", researchTab: 1},
        });

        const company = {id: "company-1", ticker: "NVDA"};
        const setResearchTabsIndex = jest.fn();

        render(<MainBar {...createProps({
            companies: [company],
            setResearchTabsIndex,
        })} />);

        await waitFor(() => expect(setResearchTabsIndex).toHaveBeenCalledWith(1));
        expect(mockNavigate).toHaveBeenCalledWith("/research", {
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

    test("renders route navigation buttons after selectors on data routes", () => {
        mockUseLocation.mockReturnValue({pathname: "/research", state: null});

        render(<MainBar {...createProps()} />);

        expect(screen.getByRole("button", {name: "go to trades"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "go to dividends"})).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "go to research"})).not.toBeInTheDocument();
    });

    test("renders research external links when company is selected on research route", () => {
        mockUseLocation.mockReturnValue({pathname: "/research", state: null});

        render(<MainBar {...createProps({
            companySelectorValue: {id: "company-1", ticker: "NVDA"},
        })} />);

        expect(screen.getByRole("link", {name: "TradingView financials"}))
            .toHaveAttribute("href", "https://www.tradingview.com/symbols/NASDAQ-NVDA/financials-income-statement/?statements-period=FQ");
        expect(screen.getByRole("link", {name: "MarketBeat ratings"}))
            .toHaveAttribute("href", "https://www.marketbeat.com/stocks/NASDAQ/NVDA/forecast/#ratings-table");
        expect(screen.getByRole("link", {name: "Zacks earnings estimates"}))
            .toHaveAttribute("href", "https://www.zacks.com/stock/quote/NVDA/detailed-earning-estimates#detailed_earnings_estimates");
    });

    test("does not render research external links without selected company", () => {
        mockUseLocation.mockReturnValue({pathname: "/research", state: null});

        render(<MainBar {...createProps()} />);

        expect(screen.queryByRole("link", {name: "TradingView financials"})).not.toBeInTheDocument();
        expect(screen.queryByRole("link", {name: "MarketBeat ratings"})).not.toBeInTheDocument();
        expect(screen.queryByRole("link", {name: "Zacks earnings estimates"})).not.toBeInTheDocument();
    });

    test("navigates between data routes with current selector state", () => {
        mockUseLocation.mockReturnValue({pathname: "/research", state: null});

        render(<MainBar {...createProps({
            companySelectorValue: {id: "company-1", ticker: "NVDA"},
            currencySelectorValue: "$",
            yearSelectorValue: "2024",
            sectorSelectorValue: {key: "TECH", name: "Technology"},
        })} />);

        fireEvent.click(screen.getByRole("button", {name: "go to trades"}));

        expect(mockNavigate).toHaveBeenCalledWith("/trades", {
            state: {
                companyId: "company-1",
                currency: "$",
                year: "2024",
                sector: "TECH",
            },
        });
    });
});
