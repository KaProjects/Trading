import {matchText} from "../../../testing/matchText";
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
                    estimates: [{
                            current: 1.62,
                            next1: 1.85,
                            next2: null,
                            next3: 2.76,
                    }],
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
            .toHaveTextContent("Eps estimates:- | - | - | - => 1.62 | 1.85 | - | 2.76");
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

    test("writes the estimates from the action buttons, and opens the targets from the summary", () => {
        const openEstimateDialog = jest.fn();
        const openRevenueEstimateDialog = jest.fn();
        const openTargetDialog = jest.fn();
        const openNewsSentimentDialog = jest.fn();

        renderPeriod(
            {
                estimates: [{current: 1.62, next1: 1.85, next2: null, next3: 2.76}],
                revenueEstimates: [{current: 1000, next1: 1200, next2: null, next3: 1500}],
                targetStats: {count: 3, minimum: 120, average: 145.5, maximum: 175},
            },
            {openEstimateDialog, openRevenueEstimateDialog, openTargetDialog, openNewsSentimentDialog}
        );

        // The estimates read among the days, so they are shown rather than written in: the
        // buttons are what open their dialogs.
        fireEvent.click(screen.getByTestId("period-estimates"));
        fireEvent.click(screen.getByTestId("period-revenue-estimates"));
        expect(openEstimateDialog).not.toHaveBeenCalled();
        expect(openRevenueEstimateDialog).not.toHaveBeenCalled();

        fireEvent.click(screen.getByRole("button", {name: "Add EPS Estimates"}));
        expect(openEstimateDialog).toHaveBeenCalledWith(expect.objectContaining({id: "period-1"}));

        fireEvent.click(screen.getByRole("button", {name: "Add Revenue Estimates"}));
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

    test("saves an entry against the date that identifies it", async () => {
        axios.put.mockResolvedValue({data: {
            id: 7,
            date: "2026-01-10",
            content: JSON.stringify([{type: "paragraph", children: [{text: "Updated research"}]}]),
            images: [],
        }});

        render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2025", type: "FY"},
                    endingMonth: "2025-12",
                    reportDate: "2026-02-15",
                    previousReportDate: "2025-11-01",
                    contents: [{id: 7, date: "2026-01-10", content: "Stored research", images: []}],
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.getByTestId("period-content-date-2026-01-10")).toHaveTextContent("10.01.2026");

        fireEvent.click(screen.getByText("Stored research"));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith(
            "/api/period/content/period-1",
            {
                date: "2026-01-10",
                content: JSON.stringify([{type: "paragraph", children: [{text: "Updated research"}]}]),
            }
        ));
    });

    test("writes a new day in its own dialog on a narrow screen", () => {
        render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2026", type: "Q2"},
                    endingMonth: "2026-07",
                    reportDate: "2026-08-26",
                    previousReportDate: "2026-05-20",
                    contents: [],
                }}
                ticker={"NVDA"}
                stretch
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        fireEvent.click(screen.getByRole("button", {name: "Add Content"}));

        // The dialog the day is written in, with the day itself to pick at the top of it.
        expect(screen.getByRole("dialog")).toBeInTheDocument();
        expect(screen.getByTestId("content-editor-title")).toHaveTextContent("NVDA 26Q2 new content");
        expect(screen.getByLabelText("New content date")).toBeInTheDocument();
        expect(screen.queryByTestId("period-content-new")).not.toBeInTheDocument();
    });

    test("keeps an emptied day on the page until it is loaded again", async () => {
        const empty = '[{"type":"paragraph","children":[{"text":""}]}]';
        axios.put.mockResolvedValue({data: {id: 7, date: "2026-01-10", content: empty, images: []}});

        render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2025", type: "FY"},
                    endingMonth: "2025-12",
                    reportDate: "2026-02-15",
                    previousReportDate: "2025-11-01",
                    contents: [{id: 7, date: "2026-01-10", content: "Stored research", images: []}],
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        fireEvent.click(screen.getByText("Stored research"));

        // Cleared, but still there to write in again; the next load is what drops it.
        await waitFor(() => expect(axios.put).toHaveBeenCalled());
        expect(screen.getByTestId("period-content-date-2026-01-10")).toBeInTheDocument();
    });

    test("marks a date the period no longer covers", () => {
        render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2025", type: "FY"},
                    endingMonth: "2025-12",
                    reportDate: "2026-02-15",
                    previousReportDate: "2025-11-01",
                    contents: [
                        {id: 7, date: "2026-01-10", content: "inside", images: []},
                        {id: 8, date: "2026-03-20", content: "after the report", images: []},
                    ],
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.getByTestId("period-content-date-2026-03-20"))
            .toHaveAttribute("title", "This date is outside the period");
        expect(screen.getByTestId("period-content-date-2026-01-10")).not.toHaveAttribute("title");
    });

    test("renders estimates when financials are missing", () => {
        render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2026", type: "Q2"},
                    endingMonth: "2026-07",
                    reportDate: null,
                    estimates: [{
                            current: 1.62,
                            next1: null,
                            next2: null,
                            next3: null,
                    }],
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.getByTestId("period-estimates"))
            .toHaveTextContent("Eps estimates:- | - | - | - => 1.62 | - | - | -");
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
                    estimates: [{current: 1.62, next1: null, next2: null, next3: null}],
                    revenueEstimates: [{
                            past4: 46740,
                            past3: 57010,
                            past2: 68130,
                            past1: 81620,
                            current: 94320,
                            next1: 108450,
                            next2: null,
                            next3: null,
                    }],
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
                openRevenueEstimateDialog={openRevenueEstimateDialog}
            />
        );

        expect(screen.getByTestId("period-revenue-estimates"))
            .toHaveTextContent("Rev. estimates:46.7B | 57B | 68.1B | 81.6B => 94.3B | 108B | - | -");
        expect(screen.getByTestId("period-estimates"))
            .toHaveTextContent("Eps estimates:- | - | - | - => 1.62 | - | - | -");

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

    test("keeps the estimate lines among the days in the stretched narrow view", () => {
        render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2026", type: "Q2"},
                    endingMonth: "2026-07",
                    estimates: [{current: 1.62, next1: null, next2: null, next3: null}],
                    revenueEstimates: [{current: 94320, next1: null, next2: null, next3: null}],
                }}
                stretch
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        // They are part of the research now, which the narrow view shows as well; only their
        // past figures are dropped at that width, and that is the line's own doing.
        const list = screen.getByTestId("period-content-list");
        expect(list).toContainElement(screen.getByTestId("period-estimates"));
        expect(list).toContainElement(screen.getByTestId("period-revenue-estimates"));
    });

    test("opens the content editor dialog when the stretched content is clicked", () => {
        const {container} = render(
            <Period
                period={{
                    id: "period-1",
                    name: {year: "2026", type: "Q2"},
                    endingMonth: "2026-07",
                    contents: [{id: 3, date: "2026-07-14", content: "saved research", images: []}],
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
        expect(container.querySelector("[data-period-scroll]")).not.toBeNull();

        // On a narrow screen an entry is edited in its own dialog, which names the day.
        fireEvent.click(screen.getByText("saved research"));

        expect(screen.getByRole("dialog")).toBeInTheDocument();
        expect(screen.getByTestId("content-editor-title")).toHaveTextContent("NVDA 26Q2 14.07.2026");
        expect(screen.getByRole("button", {name: "Save"})).toBeInTheDocument();
    });

    test("will not write a new day over one the period already has", async () => {
        renderPeriod({
            reportDate: "2026-08-26",
            previousReportDate: "2026-05-20",
            contents: [{id: 1, date: "2026-07-14", content: "written that day", images: []}],
        }, {stretch: true, ticker: "NVDA"});

        fireEvent.click(screen.getByRole("button", {name: "Add Content"}));
        fireEvent.change(screen.getByLabelText("New content date"), {target: {value: "2026-07-14"}});
        expect(screen.getByLabelText("New content date"))
            .toHaveAttribute("title", "This date already has content");

        fireEvent.click(screen.getByRole("button", {name: "Save"}));

        await waitFor(() => expect(screen.getByText("Date already used")).toBeInTheDocument());
        expect(axios.put).not.toHaveBeenCalled();
        // The dialog stays open on the day that was refused.
        expect(screen.getByRole("dialog")).toBeInTheDocument();
    });

    test("offers no second new day while one is already open", () => {
        renderPeriod({reportDate: "2026-08-26", previousReportDate: "2026-05-20"});

        fireEvent.click(screen.getByRole("button", {name: "Add Content"}));

        expect(screen.getByTestId("period-content-new")).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Add Content"})).not.toBeInTheDocument();
    });

    test("reads the estimates among the days, each under its own date", () => {
        renderPeriod({
            reportDate: "2026-08-26",
            previousReportDate: "2026-05-20",
            estimates: [{current: 1.62, next1: 1.85, next2: null, next3: 2.76, datetime: "2026-07-14T09:00:00"}],
            revenueEstimates: [{current: 1000, next1: 1200, next2: null, next3: 1500, datetime: "2026-08-03T09:00:00"}],
        });

        const eps = screen.getByTestId("period-estimates");
        const revenue = screen.getByTestId("period-revenue-estimates");
        expect(screen.getByTestId("period-estimate-date-2026-07-14")).toHaveTextContent("14.07.2026");
        expect(screen.getByTestId("period-estimate-date-2026-08-03")).toHaveTextContent("03.08.2026");
        // Newest day first, like the days they read between.
        expect(revenue.compareDocumentPosition(eps) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    test("reads every estimate the period holds, each on the day it was taken", () => {
        renderPeriod({
            reportDate: "2026-08-26",
            previousReportDate: "2026-05-20",
            estimates: [
                {id: 2, current: 1.8, next1: 2, next2: null, next3: 2.9, datetime: "2026-08-03T09:00:00"},
                {id: 1, current: 1.62, next1: 1.85, next2: null, next3: 2.76, datetime: "2026-06-11T09:00:00"},
            ],
        });

        const lines = screen.getAllByTestId("period-estimates");
        expect(lines).toHaveLength(2);
        // Newest first, each under the day it was taken.
        expect(lines[0]).toHaveTextContent("1.8 | 2 | - | 2.9");
        expect(lines[1]).toHaveTextContent("1.62 | 1.85 | - | 2.76");
        expect(screen.getByTestId("period-estimate-date-2026-08-03")).toBeInTheDocument();
        expect(screen.getByTestId("period-estimate-date-2026-06-11")).toBeInTheDocument();
    });

    test("opens a shared day with its estimates and names it once", () => {
        renderPeriod({
            reportDate: "2026-08-26",
            previousReportDate: "2026-05-20",
            contents: [{id: 1, date: "2026-07-14", content: "written that day", images: []}],
            estimates: [{current: 1.62, next1: 1.85, next2: null, next3: 2.76, datetime: "2026-07-14T09:00:00"}],
        });

        const heading = screen.getByTestId("period-estimate-date-2026-07-14");
        expect(heading).toHaveTextContent("14.07.2026");
        expect(screen.queryByTestId("period-content-date-2026-07-14")).not.toBeInTheDocument();
        // The day's estimates first, the research written that day under them.
        const estimates = screen.getByTestId("period-estimates");
        const written = screen.getByText("written that day");
        expect(heading.compareDocumentPosition(estimates) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(estimates.compareDocumentPosition(written) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    test("puts the targets in the free corner of the reported figures", () => {
        renderPeriod({
            reportDate: "2026-08-26",
            shares: 100,
            priceHigh: 20,
            priceLow: 10,
            financial: {revenue: {value: 300}, grossProfit: {value: 200}},
            targetStats: {count: 3, minimum: 120, average: 145.5, maximum: 175},
        });

        const summary = screen.getByTestId("period-financial-summary");
        const targets = screen.getByTestId("period-target-summary");
        // Under the price range, in the half of that column the two price rows leave empty, so
        // a long line of targets has the width of both to say it on - and still clears the
        // action buttons on the right.
        const column = summary.lastElementChild;
        expect(column).toContainElement(targets);
        expect(column.firstElementChild).toHaveTextContent("High");
        const corner = window.getComputedStyle(targets.parentElement);
        expect(corner.marginTop).toBe("auto");
        expect(corner.alignSelf).toBe("flex-end");
        expect(corner.whiteSpace).toBe("nowrap");
        expect(corner.paddingRight).toBe("18px");
    });

    test("lays the targets over the top corner of a period with nothing reported yet", () => {
        renderPeriod({
            targetStats: {count: 3, minimum: 120, average: 145.5, maximum: 175},
        });

        expect(screen.queryByTestId("period-financial-summary")).not.toBeInTheDocument();
        const corner = window.getComputedStyle(screen.getByTestId("period-target-summary").parentElement);
        // Laid over the research, so the research still starts at the top of the card.
        expect(corner.position).toBe("absolute");
        expect(corner.top).toBe("4px");
        expect(corner.right).toBe("29px");
    });

    test("keeps the targets under the research in the stretched narrow view", () => {
        renderPeriod(
            {targetStats: {count: 3, minimum: 120, average: 145.5, maximum: 175}},
            {stretch: true}
        );

        const footer = screen.getByTestId("period-footer");
        expect(footer).toContainElement(screen.getByTestId("period-target-summary"));
        const content = screen.getByTestId("period-content-list");
        expect(content.compareDocumentPosition(footer) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    test("keeps the target summary of a reported period in the stretched narrow view", () => {
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

        // The reported figures own no corner at this width, so the targets read under the
        // research instead - but they are read either way.
        expect(screen.getByTestId("period-footer"))
            .toContainElement(screen.getByTestId("period-target-summary"));

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
                    estimates: [{
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
                    }],
                }}
                currency={"$"}
                setAlert={jest.fn()}
                openDialog={jest.fn()}
            />
        );

        expect(screen.getByTestId("period-estimates"))
            .toHaveTextContent("Eps estimates:0.91 | 1.05 | - | 1.42 => 1.62 | 1.85 | - | 2.76");
        expect(screen.getByTestId("period-estimate-date-2026-08-02")).toHaveTextContent("02.08.2026");
        expect(screen.getByText("(4.38)")).toBeInTheDocument();
        expect(screen.getByText(matchText("(+12.5% | -3.3% | 0% | +4%)"))).toBeInTheDocument();
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
