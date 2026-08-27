import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";

const mockNavigate = jest.fn();
const mockUseLocation = jest.fn();
const mockUseMediaQuery = jest.fn(() => false);

jest.mock("@mui/material/useMediaQuery", () => (...args) => mockUseMediaQuery(...args));

jest.mock("../MainBarSelect", () => ({
    MainBarSelect: ({
        label,
        companyLists,
        defaultCompanyList,
        companyListValue,
        setCompanyListValue,
        values,
        value,
        setValue,
    }) => (
        <div>
            <button
                data-company-lists={companyLists ? Object.keys(companyLists).join(",") : ""}
                data-default-company-list={defaultCompanyList ?? ""}
                onClick={() => setValue(values?.[0] ?? "")}
            >
                <span>selector:{label}</span>
                <span data-testid={`selector-value-${label}`}>{value?.ticker ?? value ?? ""}</span>
            </button>
            {companyLists && <>
                <span data-testid="company-list-value">{companyListValue}</span>
                <button onClick={() => setCompanyListValue("researched")}>select-list:researched</button>
                {companyLists.owned?.[0] && <button onClick={() => {
                    setCompanyListValue("owned");
                    setValue(companyLists.owned[0]);
                }}>select-company-from-list:owned</button>}
            </>}
        </div>
    ),
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
        companyLists: {all: [{id: "company-1", ticker: "NVDA"}]},
        companySelectorValue: "",
        setCompanySelectorValue: jest.fn(),
        companyListSelectorValue: "all",
        setCompanyListSelectorValue: jest.fn(),
        currencies: ["$", "EUR"],
        currencySelectorValue: "",
        setCurrencySelectorValue: jest.fn(),
        years: ["2024", "2025"],
        yearSelectorValue: "",
        setYearSelectorValue: jest.fn(),
        sectors: [{name: "Technology"}],
        sectorSelectorValue: "",
        setSectorSelectorValue: jest.fn(),
        exchanges: [
            {key: "XNAS", name: "Nasdaq", tradingViewCode: "NASDAQ", marketBeatCode: "NASDAQ", zacksSupported: true},
            {key: "XPAR", name: "Euronext Paris", tradingViewCode: "EURONEXT", marketBeatCode: "EPA", zacksSupported: false},
            {key: "XSWX", name: "SIX Swiss Exchange", tradingViewCode: "SIX", marketBeatCode: null, zacksSupported: false},
        ],
        portfolios: [{key: "PATRIA_STANDARD", name: "Patria - Standard"}],
        portfolioSelectorValue: "",
        setPortfolioSelectorValue: jest.fn(),
        ...overrides,
    };
}

describe("MainBar", () => {
    beforeEach(() => {
        mockNavigate.mockReset();
        mockUseLocation.mockReturnValue({pathname: "/", state: null});
        mockUseMediaQuery.mockReset();
        mockUseMediaQuery.mockReturnValue(false);
    });

    test("renders trade selectors and action buttons on trades route", () => {
        mockUseLocation.mockReturnValue({pathname: "/trades"});

        render(<MainBar {...createProps()} />);

        expect(screen.getByText("selector:all")).toBeInTheDocument();
        expect(screen.getByText("selector:companies")).toBeInTheDocument();
        expect(screen.getByText("selector:companies").closest("button")).toHaveAttribute("data-company-lists", "all");
        expect(screen.getByText("selector:companies").closest("button")).toHaveAttribute("data-default-company-list", "all");
        expect(screen.getByText("selector:currencies")).toBeInTheDocument();
        expect(screen.getByText("selector:years")).toBeInTheDocument();
        expect(screen.getByText("selector:sectors")).toBeInTheDocument();
        expect(screen.getByText("selector:portfolios")).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "sell trade"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "add trade"})).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "go to trades"})).not.toBeInTheDocument();
        expect(screen.getByRole("button", {name: "go to dividends"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "go to research"})).toBeInTheDocument();

        const pageControls = screen.getByRole("group", {name: "page controls"});
        expect(pageControls).toContainElement(screen.getByRole("button", {name: "sell trade"}));
        expect(pageControls).toContainElement(screen.getByRole("button", {name: "add trade"}));
        expect(pageControls).toContainElement(screen.getByText("selector:all").closest("button"));
        expect(pageControls).toContainElement(screen.getByRole("button", {name: "go to dividends"}));
    });

    test("renders dividend action, selectors and redirects in shared page controls", () => {
        mockUseLocation.mockReturnValue({pathname: "/dividends"});

        render(<MainBar {...createProps()} />);

        const pageControls = screen.getByRole("group", {name: "page controls"});
        expect(pageControls).toContainElement(screen.getByRole("button", {name: "add dividend"}));
        expect(pageControls).toContainElement(screen.getByText("selector:companies").closest("button"));
        expect(pageControls).toContainElement(screen.getByRole("button", {name: "go to trades"}));
        expect(pageControls).toContainElement(screen.getByRole("button", {name: "go to research"}));
    });

    test("renders company action and selectors in shared page controls", () => {
        mockUseLocation.mockReturnValue({pathname: "/companies"});

        render(<MainBar {...createProps()} />);

        const pageControls = screen.getByRole("group", {name: "page controls"});
        expect(pageControls).toContainElement(screen.getByRole("button", {name: "add company"}));
        expect(pageControls).toContainElement(screen.getByText("selector:currencies").closest("button"));
        expect(pageControls).toContainElement(screen.getByText("selector:sectors").closest("button"));
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
        expect(screen.getByText("selector:portfolios")).toBeInTheDocument();
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
        expect(screen.getByRole("tab", {name: "P/L"})).toBeInTheDocument();
        expect(screen.getByText("selector:years")).toBeInTheDocument();
        expect(screen.getByText("selector:sectors")).toBeInTheDocument();
        expect(screen.queryByText("selector:companies")).not.toBeInTheDocument();
    });

    test("renders P/L filters on stats route", () => {
        mockUseLocation.mockReturnValue({pathname: "/stats"});

        render(<MainBar {...createProps({statsTabsIndex: 4})}/>);

        expect(screen.getByText("selector:companies")).toBeInTheDocument();
        expect(screen.getByText("selector:currencies")).toBeInTheDocument();
        expect(screen.getByText("selector:sectors")).toBeInTheDocument();
        expect(screen.getByText("selector:portfolios")).toBeInTheDocument();
        expect(screen.queryByText("selector:years")).not.toBeInTheDocument();
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

        expect(screen.getByRole("tab", {name: "Research", hidden: true})).toBeInTheDocument();
        expect(screen.getByRole("tab", {name: "Records", hidden: true})).toBeInTheDocument();

        fireEvent.click(screen.getByRole("tab", {name: "Records", hidden: true}));

        expect(setResearchTabsIndex).toHaveBeenCalledWith(1);
    });

    test("hides research tabs until company is selected", () => {
        mockUseLocation.mockReturnValue({pathname: "/research", state: null});

        render(<MainBar {...createProps()} />);

        expect(screen.queryByRole("tab", {name: "Research"})).not.toBeInTheDocument();
        expect(screen.queryByRole("tab", {name: "Records"})).not.toBeInTheDocument();
    });

    test("renders the Todo tab on a narrow Research view without a selected company", () => {
        mockUseMediaQuery.mockReturnValue(true);
        mockUseLocation.mockReturnValue({pathname: "/research", state: null});
        const setResearchTabsIndex = jest.fn();

        render(<MainBar {...createProps({setResearchTabsIndex})}/>);

        expect(screen.getByRole("tab", {name: "Research", hidden: true})).toBeInTheDocument();
        expect(screen.getByRole("tab", {name: "Records", hidden: true})).toBeInTheDocument();
        fireEvent.click(screen.getByRole("tab", {name: "Todo", hidden: true}));
        expect(setResearchTabsIndex).toHaveBeenCalledWith(2);
    });

    test("returns to the Research tab when the Todo tab is no longer available", async () => {
        mockUseLocation.mockReturnValue({pathname: "/research", state: null});
        const setResearchTabsIndex = jest.fn();

        render(<MainBar {...createProps({
            companySelectorValue: {id: "company-1", ticker: "NVDA"},
            researchTabsIndex: 2,
            setResearchTabsIndex,
        })}/>);

        await waitFor(() => expect(setResearchTabsIndex).toHaveBeenCalledWith(0));
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
            companyLists: {all: [company]},
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
        expect(mockNavigate).toHaveBeenCalledWith({
            pathname: "/trades",
            search: "?company=NVDA",
            hash: undefined,
        }, {
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
            companyLists: {all: [company]},
            activeSelectorValue: "only active",
            setActiveSelectorValue,
        })} />);

        await waitFor(() => expect(setActiveSelectorValue).toHaveBeenCalledWith(""));
        expect(mockNavigate).toHaveBeenCalledWith({
            pathname: "/trades",
            search: "?company=NVDA",
            hash: undefined,
        }, {
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
            companyLists: {all: [company]},
            setResearchTabsIndex,
        })} />);

        await waitFor(() => expect(setResearchTabsIndex).toHaveBeenCalledWith(1));
        expect(mockNavigate).toHaveBeenCalledWith({
            pathname: "/research",
            search: "?company=NVDA",
            hash: undefined,
        }, {
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
            companyLists: {all: [company]},
            setCompanySelectorValue,
        })} />);

        await waitFor(() => expect(setCompanySelectorValue).toHaveBeenCalledWith(company));
        expect(mockNavigate).toHaveBeenCalledWith({
            pathname: "/research",
            search: "?company=NVDA",
            hash: undefined,
        }, {
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
            companySelectorValue: {
                id: "company-1",
                ticker: "NVDA",
                exchange: "XNAS",
            },
        })} />);

        expect(screen.getByRole("link", {name: "TradingView financials"}))
            .toHaveAttribute("href", "https://www.tradingview.com/symbols/NASDAQ-NVDA/financials-income-statement/?statements-period=FQ");
        expect(screen.getByRole("link", {name: "MarketBeat ratings"}))
            .toHaveAttribute("href", "https://www.marketbeat.com/stocks/NASDAQ/NVDA/forecast/#ratings-table");
        expect(screen.getByRole("link", {name: "Zacks earnings estimates"}))
            .toHaveAttribute("href", "https://www.zacks.com/stock/quote/NVDA/detailed-estimates");
    });

    test("uses provider-specific exchange codes for European links", () => {
        mockUseLocation.mockReturnValue({pathname: "/research", state: null});

        render(<MainBar {...createProps({
            companySelectorValue: {
                id: "company-2",
                ticker: "LVMH",
                exchange: "XPAR",
            },
        })}/>);

        expect(screen.getByRole("link", {name: "TradingView financials"}))
            .toHaveAttribute("href", "https://www.tradingview.com/symbols/EURONEXT-LVMH/financials-income-statement/?statements-period=FQ");
        expect(screen.getByRole("link", {name: "MarketBeat ratings"}))
            .toHaveAttribute("href", "https://www.marketbeat.com/stocks/EPA/LVMH/forecast/#ratings-table");
        expect(screen.queryByRole("link", {name: "Zacks earnings estimates"})).not.toBeInTheDocument();
    });

    test("omits providers without support for the selected exchange", () => {
        mockUseLocation.mockReturnValue({pathname: "/research", state: null});

        render(<MainBar {...createProps({
            companySelectorValue: {
                id: "company-3",
                ticker: "NSN",
                exchange: "XSWX",
            },
        })}/>);

        expect(screen.getByRole("link", {name: "TradingView financials"}))
            .toHaveAttribute("href", "https://www.tradingview.com/symbols/SIX-NSN/financials-income-statement/?statements-period=FQ");
        expect(screen.queryByRole("link", {name: "MarketBeat ratings"})).not.toBeInTheDocument();
        expect(screen.queryByRole("link", {name: "Zacks earnings estimates"})).not.toBeInTheDocument();
    });

    test("does not guess an exchange for a selected company without one configured", () => {
        mockUseLocation.mockReturnValue({pathname: "/research", state: null});

        render(<MainBar {...createProps({
            companySelectorValue: {id: "company-1", ticker: "NVDA"},
        })}/>);

        expect(screen.queryByRole("link", {name: "TradingView financials"})).not.toBeInTheDocument();
        expect(screen.queryByRole("link", {name: "MarketBeat ratings"})).not.toBeInTheDocument();
        expect(screen.queryByRole("link", {name: "Zacks earnings estimates"})).not.toBeInTheDocument();
    });

    test("does not render research external links without selected company", () => {
        mockUseLocation.mockReturnValue({pathname: "/research", state: null});

        render(<MainBar {...createProps()} />);

        expect(screen.queryByRole("link", {name: "TradingView financials"})).not.toBeInTheDocument();
        expect(screen.queryByRole("link", {name: "MarketBeat ratings"})).not.toBeInTheDocument();
        expect(screen.queryByRole("link", {name: "Zacks earnings estimates"})).not.toBeInTheDocument();
    });

    test("navigates between data routes with current selector state", () => {
        mockUseLocation.mockReturnValue({
            pathname: "/research",
            search: "?company=NVDA&list=owned",
            state: null,
        });

        render(<MainBar {...createProps({
            companyLists: {
                all: [{id: "company-1", ticker: "NVDA"}],
                owned: [{id: "company-1", ticker: "NVDA"}],
            },
            companySelectorValue: {id: "company-1", ticker: "NVDA"},
            companyListSelectorValue: "owned",
            currencySelectorValue: "$",
            yearSelectorValue: "2024",
            sectorSelectorValue: {key: "TECH", name: "Technology"},
        })} />);

        fireEvent.click(screen.getByRole("button", {name: "go to trades"}));

        expect(mockNavigate).toHaveBeenCalledWith({
            pathname: "/trades",
            search: "?company=NVDA&list=owned",
        }, {
            state: {
                currency: "$",
                year: "2024",
                sector: "TECH",
            },
        });
    });

    test("preserves the Research list from the URL when navigating back from another data route", () => {
        mockUseLocation.mockReturnValue({
            pathname: "/trades",
            search: "?company=NVDA&list=owned",
            state: null,
        });

        render(<MainBar {...createProps({
            companySelectorValue: {id: "company-1", ticker: "NVDA"},
            companyListSelectorValue: "all",
        })}/>);

        fireEvent.click(screen.getByRole("button", {name: "go to research"}));

        expect(mockNavigate).toHaveBeenCalledWith({
            pathname: "/research",
            search: "?company=NVDA&list=owned",
        }, {
            state: {
                currency: "",
                year: "",
                sector: undefined,
            },
        });
    });

    test("selects company from the URL query parameter", async () => {
        const company = {id: "company-1", ticker: "NVDA"};
        const setCompanySelectorValue = jest.fn();
        mockUseLocation.mockReturnValue({
            pathname: "/research",
            search: "?company=NVDA",
            state: null,
        });

        render(<MainBar {...createProps({
            companyLists: {all: [company]},
            setCompanySelectorValue,
        })}/>);

        await waitFor(() => expect(setCompanySelectorValue).toHaveBeenCalledWith(company));
    });

    test("updates selected company when browser history changes the query parameter", async () => {
        const nvidia = {id: "company-1", ticker: "NVDA"};
        const amd = {id: "company-2", ticker: "AMD"};
        mockUseLocation.mockReturnValue({
            pathname: "/trades",
            search: "?company=NVDA",
            state: null,
        });

        const Harness = () => {
            const [company, setCompany] = React.useState("");
            return <MainBar {...createProps({
                companyLists: {all: [nvidia, amd]},
                companySelectorValue: company,
                setCompanySelectorValue: setCompany,
            })}/>;
        };

        const {rerender} = render(<Harness/>);
        await waitFor(() => expect(screen.getByTestId("selector-value-companies")).toHaveTextContent("NVDA"));

        mockUseLocation.mockReturnValue({
            pathname: "/trades",
            search: "?company=AMD",
            state: null,
        });
        rerender(<Harness/>);

        await waitFor(() => expect(screen.getByTestId("selector-value-companies")).toHaveTextContent("AMD"));
    });

    test("adds selected company ticker to the URL while preserving other query parameters", async () => {
        const company = {id: "company-1", ticker: "NVDA"};
        const companyLists = {all: [company]};
        mockUseLocation.mockReturnValue({
            pathname: "/dividends",
            search: "?source=companies",
            state: {preserved: true},
        });

        const Harness = () => {
            const [selectedCompany, setSelectedCompany] = React.useState("");
            return <MainBar {...createProps({
                companyLists,
                companySelectorValue: selectedCompany,
                setCompanySelectorValue: setSelectedCompany,
            })}/>;
        };

        render(<Harness/>);
        fireEvent.click(screen.getByText("selector:companies"));

        await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith({
            pathname: "/dividends",
            search: "?source=companies&company=NVDA",
            hash: undefined,
        }, {
            state: {preserved: true},
        }));
    });

    test("restores the Research company list from the URL query parameter", async () => {
        const company = {id: "company-1", ticker: "NVDA"};
        const setCompanyListSelectorValue = jest.fn();
        mockUseLocation.mockReturnValue({
            pathname: "/research",
            search: "?company=NVDA&list=owned",
            state: null,
        });

        render(<MainBar {...createProps({
            companyLists: {all: [company], owned: [company]},
            companySelectorValue: company,
            setCompanyListSelectorValue,
        })}/>);

        await waitFor(() => expect(setCompanyListSelectorValue).toHaveBeenCalledWith("owned"));
    });

    test("updates the Research company list when browser history changes the query parameter", async () => {
        const company = {id: "company-1", ticker: "NVDA"};
        const companyLists = {all: [company], owned: [company], recent: [company]};
        mockUseLocation.mockReturnValue({
            pathname: "/research",
            search: "?company=NVDA&list=owned",
            state: null,
        });

        const Harness = () => {
            const [companyList, setCompanyList] = React.useState("all");
            return <MainBar {...createProps({
                companyLists,
                companySelectorValue: company,
                companyListSelectorValue: companyList,
                setCompanyListSelectorValue: setCompanyList,
            })}/>;
        };

        const {rerender} = render(<Harness/>);
        await waitFor(() => expect(screen.getByTestId("company-list-value")).toHaveTextContent("owned"));

        mockUseLocation.mockReturnValue({
            pathname: "/research",
            search: "?company=NVDA&list=recent",
            state: null,
        });
        rerender(<Harness/>);

        await waitFor(() => expect(screen.getByTestId("company-list-value")).toHaveTextContent("recent"));
    });

    test("adds the selected Research company list to the URL while preserving other query parameters", async () => {
        const company = {id: "company-1", ticker: "NVDA"};
        const companyLists = {all: [company], researched: [company]};
        mockUseLocation.mockReturnValue({
            pathname: "/research",
            search: "?company=NVDA&source=companies",
            state: {preserved: true},
        });

        const Harness = () => {
            const [companyList, setCompanyList] = React.useState("all");
            return <MainBar {...createProps({
                companyLists,
                companySelectorValue: company,
                companyListSelectorValue: companyList,
                setCompanyListSelectorValue: setCompanyList,
            })}/>;
        };

        render(<Harness/>);
        fireEvent.click(screen.getByText("select-list:researched"));

        await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith({
            pathname: "/research",
            search: "?company=NVDA&source=companies&list=researched",
            hash: undefined,
        }, {
            state: {preserved: true},
        }));
    });

    test("selects a Research company and its list with one click", async () => {
        const company = {id: "company-1", ticker: "NVDA"};
        const companyLists = {all: [company], owned: [company]};
        mockUseLocation.mockReturnValue({
            pathname: "/research",
            search: "",
            state: null,
        });

        const Harness = () => {
            const [selectedCompany, setSelectedCompany] = React.useState("");
            const [companyList, setCompanyList] = React.useState("all");
            return <MainBar {...createProps({
                companyLists,
                companySelectorValue: selectedCompany,
                setCompanySelectorValue: setSelectedCompany,
                companyListSelectorValue: companyList,
                setCompanyListSelectorValue: setCompanyList,
            })}/>;
        };

        render(<Harness/>);
        fireEvent.click(screen.getByText("select-company-from-list:owned"));

        await waitFor(() => expect(mockNavigate).toHaveBeenCalled());
        expect(mockNavigate.mock.calls.every(([target]) => (
            target.search === "?company=NVDA&list=owned"
        ))).toBe(true);
        expect(screen.getByTestId("selector-value-companies")).toHaveTextContent("NVDA");
        expect(screen.getByTestId("company-list-value")).toHaveTextContent("owned");
    });

    test("removes an unknown Research company list from the URL and selects all companies", async () => {
        const company = {id: "company-1", ticker: "NVDA"};
        const setCompanyListSelectorValue = jest.fn();
        mockUseLocation.mockReturnValue({
            pathname: "/research",
            search: "?company=NVDA&list=deleted-list&source=bookmark",
            state: {preserved: true},
        });

        render(<MainBar {...createProps({
            companyLists: {all: [company], owned: [company]},
            companySelectorValue: company,
            companyListSelectorValue: "owned",
            setCompanyListSelectorValue,
        })}/>);

        await waitFor(() => expect(setCompanyListSelectorValue).toHaveBeenCalledWith("all"));
        expect(mockNavigate).toHaveBeenCalledWith({
            pathname: "/research",
            search: "?company=NVDA&source=bookmark",
            hash: undefined,
        }, {
            replace: true,
            state: {preserved: true},
        });
    });

    test("does not synchronize a company list query parameter outside Research", () => {
        const company = {id: "company-1", ticker: "NVDA"};
        const companyLists = {all: [company], researched: [company]};
        mockUseLocation.mockReturnValue({
            pathname: "/trades",
            search: "?company=NVDA",
            state: null,
        });

        const Harness = () => {
            const [companyList, setCompanyList] = React.useState("all");
            return <MainBar {...createProps({
                companyLists,
                companySelectorValue: company,
                companyListSelectorValue: companyList,
                setCompanyListSelectorValue: setCompanyList,
            })}/>;
        };

        render(<Harness/>);
        fireEvent.click(screen.getByText("select-list:researched"));

        expect(mockNavigate).not.toHaveBeenCalled();
    });
});
