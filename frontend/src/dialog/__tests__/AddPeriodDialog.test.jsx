import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

const mockFormatError = jest.fn(() => ({title: "Create failed", message: "Period could not be saved"}));
const dialogTextFieldModule = {
    DialogTextField: ({id, label, value = "", onChange, validate, warning, required = true, ...props}) => {
        const error = validate ? validate() : "";

        return (
            <div>
                <label htmlFor={id}>{label || id}</label>
                <input
                    id={id}
                    aria-label={label || id}
                    data-testid={id}
                    value={value ?? ""}
                    onChange={onChange}
                    required={required}
                    {...props}
                />
                {error && <span>{error}</span>}
                {!error && warning && <span>{warning}</span>}
            </div>
        );
    },
};
const dialogDatePickerModule = {
    DialogDatePicker: ({id, label, value = "", onChange, validate, warning, type = "date", required = true, ...props}) => {
        const error = validate ? validate() : "";

        return (
            <div>
                <label htmlFor={id}>{label || id}</label>
                <input
                    id={id}
                    aria-label={label || id}
                    data-testid={id}
                    type={type}
                    value={value ?? ""}
                    onChange={onChange}
                    required={required}
                    {...props}
                />
                {error && <span>{error}</span>}
                {!error && warning && <span>{warning}</span>}
            </div>
        );
    },
};

jest.mock("axios");
jest.mock("../../properties", () => ({
    backend: "/api",
}));
jest.mock("../../service/FormattingService", () => ({
    ...jest.requireActual("../../service/FormattingService"),
    formatError: (...args) => mockFormatError(...args),
}));
jest.mock("../component/DialogTextField", () => dialogTextFieldModule);
jest.mock("../component/DialogDatePicker", () => dialogDatePickerModule);

import {AddPeriodDialog} from "../AddPeriodDialog";

function createProps(overrides = {}) {
    return {
        companyId: "company-1",
        open: true,
        handleClose: jest.fn(),
        triggerRefresh: jest.fn(),
        ...overrides,
    };
}

describe("AddPeriodDialog", () => {
    beforeEach(() => {
        axios.post.mockReset();
        mockFormatError.mockClear();
    });

    test("submits a period and closes the dialog on success", async () => {
        axios.post.mockResolvedValue({});

        const props = createProps();

        render(<AddPeriodDialog {...props}/>);

        fireEvent.change(screen.getByLabelText("Name"), {target: {value: "24Q1"}});
        fireEvent.change(screen.getByTestId("trader-period-end-month"), {target: {value: "2024-03"}});
        fireEvent.click(screen.getByText("Create"));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("/api/period", {
            companyId: "company-1",
            name: "24Q1",
            endingMonth: "2024-03",
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.handleClose).toHaveBeenCalled();
    });

    test("prefills the consecutive period from the latest one", () => {
        render(<AddPeriodDialog {...createProps({
            periods: [{name: {year: "2025", type: "Q3"}, endingMonth: "2025-07"}],
        })}/>);

        expect(screen.getByLabelText("Name")).toHaveValue("25Q4");
        expect(screen.getByTestId("trader-period-end-month")).toHaveValue("2025-10");
    });

    test("prefills the next fiscal year after the last quarter", () => {
        render(<AddPeriodDialog {...createProps({
            periods: [{name: {year: "2026", type: "Q4"}, endingMonth: "2026-01"}],
        })}/>);

        expect(screen.getByLabelText("Name")).toHaveValue("27Q1");
        expect(screen.getByTestId("trader-period-end-month")).toHaveValue("2026-04");
    });

    test("leaves the fields empty for a company without periods", () => {
        render(<AddPeriodDialog {...createProps()}/>);

        expect(screen.getByLabelText("Name")).toHaveValue("");
        expect(screen.getByTestId("trader-period-end-month")).toHaveValue("");
    });

    test("submits the prefilled period without further edits", async () => {
        axios.post.mockResolvedValue({});
        const props = createProps({
            periods: [{name: {year: "2025", type: "H1"}, endingMonth: "2025-06"}],
        });

        render(<AddPeriodDialog {...props}/>);
        fireEvent.click(screen.getByText("Create"));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("/api/period", {
            companyId: "company-1",
            name: "25H2",
            endingMonth: "2025-12",
        }));
    });

    test("shows an error for a period name that already exists", () => {
        const props = createProps({
            periods: [
                {name: {year: "2025", type: "Q3"}, endingMonth: "2025-07"},
                {name: {year: "2025", type: "Q2"}, endingMonth: "2025-04"},
            ],
        });

        render(<AddPeriodDialog {...props}/>);

        fireEvent.change(screen.getByLabelText("Name"), {target: {value: "25Q2"}});

        expect(screen.getByText("period already exists for this company")).toBeInTheDocument();
        expect(screen.getByText("Create").closest("button")).not.toBeDisabled();
    });

    test("shows an error for an unsupported period type", () => {
        render(<AddPeriodDialog {...createProps()}/>);

        fireEvent.change(screen.getByLabelText("Name"), {target: {value: "25Q9"}});

        expect(screen.getByText("expected Q1-Q4, H1, H2 or FY, e.g. 25FY, 25Q1, ...")).toBeInTheDocument();
        expect(screen.getByText("Create").closest("button")).not.toBeDisabled();
    });

    test("warns without blocking when the period does not follow the latest one", () => {
        render(<AddPeriodDialog {...createProps({
            periods: [{name: {year: "2025", type: "Q3"}, endingMonth: "2025-07"}],
        })}/>);

        fireEvent.change(screen.getByLabelText("Name"), {target: {value: "26Q2"}});

        expect(screen.getByText("does not follow 25Q3, expected 25Q4")).toBeInTheDocument();
        expect(screen.getByText("Create").closest("button")).not.toBeDisabled();
    });

    test("warns without blocking when a consecutive period has an unexpected ending month", async () => {
        axios.post.mockResolvedValue({});
        const props = createProps({
            periods: [{name: {year: "2026", type: "Q4"}, endingMonth: "2026-01"}],
        });

        render(<AddPeriodDialog {...props}/>);

        expect(screen.queryByText("expected 2026-04 for 27Q1")).not.toBeInTheDocument();
        fireEvent.change(screen.getByTestId("trader-period-end-month"), {target: {value: "2027-04"}});

        expect(screen.getByText("expected 2026-04 for 27Q1")).toBeInTheDocument();
        expect(screen.getByText("Create").closest("button")).not.toBeDisabled();

        fireEvent.click(screen.getByText("Create"));
        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("/api/period", {
            companyId: "company-1",
            name: "27Q1",
            endingMonth: "2027-04",
        }));
    });

    test("shows formatted error when create fails", async () => {
        axios.post.mockRejectedValue(new Error("boom"));

        const props = createProps();

        render(<AddPeriodDialog {...props}/>);

        fireEvent.change(screen.getByLabelText("Name"), {target: {value: "24Q1"}});
        fireEvent.change(screen.getByTestId("trader-period-end-month"), {target: {value: "2024-03"}});
        fireEvent.click(screen.getByText("Create"));

        await waitFor(() => expect(mockFormatError).toHaveBeenCalled());
        expect(props.triggerRefresh).not.toHaveBeenCalled();
        expect(props.handleClose).not.toHaveBeenCalled();
    });
});
