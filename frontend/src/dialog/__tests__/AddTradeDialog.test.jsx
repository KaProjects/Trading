import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

const mockFormatError = jest.fn(() => ({title: "Create failed", message: "Trade could not be saved"}));
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

import AddTradeDialog from "../AddTradeDialog";

function createProps(overrides = {}) {
    const company = {id: "company-1", ticker: "NVDA"};

    return {
        openAddTrade: true,
        setOpenAddTrade: jest.fn(),
        triggerRefresh: jest.fn(),
        companySelectorValue: company,
        companies: [company],
        ...overrides,
    };
}

describe("AddTradeDialog", () => {
    beforeEach(() => {
        axios.post.mockReset();
        mockFormatError.mockClear();
    });

    test("submits a trade and closes the dialog on success", async () => {
        axios.post.mockResolvedValue({});

        const props = createProps();

        render(<AddTradeDialog {...props}/>);

        fireEvent.change(screen.getByTestId("trader-trade-date"), {target: {value: "2024-03-20"}});
        fireEvent.change(screen.getByLabelText("Quantity"), {target: {value: "5"}});
        fireEvent.change(screen.getByLabelText("Price"), {target: {value: "800.15"}});
        fireEvent.change(screen.getByLabelText("Fees"), {target: {value: "14.50"}});
        fireEvent.click(screen.getByText("Create"));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("http://backend/trade", {
            companyId: "company-1",
            date: "2024-03-20",
            price: "800.15",
            quantity: "5",
            fees: "14.50",
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.setOpenAddTrade).toHaveBeenCalledWith(false);
    });

    test("shows formatted error when create fails", async () => {
        axios.post.mockRejectedValue(new Error("boom"));

        const props = createProps();

        render(<AddTradeDialog {...props}/>);

        fireEvent.change(screen.getByTestId("trader-trade-date"), {target: {value: "2024-03-20"}});
        fireEvent.change(screen.getByLabelText("Quantity"), {target: {value: "5"}});
        fireEvent.change(screen.getByLabelText("Price"), {target: {value: "800.15"}});
        fireEvent.change(screen.getByLabelText("Fees"), {target: {value: "14.50"}});
        fireEvent.click(screen.getByText("Create"));

        await waitFor(() => expect(mockFormatError).toHaveBeenCalled());
        expect(props.triggerRefresh).not.toHaveBeenCalled();
        expect(props.setOpenAddTrade).not.toHaveBeenCalled();
    });
});
