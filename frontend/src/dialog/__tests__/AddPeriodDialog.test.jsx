import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

const mockFormatError = jest.fn(() => ({title: "Create failed", message: "Period could not be saved"}));
const dialogTextFieldModule = {
    DialogTextField: ({id, label, value = "", onChange, validate, required = true, ...props}) => {
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
            </div>
        );
    },
};
const dialogDatePickerModule = {
    DialogDatePicker: ({id, label, value = "", onChange, validate, type = "date", required = true, ...props}) => {
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
            </div>
        );
    },
};

jest.mock("axios");
jest.mock("../../properties", () => ({
    backend: "http://backend",
}));
jest.mock("../../service/FormattingService", () => ({
    formatError: (...args) => mockFormatError(...args),
}));
jest.mock("../component/DialogTextField", () => dialogTextFieldModule);
jest.mock("../component/DialogDatePicker", () => dialogDatePickerModule);

import AddPeriodDialog from "../AddPeriodDialog";

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

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("http://backend/period", {
            companyId: "company-1",
            name: "24Q1",
            endingMonth: "2024-03",
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.handleClose).toHaveBeenCalled();
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
