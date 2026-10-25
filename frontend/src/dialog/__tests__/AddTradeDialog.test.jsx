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
    backend: "/api",
}));
jest.mock("../../service/FormattingService", () => ({
    formatError: (...args) => mockFormatError(...args),
}));
jest.mock("../component/DialogTextField", () => dialogTextFieldModule);
jest.mock("../component/DialogDatePicker", () => dialogDatePickerModule);

import {AddTradeDialog} from "../AddTradeDialog";

function selectOption(index, optionText) {
    fireEvent.mouseDown(screen.getAllByRole("combobox")[index]);
    fireEvent.click(screen.getByRole("option", {name: optionText}));
}

function createProps(overrides = {}) {
    const company = {id: "company-1", ticker: "NVDA"};

    return {
        openAddTrade: true,
        setOpenAddTrade: jest.fn(),
        triggerRefresh: jest.fn(),
        companySelectorValue: company,
        companyLists: {recent: [company], all: [company]},
        portfolios: [
            {key: "PATRIA_STANDARD", name: "Patria - Standard", abbreviation: "P"},
            {key: "REVOLUT_CFD", name: "Revolut - CFD", abbreviation: "Rd"},
        ],
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

        selectOption(1, "Revolut - CFD");
        fireEvent.change(screen.getByTestId("trader-trade-date"), {target: {value: "2024-03-20"}});
        fireEvent.change(screen.getByLabelText("Quantity"), {target: {value: "5"}});
        fireEvent.change(screen.getByLabelText("Price"), {target: {value: "800.15"}});
        fireEvent.change(screen.getByLabelText("Fees"), {target: {value: "14.50"}});
        fireEvent.click(screen.getByText("Create"));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("/api/trade", {
            companyId: "company-1",
            date: "2024-03-20",
            price: "800.15",
            quantity: "5",
            fees: "14.50",
            portfolio: "REVOLUT_CFD",
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.setOpenAddTrade).toHaveBeenCalledWith(false);
    });

    test("shows formatted error when create fails", async () => {
        axios.post.mockRejectedValue(new Error("boom"));

        const props = createProps();

        render(<AddTradeDialog {...props}/>);

        selectOption(1, "Patria - Standard");
        fireEvent.change(screen.getByTestId("trader-trade-date"), {target: {value: "2024-03-20"}});
        fireEvent.change(screen.getByLabelText("Quantity"), {target: {value: "5"}});
        fireEvent.change(screen.getByLabelText("Price"), {target: {value: "800.15"}});
        fireEvent.change(screen.getByLabelText("Fees"), {target: {value: "14.50"}});
        fireEvent.click(screen.getByText("Create"));

        await waitFor(() => expect(mockFormatError).toHaveBeenCalled());
        expect(props.triggerRefresh).not.toHaveBeenCalled();
        expect(props.setOpenAddTrade).not.toHaveBeenCalled();
    });

    test("allows creating a trade without a portfolio", async () => {
        axios.post.mockResolvedValue({});
        const props = createProps();

        render(<AddTradeDialog {...props}/>);

        fireEvent.mouseDown(screen.getAllByRole("combobox")[1]);
        expect(screen.getByRole("option", {name: ""})).toHaveAttribute("data-value", "");
        fireEvent.click(screen.getByRole("option", {name: ""}));
        fireEvent.change(screen.getByTestId("trader-trade-date"), {target: {value: "2024-03-20"}});
        fireEvent.change(screen.getByLabelText("Quantity"), {target: {value: "5"}});
        fireEvent.change(screen.getByLabelText("Price"), {target: {value: "800.15"}});
        fireEvent.change(screen.getByLabelText("Fees"), {target: {value: "14.50"}});
        fireEvent.click(screen.getByText("Create"));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("/api/trade", {
            companyId: "company-1",
            date: "2024-03-20",
            price: "800.15",
            quantity: "5",
            fees: "14.50",
            portfolio: null,
        }));
    });

    test("uses recent companies by default and can switch to another company list", () => {
        const selected = {id: "company-1", ticker: "NVDA"};
        const recent = {id: "company-2", ticker: "CEZ"};
        const allOnly = {id: "company-3", ticker: "AAPL"};

        render(<AddTradeDialog {...createProps({
            companySelectorValue: selected,
            companyLists: {recent: [recent], all: [selected, recent, allOnly]},
        })}/>);

        fireEvent.mouseDown(screen.getAllByRole("combobox")[0]);

        expect(screen.getByRole("button", {name: "Company list Recent"})).toBeInTheDocument();
        expect(screen.getByRole("option", {name: "CEZ"})).toBeInTheDocument();
        expect(screen.queryByRole("option", {name: "AAPL"})).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Company list Recent"}));
        fireEvent.click(screen.getByRole("button", {name: "Use company list All"}));

        expect(screen.getByRole("option", {name: "NVDA"})).toBeInTheDocument();
        expect(screen.getByRole("option", {name: "CEZ"})).toBeInTheDocument();
        expect(screen.getByRole("option", {name: "AAPL"})).toBeInTheDocument();
    });
});
