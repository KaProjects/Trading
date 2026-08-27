import {act, fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

jest.mock("axios");
jest.mock("../../properties", () => ({backend: "/api"}));
jest.mock("../../service/FormattingService", () => {
    const actual = jest.requireActual("../../service/FormattingService");
    return {
        ...actual,
        formatError: () => ({title: "Request failed", message: "Conflict"}),
    };
});

import {TargetDialog} from "../TargetDialog";

const period = {
    id: "period-1",
    name: {year: "2025", type: "Q2"},
    previousReportDate: "2025-05-28",
    reportDate: "2025-08-27",
};
const company = {ticker: "NVDA", currency: "$"};

function target(overrides = {}) {
    return {
        id: "target-1",
        periodId: "period-1",
        date: "2025-07-10",
        institution: "Northstar",
        price: 175.25,
        rating: "Buy",
        overview: "Demand remains strong.",
        takeaway1: "Margins are expanding.",
        takeaway2: null,
        takeaway3: null,
        takeaway4: null,
        ...overrides,
    };
}

function arrangeGetResponses({targets = [], count = 0, warnings = []} = {}) {
    axios.get.mockImplementation(url => {
        if (url.endsWith("/sync/count")) {
            return Promise.resolve({data: {count, warnings}});
        }
        return Promise.resolve({data: targets});
    });
}

async function waitForRequestsToSettle() {
    await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 0));
    });
    await waitFor(() => expect(axios.get.mock.calls.length).toBeGreaterThanOrEqual(2));
    await waitFor(() => expect(screen.queryByRole("progressbar")).not.toBeInTheDocument());
}

describe("TargetDialog", () => {
    const triggerRefresh = jest.fn();

    beforeEach(() => {
        axios.get.mockReset();
        axios.post.mockReset();
        axios.delete.mockReset();
        triggerRefresh.mockReset();
    });

    test("loads only persisted targets and shows candidate count and warnings", async () => {
        arrangeGetResponses({
            targets: [target()],
            count: 2,
            warnings: ["Firebase targets for NVDA could not be loaded completely"],
        });

        render(
            <TargetDialog
                open
                handleClose={jest.fn()}
                triggerRefresh={triggerRefresh}
                company={company}
                period={period}
            />
        );

        await waitForRequestsToSettle();
        expect(screen.getByText("Northstar | $175.25")).toBeInTheDocument();
        expect(screen.getByText("10.07.2025 | Buy")).toBeInTheDocument();
        expect(screen.getByText("Demand remains strong.")).toBeInTheDocument();
        expect(screen.getByText("Margins are expanding.")).toBeInTheDocument();
        expect(screen.getByText("2")).toBeInTheDocument();
        expect(screen.getByText("Some expected target data could not be loaded")).toBeInTheDocument();
        expect(screen.getByText("Firebase targets for NVDA could not be loaded completely")).toBeInTheDocument();
        expect(screen.queryByLabelText(/Target date/)).not.toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Add Target"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Delete target Northstar"})).toHaveClass("deleteTarget");
        expect(screen.getByTestId("target-target-1")).toHaveAttribute("data-highlighted", "false");
        expect(axios.get).toHaveBeenCalledWith("/api/target/period-1");
        expect(axios.get).toHaveBeenCalledWith("/api/target/period-1/sync/count");
    });

    test("validates and manually creates a target", async () => {
        const createdTarget = target({id: "target-2", institution: "Manual Capital"});
        let persistedTargets = [];
        axios.get.mockImplementation(url => url.endsWith("/sync/count")
            ? Promise.resolve({data: {count: 0, warnings: []}})
            : Promise.resolve({data: persistedTargets}));
        axios.post.mockImplementation(() => {
            persistedTargets = [createdTarget];
            return Promise.resolve({data: createdTarget});
        });

        render(
            <TargetDialog
                open
                handleClose={jest.fn()}
                triggerRefresh={triggerRefresh}
                company={company}
                period={period}
            />
        );

        await waitForRequestsToSettle();
        fireEvent.click(screen.getByRole("button", {name: "Add Target"}));
        expect(screen.getByLabelText(/Target date/)).toBeInTheDocument();
        expect(screen.queryByText("Saved targets")).not.toBeInTheDocument();
        expect(screen.getAllByText("not filled")).toHaveLength(3);
        expect(axios.post).not.toHaveBeenCalled();

        fireEvent.change(screen.getByLabelText(/Target date/), {target: {value: "2025-07-10"}});
        fireEvent.change(screen.getByLabelText(/Institution/), {target: {value: "  Northstar  "}});
        fireEvent.change(screen.getByLabelText(/Price/), {target: {value: "175.2500"}});
        fireEvent.change(screen.getByLabelText("Rating"), {target: {value: " Buy "}});
        fireEvent.change(screen.getByLabelText("Overview"), {target: {value: " Overview "}});
        fireEvent.change(screen.getByLabelText("Takeaway 1"), {target: {value: " First "}});
        expect(screen.getByText("10/1000")).toBeInTheDocument();
        expect(screen.getByText("7/500")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Add"}));
        await waitForRequestsToSettle();

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith(
            "/api/target/period-1",
            {
                date: "2025-07-10",
                institution: "Northstar",
                price: "175.2500",
                rating: "Buy",
                overview: "Overview",
                takeaway1: "First",
                takeaway2: null,
                takeaway3: null,
                takeaway4: null,
            }
        ));
        await waitFor(() => expect(triggerRefresh).toHaveBeenCalledTimes(1));
        await waitFor(() => expect(screen.getByRole("button", {name: "Add Target"})).toBeInTheDocument());
        expect(screen.queryByLabelText(/Target date/)).not.toBeInTheDocument();
        expect(screen.getByTestId("target-target-2")).toHaveAttribute("data-highlighted", "true");
    });

    test("validates all text field length constraints", async () => {
        arrangeGetResponses();

        render(
            <TargetDialog
                open
                handleClose={jest.fn()}
                triggerRefresh={triggerRefresh}
                company={company}
                period={period}
            />
        );

        await waitForRequestsToSettle();
        fireEvent.click(screen.getByRole("button", {name: "Add Target"}));
        fireEvent.change(screen.getByLabelText(/Target date/), {target: {value: "2025-07-10"}});
        fireEvent.change(screen.getByLabelText(/Institution/), {target: {value: "I".repeat(51)}});
        fireEvent.change(screen.getByLabelText(/Price/), {target: {value: "175"}});
        fireEvent.change(screen.getByLabelText("Rating"), {target: {value: "R".repeat(31)}});
        fireEvent.change(screen.getByLabelText("Overview"), {target: {value: "O".repeat(1001)}});
        fireEvent.change(screen.getByLabelText("Takeaway 1"), {target: {value: "T".repeat(501)}});

        expect(screen.getByText("max length 50")).toBeInTheDocument();
        expect(screen.getByText("max length 30")).toBeInTheDocument();
        expect(screen.getByText("max length 1000")).toBeInTheDocument();
        expect(screen.getByText("max length 500")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Add"}));
        expect(axios.post).not.toHaveBeenCalled();
    });

    test("restricts manual target dates to the period import window", async () => {
        arrangeGetResponses();

        render(
            <TargetDialog
                open
                handleClose={jest.fn()}
                triggerRefresh={triggerRefresh}
                company={company}
                period={period}
            />
        );

        await waitForRequestsToSettle();
        fireEvent.click(screen.getByRole("button", {name: "Add Target"}));
        const dateInput = screen.getByLabelText(/Target date/);

        expect(dateInput).toHaveAttribute("min", "2025-05-28");
        expect(dateInput).toHaveAttribute("max", "2025-08-26");

        fireEvent.change(dateInput, {target: {value: "2025-05-27"}});
        expect(screen.getByText("must be from 2025-05-28 to 2025-08-26")).toBeInTheDocument();

        fireEvent.change(dateInput, {target: {value: "2025-08-27"}});
        expect(screen.getByText("must be from 2025-05-28 to 2025-08-26")).toBeInTheDocument();

        fireEvent.change(dateInput, {target: {value: "2025-05-28"}});
        expect(screen.queryByText("must be from 2025-05-28 to 2025-08-26")).not.toBeInTheDocument();
    });

    test.each([
        ["half-year", {id: "half", name: {year: "2025", type: "H2"}, reportDate: "2025-08-27"}, "2025-02-27", "2025-08-26"],
        ["fiscal-year", {id: "fiscal", name: {year: "2025", type: "FY"}, previousReportDate: "2025-05-28"}, "2025-05-28", "2026-05-27"],
    ])("uses the %s fallback window for manual target dates", async (_name, selectedPeriod, expectedMin, expectedMax) => {
        arrangeGetResponses();

        render(
            <TargetDialog
                open
                handleClose={jest.fn()}
                triggerRefresh={triggerRefresh}
                company={company}
                period={selectedPeriod}
            />
        );

        await waitForRequestsToSettle();
        fireEvent.click(screen.getByRole("button", {name: "Add Target"}));

        const dateInput = screen.getByLabelText(/Target date/);
        expect(dateInput).toHaveAttribute("min", expectedMin);
        expect(dateInput).toHaveAttribute("max", expectedMax);
    });

    test("cancels manual creation and resets the draft", async () => {
        arrangeGetResponses({
            targets: [target()],
            count: 1,
            warnings: ["Firebase warning"],
        });

        render(
            <TargetDialog
                open
                handleClose={jest.fn()}
                triggerRefresh={triggerRefresh}
                company={company}
                period={period}
            />
        );

        await waitForRequestsToSettle();
        fireEvent.click(screen.getByRole("button", {name: "Add Target"}));
        fireEvent.change(screen.getByLabelText(/Institution/), {target: {value: "Unsaved institution"}});

        expect(screen.queryByText("Northstar | $175.25")).not.toBeInTheDocument();
        expect(screen.queryByText("Firebase warning")).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Back"}));

        expect(screen.getByText("Northstar | $175.25")).toBeInTheDocument();
        expect(screen.getByText("Firebase warning")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Add Target"}));
        expect(screen.getByLabelText(/Institution/)).toHaveValue("");
    });

    test("clears a server alert when any field is edited", async () => {
        arrangeGetResponses();
        axios.post.mockRejectedValue({name: "AxiosError"});

        render(
            <TargetDialog
                open
                handleClose={jest.fn()}
                triggerRefresh={triggerRefresh}
                company={company}
                period={period}
            />
        );

        await waitForRequestsToSettle();
        fireEvent.click(screen.getByRole("button", {name: "Add Target"}));
        fireEvent.change(screen.getByLabelText(/Target date/), {target: {value: "2025-07-10"}});
        fireEvent.change(screen.getByLabelText(/Institution/), {target: {value: "Northstar"}});
        fireEvent.change(screen.getByLabelText(/Price/), {target: {value: "175"}});
        fireEvent.click(screen.getByRole("button", {name: "Add"}));
        await act(async () => {
            await new Promise(resolve => setTimeout(resolve, 0));
        });

        expect(await screen.findByText("Request failed")).toBeInTheDocument();
        fireEvent.change(screen.getByLabelText(/Institution/), {target: {value: "Northstar Research"}});
        expect(screen.queryByText("Request failed")).not.toBeInTheDocument();
        await waitFor(() => expect(screen.getByRole("button", {name: "Add"})).not.toBeDisabled());
    });

    test("syncs candidates and deletes persisted targets", async () => {
        const existingTarget = target();
        const importedTarget = target({
            id: "target-2",
            institution: "Imported Capital",
            date: "2025-07-12",
        });
        let persistedTargets = [existingTarget];
        let count = 1;
        axios.get.mockImplementation(url => url.endsWith("/sync/count")
            ? Promise.resolve({data: {count, warnings: []}})
            : Promise.resolve({data: persistedTargets}));
        axios.post.mockImplementation(() => {
            persistedTargets = [existingTarget, importedTarget];
            count = 0;
            return Promise.resolve({data: {count: 1, warnings: []}});
        });
        axios.delete.mockImplementation(() => {
            persistedTargets = persistedTargets.filter(item => item.id !== existingTarget.id);
            return Promise.resolve({});
        });

        render(
            <TargetDialog
                open
                handleClose={jest.fn()}
                triggerRefresh={triggerRefresh}
                company={company}
                period={period}
            />
        );

        await waitForRequestsToSettle();
        expect(screen.getByText("Northstar | $175.25")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Sync targets"}));
        await waitForRequestsToSettle();

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("/api/target/period-1/sync"));
        await waitFor(() => expect(triggerRefresh).toHaveBeenCalledTimes(1));
        await waitFor(() => expect(screen.queryByRole("progressbar")).not.toBeInTheDocument());
        expect(screen.getByTestId("target-target-1")).toHaveAttribute("data-highlighted", "false");
        expect(screen.getByTestId("target-target-2")).toHaveAttribute("data-highlighted", "true");

        const deleteButton = screen.getByRole("button", {name: "Delete target Northstar"});
        await waitFor(() => expect(deleteButton).not.toBeDisabled());
        fireEvent.click(deleteButton);
        await waitForRequestsToSettle();
        await waitFor(() => expect(axios.delete).toHaveBeenCalledWith("/api/target/target-1"));
        await waitFor(() => expect(triggerRefresh).toHaveBeenCalledTimes(2));
        await waitFor(() => expect(screen.queryByRole("progressbar")).not.toBeInTheDocument());
        expect(screen.queryByRole("button", {name: "Delete target Northstar"})).not.toBeInTheDocument();
    });
});
