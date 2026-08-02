import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

jest.mock("axios");
jest.mock("../ContentEditor", () => ({
    ContentEditor: ({content, update}) => (
        <button onClick={() => update([{type: "paragraph", children: [{text: "Updated research"}]}])}>
            {content || "empty-content"}
        </button>
    )
}));

import {Period} from "../Period";

describe("Period", () => {
    beforeEach(() => {
        axios.put.mockReset();
        axios.put.mockResolvedValue({});
    });

    test("renders title and financial details", () => {
        render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2025", type: "FY"},
                    endingMonth: "2025-12",
                    reportDate: "2026-02-15",
                    research: "Stored research",
                    shares: 123,
                    priceHigh: 20,
                    priceLow: 10,
                    financial: {
                        dividend: 12,
                        revenue: {value: 300},
                        grossProfit: {value: 200},
                        operatingIncome: {value: 100},
                        netIncome: {value: 50},
                    },
                    estimate: {
                        ttm: 4.45,
                        current: 1.62,
                        next1: 1.85,
                        next2: null,
                        next3: 2.76,
                    },
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.getByText("25FY - ending: 12/25 - report: 15.02.2026")).toBeInTheDocument();
        expect(screen.getByText("Shares: 123M | H: 20$ | L: 10$ | Dividend: 12M")).toBeInTheDocument();
        expect(screen.getByText("Revenue: 300M | Gross P.: 200M | Op. Inc.: 100M | Net Income: 50M")).toBeInTheDocument();
        expect(screen.getByText("Estimates: 4.45 |> 1.62 | 1.85 | 2.76")).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Add Financials"})).not.toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Add Estimates"})).toBeInTheDocument();
    });

    test("updates research through axios", async () => {
        render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2025", type: "FY"},
                    endingMonth: "2025-12",
                    reportDate: "2026-02-15",
                    research: "Stored research",
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        fireEvent.click(screen.getByText("Stored research"));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith(
            expect.stringContaining("/period"),
            {
                id: "period-1",
                research: JSON.stringify([{type: "paragraph", children: [{text: "Updated research"}]}]),
            }
        ));
    });

    test("renders estimates when financials are missing", () => {
        render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2026", type: "Q2"},
                    endingMonth: "2026-07",
                    reportDate: null,
                    estimate: {
                        ttm: 4.45,
                        current: 1.62,
                        next1: null,
                        next2: null,
                        next3: null,
                    },
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.getByText("Estimates: 4.45 |> 1.62")).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Add Financials"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Add Estimates"})).toBeInTheDocument();
    });

    test("opens dialog when financials are missing", () => {
        const openDialog = jest.fn();

        render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2025", type: "FY"},
                    endingMonth: "2025-12",
                    reportDate: "2026-02-15",
                    research: "Stored research",
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={openDialog}
            />
        );

        fireEvent.click(screen.getByRole("button", {name: "Add Financials"}));

        expect(openDialog).toHaveBeenCalled();
        expect(screen.queryByText(/^Estimates:/)).not.toBeInTheDocument();
    });
});
