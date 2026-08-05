import {fireEvent, render, screen} from "@testing-library/react";
import {FinancialsDialog} from "../FinancialsDialog";

const financials = [{
    period: {year: "2025", type: "FY"},
    revenue: {value: 1500, margin: 100},
    grossProfit: {value: 600, margin: 40},
    operatingIncome: {value: 300, margin: 20},
    netIncome: {value: 150, margin: 10},
    dividend: 25,
}];

test("shows the company financial table and closes", () => {
    const handleClose = jest.fn();
    render(<FinancialsDialog open handleClose={handleClose} ticker="NVDA" financials={financials}/>);

    expect(screen.getByRole("heading", {name: "NVDA - Financials"})).toBeInTheDocument();
    expect(screen.getByText("25FY")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", {name: "Close"}));
    expect(handleClose).toHaveBeenCalled();
});
