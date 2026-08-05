import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

const mockFormatError = jest.fn(() => ({title: "Load failed", message: "Estimate data could not be loaded"}));

jest.mock("axios");
jest.mock("../../properties", () => ({backend: "http://backend"}));
jest.mock("../../service/FormattingService", () => ({
    ...jest.requireActual("../../service/FormattingService"),
    formatError: (...args) => mockFormatError(...args),
}));
jest.mock("../component/DialogTextField", () => ({
    DialogTextField: ({id, label, value = "", onChange, required = true}) => (
        <input
            data-testid={id}
            aria-label={label}
            value={value}
            onChange={onChange}
            required={required}
        />
    ),
}));
jest.mock("../component/DialogDatePicker", () => ({
    DialogDatePicker: ({id, label, value = "", onChange, required = true}) => (
        <input
            data-testid={id}
            aria-label={label}
            type="date"
            value={value}
            onChange={onChange}
            required={required}
        />
    ),
}));

import {AddEstimateDialog} from "../AddEstimateDialog";

const history = [
    {id: "estimate-2", datetime: "2026-08-02T12:30:00", current: 1.5, next1: 1.7, next2: null, next3: 2.2},
    {id: "estimate-1", datetime: "2026-08-01T12:30:00", current: 1.4, next1: 1.6, next2: 1.9, next3: null},
];

const imported = {
    current: {eps: "1.62", date: "2026-08-10"},
    next1: {eps: "1.856", date: "2026-11-10"},
    next2: null,
    next3: {eps: "2.76", date: "2027-05-10"},
};

function createProps(overrides = {}) {
    return {
        open: true,
        handleClose: jest.fn(),
        triggerRefresh: jest.fn(),
        company: {id: "company-1", ticker: "NVDA"},
        period: {id: "period-1", name: {year: "2026", type: "Q2"}},
        ...overrides,
    };
}

describe("AddEstimateDialog", () => {
    beforeEach(() => {
        axios.get.mockReset();
        axios.post.mockReset();
        mockFormatError.mockClear();
        axios.get.mockImplementation(url => Promise.resolve({
            data: url === "http://backend/estimate/period-1" ? history : imported,
        }));
        axios.post.mockResolvedValue({});
    });

    test("loads history and imported values, then creates an estimate", async () => {
        const props = createProps();
        render(<AddEstimateDialog {...props}/>);

        expect(await screen.findByText("02.08.2026")).toBeInTheDocument();
        expect(screen.getByText("01.08.2026")).toBeInTheDocument();
        expect(axios.get).toHaveBeenCalledWith("http://backend/estimate/period-1");
        expect(axios.get).toHaveBeenCalledWith("http://backend/research/company-1/import/estimate/period-1");

        expect(screen.getByTestId("external-estimates"))
            .toHaveTextContent("External estimates: [ 1.62 (10.08.2026) | 1.86 (10.11.2026) | - | 2.76 (10.05.2027) ]");
        fireEvent.click(screen.getByRole("button", {name: "Use external estimates"}));
        expect(screen.getByText(/^External estimates:/)).toBeInTheDocument();
        expect(screen.getByTestId("estimate-date")).toHaveValue("");
        expect(screen.getByTestId("estimate-current")).toHaveValue("1.62");
        expect(screen.getByTestId("estimate-next1")).toHaveValue("1.86");
        expect(screen.getByTestId("estimate-next2")).toHaveValue("");
        expect(screen.getByTestId("estimate-next3")).toHaveValue("2.76");

        fireEvent.change(screen.getByTestId("estimate-date"), {target: {value: "2026-08-03"}});
        fireEvent.click(screen.getByRole("button", {name: "Add"}));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith(
            "http://backend/estimate/period-1",
            {
                date: "2026-08-03",
                current: "1.62",
                next1: "1.86",
                next2: null,
                next3: "2.76",
            },
        ));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.handleClose).toHaveBeenCalled();
    });

    test("parses two to four pasted estimates and keeps valid parser input reusable", async () => {
        render(<AddEstimateDialog {...createProps()}/>);
        await screen.findByText("02.08.2026");

        const parser = screen.getByTestId("estimate-parser");
        const useParsed = screen.getByRole("button", {name: "Use parsed estimates"});
        expect(useParsed).toBeDisabled();

        fireEvent.change(parser, {target: {value: "1,25\t-2.5 | 3 / 4,756"}});
        expect(useParsed).toBeEnabled();
        fireEvent.click(useParsed);

        expect(screen.getByTestId("estimate-current")).toHaveValue("1.25");
        expect(screen.getByTestId("estimate-next1")).toHaveValue("-2.50");
        expect(screen.getByTestId("estimate-next2")).toHaveValue("3.00");
        expect(screen.getByTestId("estimate-next3")).toHaveValue("4.76");
        expect(parser).toHaveValue("1,25\t-2.5 | 3 / 4,756");
        expect(useParsed).toBeEnabled();
    });

    test("does not submit when date and current are blank", async () => {
        render(<AddEstimateDialog {...createProps()}/>);
        await screen.findByText("02.08.2026");

        fireEvent.submit(screen.getByRole("dialog"));

        expect(await screen.findByText("Invalid estimate")).toBeInTheDocument();
        expect(axios.post).not.toHaveBeenCalled();
    });

    test("shows per-value placeholders and disables external use unless current and next 1 are present", async () => {
        axios.get.mockImplementation(url => Promise.resolve({
            data: url === "http://backend/estimate/period-1"
                ? history
                : {
                    current: {eps: "1.9", date: "2026-08-10"},
                    next1: null,
                    next2: {eps: "2.1", date: null},
                    next3: {eps: null, date: "2027-05-10"},
                },
        }));

        render(<AddEstimateDialog {...createProps()}/>);

        expect(await screen.findByTestId("external-estimates"))
            .toHaveTextContent("External estimates: [ 1.90 (10.08.2026) | - | 2.10 (-) | - ]");
        expect(screen.getByRole("button", {name: "Use external estimates"})).toBeDisabled();
    });

    test("shows four dashes and disables external use when no estimates are imported", async () => {
        axios.get.mockImplementation(url => Promise.resolve({
            data: url === "http://backend/estimate/period-1" ? history : {},
        }));

        render(<AddEstimateDialog {...createProps()}/>);

        expect(await screen.findByTestId("external-estimates"))
            .toHaveTextContent("External estimates: [ - | - | - | - ]");
        expect(screen.getByRole("button", {name: "Use external estimates"})).toBeDisabled();
    });
});
