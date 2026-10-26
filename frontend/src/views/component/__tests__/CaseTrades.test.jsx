import {fireEvent, render, screen} from "@testing-library/react";
import React from "react";

import {CaseTrades} from "../CaseTrades";

const trade = (id, fields) => ({
    id: id,
    ticker: "NVDA",
    currency: "$",
    active: false,
    purchaseDate: "2026-01-05",
    purchasePrice: 100,
    purchaseTotal: 500,
    quantity: 5,
    sellDate: "2026-02-05",
    sellPrice: 120,
    profit: 50,
    profitPercentage: 10,
    ...fields,
});

/** Six trades: more than the handful that is read as it stands. */
const many = [
    trade(1, {ticker: "NVDA", active: true, quantity: 10, purchasePrice: 100, profit: null}),
    trade(2, {ticker: "NVDA", active: true, quantity: 30, purchasePrice: 120, profit: null}),
    trade(3, {ticker: "NVDA", profit: 50, purchaseTotal: 500, profitPercentage: 10}),
    trade(4, {ticker: "NVDA", profit: -20, purchaseTotal: 300, profitPercentage: -6.7}),
    trade(5, {ticker: "AMD", active: true, quantity: 7, purchasePrice: 90, profit: null}),
    trade(6, {ticker: "AMD", profit: 40, purchaseTotal: 200, profitPercentage: 20}),
];

describe("CaseTrades", () => {
    test("reads a case over one company as it was traded", () => {
        render(<CaseTrades trades={[trade(1), trade(2, {purchaseDate: "2026-03-01"})]}/>);

        expect(screen.getByTestId("case-trades")).toBeInTheDocument();
        expect(screen.queryByTestId("case-trades-summary")).not.toBeInTheDocument();
        // One company sums up to the one line the trades already are: nothing to fold away.
        expect(screen.queryByRole("button")).not.toBeInTheDocument();
    });

    test("sums the case a company at a time, however few trades there are", () => {
        render(<CaseTrades trades={many}/>);

        expect(screen.getByTestId("case-trades-summary")).toBeInTheDocument();
        expect(screen.queryByTestId("case-trades")).not.toBeInTheDocument();

        // What is still held, what it cost on average, and what the closed part has made.
        const nvda = screen.getByTestId("case-position-NVDA");
        expect(nvda).toHaveTextContent("40");
        // (10 x 100 + 30 x 120) / 40
        expect(nvda).toHaveTextContent("115.00$");
        expect(nvda).toHaveTextContent("+30.00$");
        // 30 made on 800 spent
        expect(nvda).toHaveTextContent("+3.8%");

        const amd = screen.getByTestId("case-position-AMD");
        expect(amd).toHaveTextContent("7");
        expect(amd).toHaveTextContent("90.00$");
        expect(amd).toHaveTextContent("+40.00$");
        expect(amd).toHaveTextContent("+20%");

        // The totals stay under the summary, as under the trades themselves.
        expect(screen.getByTestId("case-profit-$")).toHaveTextContent("+70.00$");
    });

    test("leaves a company that is no longer held without a quantity or a price", () => {
        render(<CaseTrades trades={[...many, trade(7, {ticker: "INTC", profit: 15,
            purchaseTotal: 100, profitPercentage: 15})]}/>);

        const intc = screen.getByTestId("case-position-INTC");
        expect(intc).toHaveTextContent("+15.00$");
        const cells = [...intc.querySelectorAll("td")].map(cell => cell.textContent);
        expect(cells[1]).toBe("");
        expect(cells[2]).toBe("");
    });

    test("opens every trade when asked, and goes back to the companies", () => {
        render(<CaseTrades trades={many}/>);

        fireEvent.click(screen.getByRole("button", {name: "Show every trade"}));

        expect(screen.getByTestId("case-trades")).toBeInTheDocument();
        expect(screen.queryByTestId("case-trades-summary")).not.toBeInTheDocument();
        expect(screen.getByTestId("case-trades").querySelectorAll("tbody tr")).toHaveLength(6);

        fireEvent.click(screen.getByRole("button", {name: "Show the companies only"}));

        expect(screen.getByTestId("case-trades-summary")).toBeInTheDocument();
    });

    test("signs what a trade made, the way it signs how much", () => {
        render(<CaseTrades trades={[
            trade(1, {profit: 50, profitPercentage: 10}),
            trade(2, {profit: -20, profitPercentage: -6.7, purchaseDate: "2026-03-01"}),
        ]}/>);

        const rows = screen.getByTestId("case-trades").querySelectorAll("tbody tr");
        expect(rows[0]).toHaveTextContent("+50.00$");
        expect(rows[0]).toHaveTextContent("+10%");
        expect(rows[1]).toHaveTextContent("-20.00$");
        expect(rows[1]).toHaveTextContent("-6.7%");
        expect(screen.getByTestId("case-profit-$")).toHaveTextContent("+30.00$");
    });

    test("keeps what the case made apart by currency", () => {
        render(<CaseTrades trades={[
            trade(1, {profit: 50}),
            trade(2, {ticker: "ASML", currency: "€", profit: 20}),
        ]}/>);

        // Two companies, so it opens on the summary; the totals read the same either way.
        expect(screen.getByTestId("case-profit-$")).toHaveTextContent("+50.00$");
        expect(screen.getByTestId("case-profit-€")).toHaveTextContent("+20.00€");
    });

    test("sums up a pair of trades in one company without folding them away", () => {
        render(<CaseTrades trades={[trade(1), trade(2, {purchaseDate: "2026-03-01"})]}/>);

        expect(screen.getByTestId("case-trades").querySelectorAll("tbody tr")).toHaveLength(2);
    });

    test("offers nothing at all when the case has no trades", () => {
        const {container} = render(<CaseTrades trades={[]}/>);

        expect(container).toBeEmptyDOMElement();
    });
});
