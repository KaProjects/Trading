import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

const mockFormatError = jest.fn(() => ({title: "Request failed", message: "Import was not completed"}));

jest.mock("axios");
jest.mock("../../properties", () => ({
    backend: "http://backend",
}));
jest.mock("../../service/FormattingService", () => ({
    formatError: (...args) => mockFormatError(...args),
}));

import {TRADE_IMPORT_TEMPLATE, TradeImport} from "../TradeImport";

const validPreview = {
    valid: true,
    reordered: true,
    errors: [],
    rows: [
        {
            rowNumber: 3,
            date: "2026-01-10",
            type: "BUY",
            ticker: "NVDA",
            quantity: "3",
            price: "145.5",
            fees: "4.95",
            portfolio: "PATRIA_STANDARD",
            allocations: [],
            remainingQuantity: null,
        },
        {
            rowNumber: 2,
            date: "2026-04-01",
            type: "SELL",
            ticker: "NVDA",
            quantity: "2",
            price: "180",
            fees: "6.95",
            portfolio: "PATRIA_STANDARD",
            allocations: [
                {source: "CSV row 3", purchaseDate: "2026-01-10", quantity: "2"},
            ],
            remainingQuantity: "1",
        },
    ],
};

describe("TradeImport", () => {
    beforeEach(() => {
        axios.post.mockReset();
        mockFormatError.mockClear();
        mockFormatError.mockImplementation(() => ({
            title: "Request failed",
            message: "Import was not completed",
        }));

        global.FileReader = class {
            readAsText(file) {
                this.result = file.contents;
                Promise.resolve().then(() => this.onload());
            }
        };

        Object.defineProperty(navigator, "clipboard", {
            configurable: true,
            value: {writeText: jest.fn().mockResolvedValue(undefined)},
        });
    });

    test("copies a ready-to-use CSV template", async () => {
        render(<TradeImport/>);

        fireEvent.click(screen.getByRole("button", {name: "Copy CSV template"}));

        await waitFor(() => expect(navigator.clipboard.writeText).toHaveBeenCalledWith(TRADE_IMPORT_TEMPLATE));
        expect(await screen.findByRole("button", {name: "Template copied"})).toBeInTheDocument();
    });

    test("previews a dropped file and imports only normalized server rows", async () => {
        axios.post
            .mockResolvedValueOnce({data: validPreview})
            .mockResolvedValueOnce({data: validPreview});
        render(<TradeImport/>);
        const file = {name: "trades.csv", contents: TRADE_IMPORT_TEMPLATE};

        fireEvent.drop(screen.getByRole("button", {name: "Drop trade CSV file"}), {
            dataTransfer: {files: [file]},
        });

        await waitFor(() => expect(axios.post).toHaveBeenNthCalledWith(
            1,
            "http://backend/trade/import/preview",
            TRADE_IMPORT_TEMPLATE,
            {headers: {"Content-Type": "text/csv"}},
        ));
        expect(screen.getByText(/rows were not chronological/i)).toBeInTheDocument();
        expect(screen.getByText("CSV row 3: 2 (2026-01-10)")).toBeInTheDocument();
        expect(screen.getByText("1")).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Import 2 trades"}));

        await waitFor(() => expect(axios.post).toHaveBeenNthCalledWith(2, "http://backend/trade/import", {
            rows: validPreview.rows.map(({allocations, remainingQuantity, ...row}) => row),
        }));
        expect(await screen.findByText("2 trades imported successfully.")).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Import 2 trades"})).toBeDisabled();
    });

    test("shows every preview error and prevents confirmation", async () => {
        axios.post.mockResolvedValue({
            data: {
                valid: false,
                reordered: false,
                rows: [{...validPreview.rows[1], allocations: [], remainingQuantity: "2"}],
                errors: [
                    {rowNumber: 2, field: "ticker", message: "company 'BAD' was not found"},
                    {rowNumber: 2, field: "quantity", message: "cannot sell 4; only 2 is available"},
                ],
            },
        });
        const {container} = render(<TradeImport/>);
        const file = {name: "invalid.csv", contents: TRADE_IMPORT_TEMPLATE};

        fireEvent.change(container.querySelector('input[type="file"]'), {target: {files: [file]}});

        expect(await screen.findByText("Row 2, ticker: company 'BAD' was not found")).toBeInTheDocument();
        expect(screen.getByText("Row 2, quantity: cannot sell 4; only 2 is available")).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Import 1 trades"})).toBeDisabled();
        expect(axios.post).toHaveBeenCalledTimes(1);
    });

    test("replaces the preview when commit-time revalidation detects a conflict", async () => {
        const conflict = {
            ...validPreview,
            valid: false,
            errors: [{rowNumber: 2, field: "quantity", message: "availability changed"}],
        };
        axios.post
            .mockResolvedValueOnce({data: validPreview})
            .mockRejectedValueOnce({response: {data: conflict}});
        render(<TradeImport/>);

        fireEvent.drop(screen.getByRole("button", {name: "Drop trade CSV file"}), {
            dataTransfer: {files: [{name: "trades.csv", contents: TRADE_IMPORT_TEMPLATE}]},
        });
        fireEvent.click(await screen.findByRole("button", {name: "Import 2 trades"}));

        expect(await screen.findByText("Row 2, quantity: availability changed")).toBeInTheDocument();
        await waitFor(() => expect(mockFormatError).toHaveBeenCalled());
        expect(mockFormatError.mock.results.at(-1).value).toEqual({
            title: "Request failed",
            message: "Import was not completed",
        });
        expect(await screen.findByText("Import was not completed")).toBeInTheDocument();
    });
});
