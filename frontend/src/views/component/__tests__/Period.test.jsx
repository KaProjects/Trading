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
        const openEditDialog = jest.fn();
        const openEstimateDialog = jest.fn();
        const openTargetDialog = jest.fn();
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
                        adjustedEps: 1.62,
                        revenue: {value: 300},
                        grossProfit: {value: 200},
                        operatingIncome: {value: 100},
                        netIncome: {value: 50},
                    },
                    estimate: {
                        current: 1.62,
                        next1: 1.85,
                        next2: null,
                        next3: 2.76,
                    },
                    targetStats: {
                        count: 3,
                        minimum: 120,
                        average: 145.5,
                        maximum: 175,
                    },
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
                openEditDialog={openEditDialog}
                openEstimateDialog={openEstimateDialog}
                openTargetDialog={openTargetDialog}
                targetCandidateCount={3}
            />
        );

        expect(screen.getByText("25FY - ending: 12/25 - report: 15.02.2026")).toBeInTheDocument();
        expect(screen.getByText("Shares: 123M | H: 20$ | L: 10$ | Dividend: 12M | Adj. Eps: 1.62"))
            .toBeInTheDocument();
        expect(screen.getByText("Revenue: 300M | Gross P.: 200M | Op. Inc.: 100M | Net Income: 50M")).toBeInTheDocument();
        expect(screen.getByTestId("period-estimates"))
            .toHaveTextContent("Estimates: - | - | - | - => 1.62 | 1.85 | - | 2.76");
        expect(screen.getByTestId("period-target-summary"))
            .toHaveTextContent("Targets: 3@(175-120)~146$");
        expect(screen.queryByRole("button", {name: "Add Financials"})).not.toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Add Estimates"})).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Add Estimates"}));
        expect(openEstimateDialog).toHaveBeenCalledWith(expect.objectContaining({id: "period-1"}));
        fireEvent.click(screen.getByRole("button", {name: "Edit Period"}));
        expect(openEditDialog).toHaveBeenCalledWith(expect.objectContaining({id: "period-1"}));
        const manageTargets = screen.getByRole("button", {name: "Manage Targets"});
        expect(manageTargets).toHaveTextContent("3");
        expect(screen.getByText("3")).toHaveClass("MuiBadge-colorSuccess");
        fireEvent.click(manageTargets);
        expect(openTargetDialog).toHaveBeenCalledWith(expect.objectContaining({id: "period-1"}));
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

        expect(screen.getByTestId("period-estimates"))
            .toHaveTextContent("Estimates: - | - | - | - => 1.62 | - | - | -");
        expect(screen.getByRole("button", {name: "Add Financials"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Add Estimates"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Manage Targets"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Manage Targets"})).not.toHaveTextContent(/[1-9]/);
        expect(screen.queryByTestId("period-target-summary")).not.toBeInTheDocument();
    });

    test("shows an error badge instead of an import count when availability cannot be checked", () => {
        render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2026", type: "Q2"},
                    endingMonth: "2026-07",
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
                targetCandidateCount={4}
                targetCandidateFailed
            />
        );

        const manageTargets = screen.getByRole("button", {name: "Manage Targets"});
        expect(manageTargets).toHaveTextContent("!");
        expect(manageTargets).not.toHaveTextContent("4");
        expect(screen.getByText("!")).toHaveClass("MuiBadge-colorError");
    });

    test("renders past estimates before the current estimate", () => {
        render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2026", type: "Q2"},
                    endingMonth: "2026-07",
                    reportDate: null,
                    estimate: {
                        past4: 0.91,
                        past3: 1.05,
                        past2: null,
                        past1: 1.42,
                        current: 1.62,
                        next1: 1.85,
                        next2: null,
                        next3: 2.76,
                        datetime: "2026-08-02T12:30:00",
                        pastTotal: 4.38,
                        currentChange: 12.5,
                        next1Change: -3.25,
                        next2Change: 0,
                        next3Change: 4,
                    },
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.getByTestId("period-estimates"))
            .toHaveTextContent("Estimates: 0.91 | 1.05 | - | 1.42 => 1.62 | 1.85 | - | 2.76");
        expect(screen.getByText("(02.08.2026)")).toBeInTheDocument();
        expect(screen.getByText("(4.38)")).toBeInTheDocument();
        expect(screen.getByText("(+12.5% | -3.3% | 0% | +4%)")).toBeInTheDocument();
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
        expect(screen.queryByRole("button", {name: "Edit Period"})).not.toBeInTheDocument();
    });
});
