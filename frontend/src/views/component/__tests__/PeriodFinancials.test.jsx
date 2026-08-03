import {fireEvent, render, screen} from "@testing-library/react";
import {FinancialsTable, PeriodFinancials} from "../PeriodFinancials";

const ttm = {
    revenue: {value: 1500, margin: 100},
    grossProfit: {value: 600, margin: 40},
    operatingIncome: {value: 300, margin: 20},
    netIncome: {value: 150, margin: 10},
};

const financials = [{
    period: {year: "2025", type: "FY"},
    revenue: {value: 1500, margin: 100, yoy: 25},
    grossProfit: {value: 600, margin: 40, yoy: 50, qoq: 20},
    operatingIncome: {value: 300, margin: 20, yoy: -10},
    netIncome: {value: 150, margin: 10, qoq: 0},
    dividend: 25,
}];

describe("PeriodFinancials", () => {
    test("renders only the compact summary and opens financials", () => {
        const onOpen = jest.fn();
        render(<PeriodFinancials ttm={ttm} onOpen={onOpen}/>);

        expect(screen.getByText("1.5B")).toBeInTheDocument();
        expect(screen.getByText("net income")).toBeInTheDocument();
        expect(screen.queryByText("Dividend")).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Open financials"}));
        expect(onOpen).toHaveBeenCalled();
    });

    test("renders the detailed financial table separately", () => {
        render(<FinancialsTable financials={financials} fontSize={16}/>);

        expect(screen.getByText("Period")).toBeInTheDocument();
        expect(screen.getByText("Revenue")).toBeInTheDocument();
        expect(screen.getByText("25FY")).toBeInTheDocument();
        expect(screen.getByText("+50% / +20%")).toBeInTheDocument();
    });

    test("shows a dash when a dividend is zero or absent", () => {
        render(<FinancialsTable financials={[
            {...financials[0], period: {year: "2025", type: "Q1"}, dividend: 0},
            {...financials[0], period: {year: "2024", type: "Q4"}, dividend: null},
        ]}/>);

        expect(screen.getAllByText("-")).toHaveLength(2);
    });
});
