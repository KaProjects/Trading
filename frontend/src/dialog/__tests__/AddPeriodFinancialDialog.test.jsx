import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

const mockFormatError = jest.fn(() => ({title: "Save failed", message: "Financial data could not be saved"}));
const mockGetFinancial = jest.fn();
const mockGetQuote = jest.fn();
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
    ...jest.requireActual("../../service/FormattingService"),
    formatError: (...args) => mockFormatError(...args),
}));
jest.mock("../../service/PolygonIoService", () => ({
    getFinancial: (...args) => mockGetFinancial(...args),
    getQuote: (...args) => mockGetQuote(...args),
}));
jest.mock("../component/DialogTextField", () => dialogTextFieldModule);
jest.mock("../component/DialogDatePicker", () => dialogDatePickerModule);

import AddPeriodFinancialDialog from "../AddPeriodFinancialDialog";

const polygonFinancial = {
    financials: {
        income_statement: {
            basic_average_shares: {value: 10000000},
            revenues: {value: 20000000},
            gross_profit: {value: 30000000},
            operating_income_loss: {value: 40000000},
            net_income_loss: {value: 50000000},
        },
    },
};

function createProps(overrides = {}) {
    return {
        open: true,
        handleClose: jest.fn(),
        triggerRefresh: jest.fn(),
        company: {
            ticker: "NVDA",
        },
        period: {
            id: "period-1",
            name: {
                year: "2024",
                type: "Q1",
            },
            reportDate: "2024-02-15",
            previousReportDate: "2023-11-15",
            cachedData: {
                shares: "1",
                revenue: "2",
                grossProfit: "3",
                operatingIncome: "4",
                netIncome: "5",
                dividend: "0.5",
                priceHigh: "125",
                priceLow: "95",
                reportDate: "2024-02-15",
                previousReportDate: "2023-11-15",
            },
        },
        ...overrides,
    };
}

describe("AddPeriodFinancialDialog", () => {
    beforeEach(() => {
        axios.put.mockReset();
        mockFormatError.mockClear();
        mockGetFinancial.mockReset();
        mockGetQuote.mockReset();
        mockGetFinancial.mockResolvedValue(polygonFinancial);
        mockGetQuote.mockResolvedValue({h: 140.25, l: 90.75});
    });

    test("loads cached values, fetches suggestions, and submits updated financial data", async () => {
        axios.put.mockResolvedValue({});

        const props = createProps();

        render(<AddPeriodFinancialDialog {...props}/>);

        expect(screen.getByLabelText("Shares (in Millions)")).toHaveValue("1");
        expect(screen.getByLabelText("Revenue (in Millions)")).toHaveValue("2");

        await waitFor(() => expect(mockGetFinancial).toHaveBeenCalledWith("NVDA", "2024", "Q1"));
        await waitFor(() => expect(mockGetQuote).toHaveBeenCalledWith("NVDA", "2023-11-16", "2024-02-14"));

        fireEvent.click(await screen.findByRole("button", {name: "<< 10"}));
        expect(screen.getByLabelText("Shares (in Millions)")).toHaveValue("10");

        fireEvent.click(screen.getByRole("button", {name: "Create"}));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("http://backend/period/financial", {
            id: "period-1",
            reportDate: "2024-02-15",
            priceLow: "95",
            priceHigh: "125",
            shares: "10",
            revenue: "2",
            grossProfit: "3",
            operatingIncome: "4",
            netIncome: "5",
            dividend: "0.5",
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.handleClose).toHaveBeenCalled();
    });

    test("shows formatted error when submit fails", async () => {
        axios.put.mockRejectedValue(new Error("boom"));

        const props = createProps();

        render(<AddPeriodFinancialDialog {...props}/>);

        await screen.findByRole("button", {name: "<< 10"});
        fireEvent.click(screen.getByRole("button", {name: "Create"}));

        await waitFor(() => expect(mockFormatError).toHaveBeenCalled());
        expect(props.triggerRefresh).not.toHaveBeenCalled();
        expect(props.handleClose).not.toHaveBeenCalled();
    });
});
