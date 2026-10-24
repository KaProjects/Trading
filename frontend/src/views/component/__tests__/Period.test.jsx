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
        const openNewsSentimentDialog = jest.fn();
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
                        capex: {value: 40},
                        freeCashFlow: {value: 70},
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
                openNewsSentimentDialog={openNewsSentimentDialog}
                targetCandidateCount={3}
            />
        );

        expect(screen.getByText("25FY - ending: 12/25 - reported: 15.02.2026")).toBeInTheDocument();
        const summary = screen.getByTestId("period-financial-summary");
        // Income metrics as a matrix: label, value, per-share figure, margin, then the changes.
        expect(summary).toHaveTextContent("Revenue300M(2.44)");
        expect(summary).toHaveTextContent("Gross p.200M(1.63)");
        expect(summary).toHaveTextContent("Op. income100M(0.81)");
        expect(summary).toHaveTextContent("Net income50M(0.41)");
        // The price range and the cash-flow figures sit beside that matrix, not inside it.
        expect(summary).toHaveTextContent("High20.00$");
        expect(summary).toHaveTextContent("Low10.00$");
        expect(summary).toHaveTextContent("CapEx40M");
        expect(summary).toHaveTextContent("FCF70M");
        expect(summary).toHaveTextContent("Dividend12M");
        expect(summary).toHaveTextContent("Adj. EPS1.62");
        expect(screen.getByTestId("period-estimates"))
            .toHaveTextContent("Estimates: - | - | - | - => 1.62 | 1.85 | - | 2.76");
        expect(screen.getByTestId("period-target-summary"))
            .toHaveTextContent("Targets: 3@(175-120)~146$");
        expect(screen.queryByRole("button", {name: "Add Financials"})).not.toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Add EPS Estimates"})).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Add EPS Estimates"}));
        expect(openEstimateDialog).toHaveBeenCalledWith(expect.objectContaining({id: "period-1"}));
        fireEvent.click(screen.getByRole("button", {name: "Edit Period"}));
        expect(openEditDialog).toHaveBeenCalledWith(expect.objectContaining({id: "period-1"}));
        const manageTargets = screen.getByRole("button", {name: "Manage Targets"});
        expect(manageTargets).toHaveTextContent("3");
        expect(screen.getByText("3")).toHaveClass("MuiBadge-colorSuccess");
        fireEvent.click(manageTargets);
        expect(openTargetDialog).toHaveBeenCalledWith(expect.objectContaining({id: "period-1"}));
        fireEvent.click(screen.getByRole("button", {name: "View News Sentiment"}));
        expect(openNewsSentimentDialog).toHaveBeenCalledWith(expect.objectContaining({id: "period-1"}));
    });

    function renderPeriod(period, props = {}) {
        return render(
            <Period
                period={{id: "period-1", name: {year: "2025", type: "FY"}, endingMonth: "2025-12", ...period}}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
                openEditDialog={jest.fn()}
                targetCandidateCount={0}
                {...props}
            />
        );
    }

    test("prints a lone change without a dangling separator", () => {
        render(
            <Period
                period={{
                    id: "period-2",
                    name: {year: "2025", type: "Q1"},
                    endingMonth: "2025-03",
                    reportDate: "2025-05-01",
                    shares: 100,
                    financial: {
                        revenue: {value: 100, qoq: 5},
                        grossProfit: {value: 60, margin: 60, yoy: 12},
                        operatingIncome: {value: 30, margin: 30, qoq: 4, yoy: 9},
                        netIncome: {value: 20, margin: 20},
                    },
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
                openEditDialog={jest.fn()}
                openEstimateDialog={jest.fn()}
                openTargetDialog={jest.fn()}
                openNewsSentimentDialog={jest.fn()}
            />
        );

        const summary = screen.getByTestId("period-financial-summary");
        expect(summary).toHaveTextContent("+5%");
        expect(summary).toHaveTextContent("+12%");
        expect(summary).toHaveTextContent("+4%/+9%");
        // Only the row that has both carries the separator.
        expect(summary.textContent.match(/\//g)).toHaveLength(1);
    });

    test("prints a dash for a dividend that was not paid", () => {
        renderPeriod({
            reportDate: "2025-05-01",
            shares: 100,
            financial: {
                revenue: {value: 100},
                dividend: 0,
                dividendMargin: 0,
            },
        });

        const summary = screen.getByTestId("period-financial-summary");
        expect(summary).toHaveTextContent("Dividend\u2013");
        expect(summary).not.toHaveTextContent("(0%)");
        expect(summary).not.toHaveTextContent("Dividend0M");
    });

    test("keeps a dividend that was actually paid", () => {
        renderPeriod({
            reportDate: "2025-05-01",
            shares: 100,
            financial: {
                revenue: {value: 100},
                dividend: 12,
                dividendMargin: 4,
            },
        });

        expect(screen.getByTestId("period-financial-summary")).toHaveTextContent("Dividend(4%) 12M");
    });

    test("opens the dialogs from the rendered views instead of the action buttons", () => {
        const openEstimateDialog = jest.fn();
        const openRevenueEstimateDialog = jest.fn();
        const openTargetDialog = jest.fn();
        const openNewsSentimentDialog = jest.fn();

        renderPeriod(
            {
                estimate: {current: 1.62, next1: 1.85, next2: null, next3: 2.76},
                revenueEstimate: {current: 1000, next1: 1200, next2: null, next3: 1500},
                targetStats: {count: 3, minimum: 120, average: 145.5, maximum: 175},
            },
            {openEstimateDialog, openRevenueEstimateDialog, openTargetDialog, openNewsSentimentDialog}
        );

        fireEvent.click(screen.getByTestId("period-estimates-view"));
        expect(openEstimateDialog).toHaveBeenCalledWith(expect.objectContaining({id: "period-1"}));

        fireEvent.click(screen.getByTestId("period-revenue-estimates-view"));
        expect(openRevenueEstimateDialog).toHaveBeenCalledWith(expect.objectContaining({id: "period-1"}));

        fireEvent.click(screen.getByTestId("period-target-summary"));
        expect(openTargetDialog).toHaveBeenCalledWith(expect.objectContaining({id: "period-1"}));

        expect(screen.queryByRole("button", {name: "Manage Targets"})).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "View News Sentiment"}));
        expect(openNewsSentimentDialog).toHaveBeenCalledWith(expect.objectContaining({id: "period-1"}));
    });

    test("keeps the action buttons when the views are missing", () => {
        renderPeriod({});

        expect(screen.getByRole("button", {name: "Add EPS Estimates"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Add Revenue Estimates"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Manage Targets"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "View News Sentiment"})).toBeInTheDocument();
        expect(screen.queryByTestId("period-target-summary")).not.toBeInTheDocument();
    });

    test("keeps the targets button while candidates can be imported", () => {
        renderPeriod(
            {targetStats: {count: 3, minimum: 120, average: 145.5, maximum: 175}},
            {targetCandidateCount: 2}
        );

        expect(screen.getByTestId("period-target-summary")).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Manage Targets"})).toHaveTextContent("2");
    });

    test("keeps the targets button when the candidate check failed", () => {
        renderPeriod(
            {targetStats: {count: 3, minimum: 120, average: 145.5, maximum: 175}},
            {targetCandidateFailed: true}
        );

        expect(screen.getByRole("button", {name: "Manage Targets"})).toHaveTextContent("!");
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
        expect(screen.getByRole("button", {name: "Add EPS Estimates"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Manage Targets"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Manage Targets"})).not.toHaveTextContent(/[1-9]/);
        expect(screen.queryByTestId("period-target-summary")).not.toBeInTheDocument();
    });

    test("renders revenue estimates in millions next to the eps estimates", () => {
        const openRevenueEstimateDialog = jest.fn();
        render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2026", type: "Q2"},
                    endingMonth: "2026-07",
                    reportDate: null,
                    estimate: {current: 1.62, next1: null, next2: null, next3: null},
                    revenueEstimate: {
                        past4: 46740,
                        past3: 57010,
                        past2: 68130,
                        past1: 81620,
                        current: 94320,
                        next1: 108450,
                        next2: null,
                        next3: null,
                    },
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
                openRevenueEstimateDialog={openRevenueEstimateDialog}
            />
        );

        expect(screen.getByTestId("period-revenue-estimates"))
            .toHaveTextContent("Revenues: 46.7B | 57B | 68.1B | 81.6B => 94.3B | 108B | - | -");
        expect(screen.getByTestId("period-estimates"))
            .toHaveTextContent("Estimates: - | - | - | - => 1.62 | - | - | -");

        fireEvent.click(screen.getByRole("button", {name: "Add Revenue Estimates"}));
        expect(openRevenueEstimateDialog).toHaveBeenCalledWith(expect.objectContaining({id: "period-1"}));
    });

    test("marks the ending month when a quarter does not follow the previous one", () => {
        render(
            <Period
                period={{id: "period-1", name: {year: "2027", type: "Q2"}, endingMonth: "2027-07"}}
                previousPeriod={{id: "period-2", name: {year: "2027", type: "Q1"}, endingMonth: "2026-04"}}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.getByTestId("period-ending-month-warning")).toHaveTextContent("07/27");
    });

    test("keeps the ending month unmarked when the quarter follows three months later", () => {
        render(
            <Period
                period={{id: "period-1", name: {year: "2027", type: "Q2"}, endingMonth: "2026-07"}}
                previousPeriod={{id: "period-2", name: {year: "2027", type: "Q1"}, endingMonth: "2026-04"}}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.queryByTestId("period-ending-month-warning")).not.toBeInTheDocument();
    });

    test("does not mark the ending month without a previous quarter to compare", () => {
        render(
            <Period
                period={{id: "period-1", name: {year: "2027", type: "Q2"}, endingMonth: "2027-07"}}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.queryByTestId("period-ending-month-warning")).not.toBeInTheDocument();
    });

    test("does not mark the ending month across a gap in the period names", () => {
        render(
            <Period
                period={{id: "period-1", name: {year: "2026", type: "Q1"}, endingMonth: "2026-03"}}
                previousPeriod={{id: "period-2", name: {year: "2025", type: "Q1"}, endingMonth: "2025-01"}}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.queryByTestId("period-ending-month-warning")).not.toBeInTheDocument();
    });

    test("marks the ending month of a consecutive half year that breaks the cadence", () => {
        render(
            <Period
                period={{id: "period-1", name: {year: "2026", type: "H1"}, endingMonth: "2026-09"}}
                previousPeriod={{id: "period-2", name: {year: "2025", type: "H2"}, endingMonth: "2025-12"}}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.getByTestId("period-ending-month-warning")).toHaveTextContent("09/26");
    });

    test("does not mark consecutive full years that keep the cadence", () => {
        render(
            <Period
                period={{id: "period-1", name: {year: "2026", type: "FY"}, endingMonth: "2026-12"}}
                previousPeriod={{id: "period-2", name: {year: "2025", type: "FY"}, endingMonth: "2025-12"}}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.queryByTestId("period-ending-month-warning")).not.toBeInTheDocument();
    });

    test("hides the estimate lines in the stretched narrow view", () => {
        render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2026", type: "Q2"},
                    endingMonth: "2026-07",
                    estimate: {current: 1.62, next1: null, next2: null, next3: null},
                    revenueEstimate: {current: 94320, next1: null, next2: null, next3: null},
                }}
                stretch
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.queryByTestId("period-estimates")).not.toBeInTheDocument();
        expect(screen.queryByTestId("period-revenue-estimates")).not.toBeInTheDocument();
    });

    test("opens the content editor dialog when the stretched content is clicked", () => {
        const {container} = render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2026", type: "Q2"},
                    endingMonth: "2026-07",
                    research: "[{\"type\":\"paragraph\",\"children\":[{\"text\":\"saved research\"}]}]",
                }}
                ticker={"NVDA"}
                stretch
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.queryByRole("button", {name: "Edit Content"})).not.toBeInTheDocument();
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

        fireEvent.click(container.querySelector("[data-period-scroll]"));

        expect(screen.getByRole("dialog")).toBeInTheDocument();
        expect(screen.getByTestId("content-editor-title")).toHaveTextContent("NVDA 26Q2");
        expect(screen.getByRole("button", {name: "Save"})).toBeInTheDocument();
    });

    test("groups the estimates and the targets under one rule", () => {
        renderPeriod({
            estimate: {current: 1.62, next1: 1.85, next2: null, next3: 2.76},
            revenueEstimate: {current: 1000, next1: 1200, next2: null, next3: 1500},
            targetStats: {count: 3, minimum: 120, average: 145.5, maximum: 175},
        });

        const footer = screen.getByTestId("period-footer");
        expect(footer).toContainElement(screen.getByTestId("period-estimates-view"));
        expect(footer).toContainElement(screen.getByTestId("period-revenue-estimates-view"));
        expect(footer).toContainElement(screen.getByTestId("period-target-summary"));
    });

    test("leaves out the rule when a period has neither estimates nor targets", () => {
        renderPeriod({});

        expect(screen.queryByTestId("period-footer")).not.toBeInTheDocument();
    });

    test("leaves out the rule when the stretched narrow view shows nothing under the content", () => {
        renderPeriod(
            {
                reportDate: "2026-08-26",
                financial: {revenue: {value: 300}},
                estimate: {current: 1.62, next1: 1.85, next2: null, next3: 2.76},
                targetStats: {count: 3, minimum: 120, average: 145.5, maximum: 175},
            },
            {stretch: true}
        );

        expect(screen.queryByTestId("period-footer")).not.toBeInTheDocument();
    });

    test("hides the target summary of a reported period in the stretched narrow view", () => {
        const reportedPeriod = {
            id: "period-1",
            name: {year: "2026", type: "Q2"},
            endingMonth: "2026-07",
            reportDate: "2026-08-26",
            financial: {
                revenue: {value: 300},
                grossProfit: {value: 200},
                operatingIncome: {value: 100},
                netIncome: {value: 50},
            },
            targetStats: {count: 3, minimum: 120, average: 145.5, maximum: 175},
        };

        const {rerender} = render(
            <Period period={reportedPeriod} stretch currency={"$"} setAlert={jest.fn()} openDialog={jest.fn()}/>
        );

        expect(screen.queryByTestId("period-target-summary")).not.toBeInTheDocument();

        rerender(
            <Period period={reportedPeriod} currency={"$"} setAlert={jest.fn()} openDialog={jest.fn()}/>
        );

        expect(screen.getByTestId("period-target-summary")).toHaveTextContent("Targets: 3@(175-120)~146$");

        rerender(
            <Period
                period={{...reportedPeriod, financial: undefined}}
                stretch
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.getByTestId("period-target-summary")).toHaveTextContent("Targets: 3@(175-120)~146$");
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

    test("shows the report date from firebase for an unreported period", () => {
        render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2026", type: "Q2"},
                    endingMonth: "2026-07",
                    expectedReportDate: "2026-08-15",
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.getByText("26Q2 - ending: 07/26 - report: 15.08.2026")).toBeInTheDocument();
    });

    test("shows a question mark when the report date is not known for an unreported period", () => {
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
            />
        );

        expect(screen.getByText("26Q2 - ending: 07/26 - report: ?")).toBeInTheDocument();
    });

    test("appends an exclamation mark when firebase already has revenues for an unreported period", () => {
        const {container} = render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2026", type: "Q2"},
                    endingMonth: "2026-07",
                    expectedReportDate: "2026-08-15",
                    reportedInFirebase: true,
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(container.querySelector(".title")).toHaveTextContent("26Q2 - ending: 07/26 - report: 15.08.2026 !");
        expect(screen.getByText("!")).toHaveStyle({fontWeight: 900, color: "rgb(198, 40, 40)"});
        expect(screen.getByText("15.08.2026")).toHaveStyle({color: "rgb(198, 40, 40)", textDecoration: "underline"});
        expect(screen.getByText("report:")).toHaveStyle({color: "rgb(198, 40, 40)"});
    });

    test("replaces the question mark with an exclamation mark when firebase already has revenues but no date is known", () => {
        const {container} = render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2026", type: "Q2"},
                    endingMonth: "2026-07",
                    reportedInFirebase: true,
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(container.querySelector(".title")).toHaveTextContent("26Q2 - ending: 07/26 - report: !");
        expect(screen.queryByText("?")).not.toBeInTheDocument();
        expect(screen.getByText("!")).toHaveStyle({fontWeight: 900, color: "rgb(198, 40, 40)"});
    });

    test("marks the report of an unreported period that is no longer the latest", () => {
        const {container} = render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2026", type: "Q2"},
                    endingMonth: "2026-07",
                    expectedReportDate: "2026-08-15",
                }}
                isLatest={false}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(container.querySelector(".title")).toHaveTextContent("26Q2 - ending: 07/26 - report: 15.08.2026 !");
        expect(screen.getByText("15.08.2026")).toHaveStyle({color: "rgb(198, 40, 40)", textDecoration: "underline"});
        expect(screen.getByText("report:")).toHaveStyle({color: "rgb(198, 40, 40)"});
    });

    test("shows only an exclamation mark for an unreported period without a date that is not the latest", () => {
        const {container} = render(
            <Period
                period={{id: "period-1", name: {year: "2026", type: "Q2"}, endingMonth: "2026-07"}}
                isLatest={false}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(container.querySelector(".title")).toHaveTextContent("26Q2 - ending: 07/26 - report: !");
        expect(screen.queryByText("?")).not.toBeInTheDocument();
        expect(screen.getByText("!")).toHaveStyle({fontWeight: 900, color: "rgb(198, 40, 40)"});
    });

    test("leaves the latest unreported period unmarked without firebase revenues", () => {
        const {container} = render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2026", type: "Q2"},
                    endingMonth: "2026-07",
                    expectedReportDate: "2026-08-15",
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(container.querySelector(".title")).toHaveTextContent("26Q2 - ending: 07/26 - report: 15.08.2026");
        expect(screen.queryByText("!")).not.toBeInTheDocument();
        expect(container.querySelector(".title").querySelector("span")).toBeNull();
    });

    test("leaves a reported period unmarked even when it is not the latest", () => {
        render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2026", type: "Q2"},
                    endingMonth: "2026-07",
                    reportDate: "2026-08-15",
                    financial: {
                        dividend: 0,
                        adjustedEps: 1,
                        revenue: {value: 1},
                        grossProfit: {value: 1},
                        operatingIncome: {value: 1},
                        netIncome: {value: 1},
                    },
                }}
                isLatest={false}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.queryByText("!")).not.toBeInTheDocument();
    });

    test("labels a period as reported and ignores firebase fields once it has financials", () => {
        render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2026", type: "Q2"},
                    endingMonth: "2026-07",
                    reportDate: "2026-08-15",
                    expectedReportDate: "2099-01-01",
                    reportedInFirebase: true,
                    financial: {
                        dividend: 0,
                        adjustedEps: 1,
                        revenue: {value: 1},
                        grossProfit: {value: 1},
                        operatingIncome: {value: 1},
                        netIncome: {value: 1},
                    },
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.getByText("26Q2 - ending: 07/26 - reported: 15.08.2026")).toBeInTheDocument();
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
