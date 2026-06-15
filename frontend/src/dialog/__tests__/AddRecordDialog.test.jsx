import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

const mockFormatError = jest.fn(() => ({title: "Create failed", message: "Record could not be saved"}));
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

import {AddRecordDialog} from "../AddRecordDialog";

function createProps(overrides = {}) {
    return {
        companyId: "company-1",
        open: true,
        handleClose: jest.fn(),
        triggerRefresh: jest.fn(),
        indicators: {
            datetime: "2024-03-15T14:00:00Z",
            price: 120.5,
            ttm: {
                marketCapToRevenues: 6.7,
                marketCapToGrossProfit: 8.1,
                marketCapToOperatingIncome: 10.2,
                marketCapToNetIncome: 12.3,
                dividendYield: 1.4,
            },
        },
        assets: {
            aggregate: {
                quantity: 4,
                purchasePrice: 100.25,
            },
        },
        ...overrides,
    };
}

describe("AddRecordDialog", () => {
    beforeEach(() => {
        axios.post.mockReset();
        mockFormatError.mockClear();
    });

    test("prefills values from indicators and assets and normalizes blank optional fields on submit", async () => {
        axios.post.mockResolvedValue({});

        const props = createProps();

        render(<AddRecordDialog {...props}/>);

        expect(screen.getByTestId("trader-record-date")).toHaveValue("2024-03-15");
        expect(screen.getByLabelText("Price")).toHaveValue("120.5");
        expect(screen.getByLabelText("PS")).toHaveValue("6.7");
        expect(screen.getByLabelText("assets quantity sum")).toHaveValue("4");

        fireEvent.change(screen.getByLabelText("Title"), {target: {value: "Quarter update"}});
        fireEvent.change(screen.getByLabelText("PS"), {target: {value: ""}});
        fireEvent.change(screen.getByLabelText("PG"), {target: {value: ""}});
        fireEvent.change(screen.getByLabelText("PO"), {target: {value: ""}});
        fireEvent.change(screen.getByLabelText("PE"), {target: {value: ""}});
        fireEvent.change(screen.getByLabelText("DY"), {target: {value: ""}});
        fireEvent.change(screen.getByLabelText("assets quantity sum"), {target: {value: ""}});
        fireEvent.change(screen.getByLabelText("assets avg purchase price"), {target: {value: ""}});
        fireEvent.change(screen.getByLabelText("price targets"), {target: {value: ""}});
        fireEvent.click(screen.getByText("Create"));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("http://backend/record", {
            companyId: "company-1",
            title: "Quarter update",
            date: "2024-03-15",
            price: "120.5",
            priceToRevenues: null,
            priceToGrossProfit: null,
            priceToOperatingIncome: null,
            priceToNetIncome: null,
            dividendYield: null,
            sumAssetQuantity: null,
            avgAssetPrice: null,
            targets: null,
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.handleClose).toHaveBeenCalled();
    });

    test("shows formatted error when create fails", async () => {
        axios.post.mockRejectedValue(new Error("boom"));

        const props = createProps({
            indicators: null,
            assets: {},
        });

        render(<AddRecordDialog {...props}/>);

        fireEvent.change(screen.getByTestId("trader-record-date"), {target: {value: "2024-04-01"}});
        fireEvent.change(screen.getByLabelText("Title"), {target: {value: "Snapshot"}});
        fireEvent.change(screen.getByLabelText("Price"), {target: {value: "99.9"}});
        fireEvent.click(screen.getByText("Create"));

        await waitFor(() => expect(mockFormatError).toHaveBeenCalled());
        expect(props.triggerRefresh).not.toHaveBeenCalled();
        expect(props.handleClose).not.toHaveBeenCalled();
    });
});
