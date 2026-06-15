import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

const mockFormatError = jest.fn(() => ({title: "Create failed", message: "Dividend could not be saved"}));
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

import {AddDividendDialog} from "../AddDividendDialog";

function createProps(overrides = {}) {
    const company = {id: "company-1", ticker: "NVDA"};

    return {
        openAddDividend: true,
        setOpenAddDividend: jest.fn(),
        triggerRefresh: jest.fn(),
        companySelectorValue: company,
        companies: [company],
        ...overrides,
    };
}

describe("AddDividendDialog", () => {
    beforeEach(() => {
        axios.post.mockReset();
        mockFormatError.mockClear();
    });

    test("submits a dividend and closes the dialog on success", async () => {
        axios.post.mockResolvedValue({});

        const props = createProps();

        render(<AddDividendDialog {...props}/>);

        fireEvent.change(screen.getByTestId("trader-dividend-date"), {target: {value: "2024-05-15"}});
        fireEvent.change(screen.getByLabelText("Dividend"), {target: {value: "15.25"}});
        fireEvent.change(screen.getByLabelText("Tax"), {target: {value: "2.75"}});
        fireEvent.click(screen.getByText("Create"));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("http://backend/dividend", {
            companyId: "company-1",
            date: "2024-05-15",
            dividend: "15.25",
            tax: "2.75",
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.setOpenAddDividend).toHaveBeenCalledWith(false);
    });

    test("shows formatted error when create fails", async () => {
        axios.post.mockRejectedValue(new Error("boom"));

        const props = createProps();

        render(<AddDividendDialog {...props}/>);

        fireEvent.change(screen.getByTestId("trader-dividend-date"), {target: {value: "2024-05-15"}});
        fireEvent.change(screen.getByLabelText("Dividend"), {target: {value: "15.25"}});
        fireEvent.change(screen.getByLabelText("Tax"), {target: {value: "2.75"}});
        fireEvent.click(screen.getByText("Create"));

        await waitFor(() => expect(mockFormatError).toHaveBeenCalled());
        expect(props.triggerRefresh).not.toHaveBeenCalled();
        expect(props.setOpenAddDividend).not.toHaveBeenCalled();
    });
});
