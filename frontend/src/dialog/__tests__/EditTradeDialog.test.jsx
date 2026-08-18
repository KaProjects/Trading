import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

const mockFormatError = jest.fn(() => ({title: "Save failed", message: "Trade could not be saved"}));

jest.mock("axios");
jest.mock("../../properties", () => ({
    backend: "http://backend",
}));
jest.mock("../../service/FormattingService", () => ({
    formatError: (...args) => mockFormatError(...args),
}));
jest.mock("../component/DialogTextField", () => ({
    DialogTextField: ({id, label, value = "", onChange, validate}) => {
        const error = validate?.();
        return (
            <div>
                <label htmlFor={id}>{label}</label>
                <input id={id} aria-label={label} value={value} onChange={onChange} required/>
                {error && <span>{error}</span>}
            </div>
        );
    },
}));
jest.mock("../component/DialogDatePicker", () => ({
    DialogDatePicker: ({id, label, value = "", onChange}) => (
        <div>
            <label htmlFor={id}>{label}</label>
            <input id={id} aria-label={label} type="date" value={value} onChange={onChange} required/>
        </div>
    ),
}));

import {EditTradeDialog} from "../EditTradeDialog";

function createProps(trade) {
    return {
        openEditTrade: trade,
        setOpenEditTrade: jest.fn(),
        triggerRefresh: jest.fn(),
        portfolios: [
            {key: "PATRIA_MARGIN", name: "Patria - Margin"},
            {key: "REVOLUT_STANDARD", name: "Revolut - Standard"},
        ],
    };
}

describe("EditTradeDialog", () => {
    beforeEach(() => {
        axios.put.mockReset();
        mockFormatError.mockReset();
        mockFormatError.mockReturnValue({title: "Save failed", message: "Trade could not be saved"});
    });

    test("updates only purchase values for an active trade", async () => {
        axios.put.mockResolvedValue({});
        const props = createProps({
            id: "trade-1",
            active: true,
            company: {ticker: "NVDA"},
            purchaseDate: "2024-01-10",
            purchaseQuantity: 5,
            purchasePrice: 400.5,
            purchaseFees: 14.5,
            portfolio: {key: "PATRIA_MARGIN"},
            sellDate: "2026-08-18",
            sellPrice: 500,
            sellFees: 14.5,
        });

        render(<EditTradeDialog {...props}/>);

        expect(screen.getByLabelText("Purchase date")).toHaveValue("2024-01-10");
        expect(screen.getByLabelText("Quantity")).toHaveValue("5");
        expect(screen.queryByLabelText("Sell date")).not.toBeInTheDocument();

        fireEvent.change(screen.getByLabelText("Purchase date"), {target: {value: "2024-01-11"}});
        fireEvent.change(screen.getByLabelText("Quantity"), {target: {value: "6.25"}});
        fireEvent.click(screen.getByRole("button", {name: "Save"}));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("http://backend/trade/trade-1", {
            purchaseDate: "2024-01-11",
            quantity: "6.25",
            purchasePrice: "400.5",
            purchaseFees: "14.5",
            portfolio: "PATRIA_MARGIN",
            sellDate: null,
            sellPrice: null,
            sellFees: null,
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.setOpenEditTrade).toHaveBeenCalledWith(null);
    });

    test("updates purchase and sale values for a sold trade", async () => {
        axios.put.mockResolvedValue({});
        const props = createProps({
            id: "trade-2",
            active: false,
            company: {ticker: "SHELL"},
            purchaseDate: "2023-03-15",
            purchaseQuantity: 8,
            purchasePrice: 28,
            purchaseFees: 10,
            sellDate: "2024-02-20",
            sellPrice: 35.5,
            sellFees: 12,
        });

        render(<EditTradeDialog {...props}/>);

        expect(screen.getByLabelText("Sell date")).toHaveValue("2024-02-20");
        fireEvent.change(screen.getByLabelText("Sell fees"), {target: {value: "13.25"}});
        fireEvent.mouseDown(screen.getByRole("combobox", {name: "Portfolio"}));
        fireEvent.click(screen.getByRole("option", {name: "Revolut - Standard"}));
        fireEvent.click(screen.getByRole("button", {name: "Save"}));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("http://backend/trade/trade-2", {
            purchaseDate: "2023-03-15",
            quantity: "8",
            purchasePrice: "28",
            purchaseFees: "10",
            portfolio: "REVOLUT_STANDARD",
            sellDate: "2024-02-20",
            sellPrice: "35.5",
            sellFees: "13.25",
        }));
    });

    test("clears a failed-request alert when a field is edited", async () => {
        axios.put.mockRejectedValue(new Error("boom"));
        const props = createProps({
            id: "trade-3",
            active: true,
            company: {ticker: "AMD"},
            purchaseDate: "2024-01-10",
            purchaseQuantity: 5,
            purchasePrice: 100,
            purchaseFees: 1,
        });

        render(<EditTradeDialog {...props}/>);

        await waitFor(() => expect(screen.getByLabelText("Quantity")).toHaveValue("5"));
        fireEvent.click(screen.getByRole("button", {name: "Save"}));
        await waitFor(() => expect(screen.getByText("Trade could not be saved")).toBeInTheDocument());

        fireEvent.change(screen.getByLabelText("Quantity"), {target: {value: "6"}});

        expect(screen.queryByText("Trade could not be saved")).not.toBeInTheDocument();
    });
});
