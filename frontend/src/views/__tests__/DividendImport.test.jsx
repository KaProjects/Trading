import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

const mockFormatError = jest.fn(() => ({title: "Request failed", message: "Import was not completed"}));

jest.mock("axios");
jest.mock("../../properties", () => ({backend: "http://backend"}));
jest.mock("../../service/FormattingService", () => ({
    formatError: (...args) => mockFormatError(...args),
}));

import {DIVIDEND_IMPORT_TEMPLATE, DividendImport} from "../DividendImport";

const validPreview = {
    valid: true,
    reordered: true,
    errors: [],
    rows: [
        {
            rowNumber: 3,
            date: "2026-01-10",
            ticker: "NVDA",
            dividend: "10",
            tax: "1.5",
            net: "8.5",
        },
        {
            rowNumber: 2,
            date: "2026-03-15",
            ticker: "AMD",
            dividend: "20.5",
            tax: "3.25",
            net: "17.25",
        },
    ],
};

describe("DividendImport", () => {
    beforeEach(() => {
        axios.post.mockReset();
        mockFormatError.mockClear();
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

    test("copies the dividend CSV template", async () => {
        render(<DividendImport/>);

        fireEvent.click(screen.getByRole("button", {name: "Copy CSV template"}));

        await waitFor(() => expect(navigator.clipboard.writeText)
            .toHaveBeenCalledWith(DIVIDEND_IMPORT_TEMPLATE));
        expect(await screen.findByRole("button", {name: "Template copied"})).toBeInTheDocument();
    });

    test("previews a file and imports normalized dividend rows", async () => {
        axios.post
            .mockResolvedValueOnce({data: validPreview})
            .mockResolvedValueOnce({data: validPreview});
        render(<DividendImport/>);

        fireEvent.drop(screen.getByRole("button", {name: "Drop dividend CSV file"}), {
            dataTransfer: {files: [{name: "dividends.csv", contents: DIVIDEND_IMPORT_TEMPLATE}]},
        });

        await waitFor(() => expect(axios.post).toHaveBeenNthCalledWith(
            1,
            "http://backend/dividend/import/preview",
            DIVIDEND_IMPORT_TEMPLATE,
            {headers: {"Content-Type": "text/csv"}},
        ));
        expect(screen.getByText(/rows were not chronological/i)).toBeInTheDocument();
        expect(screen.getByText("8.5")).toBeInTheDocument();
        expect(screen.getByText("17.25")).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Import 2 dividends"}));

        await waitFor(() => expect(axios.post).toHaveBeenNthCalledWith(
            2,
            "http://backend/dividend/import",
            {
                rows: validPreview.rows.map(({net, ...row}) => row),
            },
        ));
        expect(await screen.findByText("2 dividends imported successfully.")).toBeInTheDocument();
    });

    test("shows all validation errors and prevents import", async () => {
        axios.post.mockResolvedValue({
            data: {
                valid: false,
                reordered: false,
                rows: [validPreview.rows[0]],
                errors: [
                    {rowNumber: 2, field: "ticker", message: "company 'BAD' was not found"},
                    {rowNumber: 2, field: "tax", message: "must be a non-negative decimal"},
                ],
            },
        });
        render(<DividendImport/>);

        fireEvent.drop(screen.getByRole("button", {name: "Drop dividend CSV file"}), {
            dataTransfer: {files: [{name: "invalid.csv", contents: DIVIDEND_IMPORT_TEMPLATE}]},
        });

        expect(await screen.findByText("Row 2, ticker: company 'BAD' was not found")).toBeInTheDocument();
        expect(screen.getByText("Row 2, tax: must be a non-negative decimal")).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Import 1 dividends"})).toBeDisabled();
        expect(axios.post).toHaveBeenCalledTimes(1);
    });
});
