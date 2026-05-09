import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

const mockFormatError = jest.fn(() => ({title: "Sell failed", message: "Trade could not be sold"}));
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

import SellTradeDialog from "../SellTradeDialog";

function createProps(overrides = {}) {
    const company = {id: "company-1", ticker: "NVDA"};

    return {
        openSellTrade: true,
        setOpenSellTrade: jest.fn(),
        triggerRefresh: jest.fn(),
        companySelectorValue: company,
        companies: [company],
        ...overrides,
    };
}

describe("SellTradeDialog", () => {
    beforeEach(() => {
        axios.get.mockReset();
        axios.put.mockReset();
        mockFormatError.mockClear();
    });

    test("loads active trades on open and submits only valid positive sell quantities", async () => {
        axios.get.mockResolvedValue({
            data: {
                trades: [
                    {
                        id: "trade-1",
                        purchaseDate: "2024-01-01",
                        purchaseQuantity: 5,
                        purchasePrice: 100,
                        purchaseFees: 1,
                        purchaseTotal: 501,
                        currency: "$",
                    },
                    {
                        id: "trade-2",
                        purchaseDate: "2024-01-15",
                        purchaseQuantity: 3,
                        purchasePrice: 120,
                        purchaseFees: 1,
                        purchaseTotal: 361,
                        currency: "$",
                    },
                ],
            },
        });
        axios.put.mockResolvedValue({});

        const props = createProps();

        render(<SellTradeDialog {...props}/>);

        await waitFor(() => expect(axios.get).toHaveBeenCalledWith("http://backend/trade?active=true&companyId=company-1"));
        expect(await screen.findByText("2024-01-01")).toBeInTheDocument();

        fireEvent.change(screen.getByTestId("trader-sell-trade-date"), {target: {value: "2024-04-15"}});
        fireEvent.change(screen.getByLabelText("Price"), {target: {value: "140"}});
        fireEvent.change(screen.getByLabelText("Fees"), {target: {value: "5"}});

        const quantityInputs = screen.getAllByTestId("trader-sell-trade-quantity");
        fireEvent.change(quantityInputs[0], {target: {value: "2"}});
        fireEvent.change(quantityInputs[1], {target: {value: "4"}});

        expect(screen.getByText("bigger than owned quantity")).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Sell"}));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("http://backend/trade", {
            companyId: "company-1",
            date: "2024-04-15",
            price: "140",
            fees: "5",
            trades: [
                {tradeId: "trade-1", quantity: 2},
            ],
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.setOpenSellTrade).toHaveBeenCalledWith(false);
    });

    test("shows formatted error when sell fails", async () => {
        axios.get.mockResolvedValue({
            data: {
                trades: [
                    {
                        id: "trade-1",
                        purchaseDate: "2024-01-01",
                        purchaseQuantity: 5,
                        purchasePrice: 100,
                        purchaseFees: 1,
                        purchaseTotal: 501,
                        currency: "$",
                    },
                ],
            },
        });
        axios.put.mockRejectedValue(new Error("boom"));

        const props = createProps();

        render(<SellTradeDialog {...props}/>);

        await screen.findByText("2024-01-01");

        fireEvent.change(screen.getByTestId("trader-sell-trade-date"), {target: {value: "2024-04-15"}});
        fireEvent.change(screen.getByLabelText("Price"), {target: {value: "140"}});
        fireEvent.change(screen.getByLabelText("Fees"), {target: {value: "5"}});
        fireEvent.change(screen.getByTestId("trader-sell-trade-quantity"), {target: {value: "1"}});
        fireEvent.click(screen.getByRole("button", {name: "Sell"}));

        await waitFor(() => expect(mockFormatError).toHaveBeenCalled());
        expect(props.triggerRefresh).not.toHaveBeenCalled();
        expect(props.setOpenSellTrade).not.toHaveBeenCalled();
    });
});
