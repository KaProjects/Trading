import {fireEvent, render, screen} from "@testing-library/react";

import {PeriodFinancials} from "../PeriodFinancials";

function mockMatchMedia(maxWidth) {
    window.matchMedia = jest.fn().mockImplementation((query) => {
        const maxWidthMatch = query.match(/max-width:\s*(\d+)px/);
        const matches = maxWidthMatch ? maxWidth <= Number(maxWidthMatch[1]) : false;

        return {
            matches,
            media: query,
            onchange: null,
            addListener: jest.fn(),
            removeListener: jest.fn(),
            addEventListener: jest.fn(),
            removeEventListener: jest.fn(),
            dispatchEvent: jest.fn(),
        };
    });
}

describe("PeriodFinancials", () => {
    const ttm = {
        revenue: 1500,
        grossProfit: 600,
        grossMargin: 40,
        operatingIncome: 300,
        operatingMargin: 20,
        netIncome: 150,
        netMargin: 10,
    };

    const financials = [
        {
            period: {year: "2025", type: "FY"},
            revenue: 1500,
            grossProfit: 600,
            grossMargin: 40,
            operatingIncome: 300,
            operatingMargin: 20,
            netIncome: 150,
            netMargin: 10,
            dividend: 25,
        },
        {
            period: {year: "2024", type: "FY"},
            revenue: 1200,
            grossProfit: 420,
            grossMargin: 35,
            operatingIncome: 180,
            operatingMargin: 15,
            netIncome: 120,
            netMargin: 10,
            dividend: 20,
        },
    ];

    beforeEach(() => {
        mockMatchMedia(1600);
    });

    test("renders compact ttm financial summary", () => {
        render(
            <PeriodFinancials
                financials={financials}
                ttm={ttm}
                expand={false}
                setExpand={jest.fn()}
            />
        );

        expect(screen.getByText("1.5B")).toBeInTheDocument();
        expect(screen.getByText("revenue")).toBeInTheDocument();
        expect(screen.getByText("600M")).toBeInTheDocument();
        expect(screen.getByText("gross profit")).toBeInTheDocument();
        expect(screen.getByText("300M")).toBeInTheDocument();
        expect(screen.getByText("operating income")).toBeInTheDocument();
        expect(screen.getByText("150M")).toBeInTheDocument();
        expect(screen.getByText("net income")).toBeInTheDocument();
        expect(screen.getAllByText("(100%)")).toHaveLength(1);
        expect(screen.getAllByText("(40%)")).toHaveLength(1);
        expect(screen.getAllByText("(20%)")).toHaveLength(1);
        expect(screen.getAllByText("(10%)")).toHaveLength(1);
        expect(screen.queryByRole("button")).not.toBeInTheDocument();
        expect(screen.queryByText("Dividend")).not.toBeInTheDocument();
    });

    test("shows expand control on hover for wide screens and toggles expand state", () => {
        const setExpand = jest.fn();
        const {container} = render(
            <PeriodFinancials
                financials={financials}
                ttm={ttm}
                expand={false}
                setExpand={setExpand}
            />
        );

        fireEvent.mouseEnter(container.firstChild);
        fireEvent.click(screen.getByRole("button"));

        expect(setExpand).toHaveBeenCalledWith(true);
    });

    test("shows expand control without hover for screens narrower than 1600px", () => {
        mockMatchMedia(1400);

        render(
            <PeriodFinancials
                financials={financials}
                ttm={ttm}
                expand={false}
                setExpand={jest.fn()}
            />
        );

        expect(screen.getByRole("button")).toBeInTheDocument();
    });

    test("renders detailed financial table when expanded", () => {
        render(
            <PeriodFinancials
                financials={financials}
                ttm={ttm}
                expand={true}
                setExpand={jest.fn()}
            />
        );

        expect(screen.getByText("Period")).toBeInTheDocument();
        expect(screen.getByText("Revenue")).toBeInTheDocument();
        expect(screen.getByText("Gross Profit")).toBeInTheDocument();
        expect(screen.getByText("Operating Income")).toBeInTheDocument();
        expect(screen.getByText("Net Income")).toBeInTheDocument();
        expect(screen.getByText("Dividend")).toBeInTheDocument();
        expect(screen.getByText("25FY")).toBeInTheDocument();
        expect(screen.getByText("24FY")).toBeInTheDocument();
        expect(screen.getAllByText("1.5B")).toHaveLength(2);
        expect(screen.getAllByText("25M")).toHaveLength(1);
        expect(screen.getAllByText("20M")).toHaveLength(1);
        expect(screen.getAllByText("(35%)")).toHaveLength(1);
        expect(screen.getAllByText("(15%)")).toHaveLength(1);
    });
});
