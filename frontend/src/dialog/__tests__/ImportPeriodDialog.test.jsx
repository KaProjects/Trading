import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

const mockFormatError = jest.fn(() => ({title: "Import failed", message: "Period could not be imported"}));
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

import {ImportPeriodDialog} from "../ImportPeriodDialog";

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
            id: "company-1",
            ticker: "NVDA",
        },
        periods: [
            {
                name: "24Q1",
                isReported: true,
                endingMonth: "2024-03",
                previousReportDate: "2023-11-15",
                reportDate: "2024-02-15",
                shares: "",
                revenue: "",
                grossProfit: "",
                operatingIncome: "",
                netIncome: "",
                dividend: "",
                priceHigh: "",
                priceLow: "",
            },
            {
                name: "24Q2",
                isReported: false,
                endingMonth: "2024-06",
            },
        ],
        ...overrides,
    };
}

describe("ImportPeriodDialog", () => {
    beforeEach(() => {
        axios.post.mockReset();
        mockFormatError.mockClear();
        mockGetFinancial.mockReset();
        mockGetQuote.mockReset();
        mockGetFinancial.mockResolvedValue(polygonFinancial);
        mockGetQuote.mockResolvedValue({h: 140.25, l: 90.75});
    });

    test("imports a reported period after applying a suggestion", async () => {
        axios.post.mockResolvedValue({});

        const props = createProps();

        render(<ImportPeriodDialog {...props}/>);

        fireEvent.click(screen.getByText("24Q1"));

        await waitFor(() => expect(mockGetFinancial).toHaveBeenCalledWith("NVDA", "2024", "Q1"));
        await waitFor(() => expect(mockGetQuote).toHaveBeenCalledWith("NVDA", "2023-11-15", "2024-02-15"));

        fireEvent.click(await screen.findByRole("button", {name: "<< 10"}));
        expect(screen.getByLabelText("Shares (in Millions)")).toHaveValue("10");

        fireEvent.click(screen.getByRole("button", {name: "Create"}));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("http://backend/period/import", {
            companyId: "company-1",
            name: "24Q1",
            isReported: true,
            endingMonth: "2024-03",
            previousReportDate: "2023-11-15",
            reportDate: "2024-02-15",
            shares: "10",
            revenue: "",
            grossProfit: "",
            operatingIncome: "",
            netIncome: "",
            dividend: "",
            priceHigh: "",
            priceLow: "",
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.handleClose).toHaveBeenCalled();
    });

    test("opens an unreported period without loading suggestions and can return to the list", async () => {
        render(<ImportPeriodDialog {...createProps()}/>);

        fireEvent.click(screen.getByText("24Q2*"));

        expect(screen.getByLabelText("Name")).toHaveValue("24Q2");
        expect(mockGetFinancial).not.toHaveBeenCalled();
        expect(mockGetQuote).not.toHaveBeenCalled();

        fireEvent.click(screen.getByText("Back"));

        await waitFor(() => expect(screen.getByText("24Q1")).toBeInTheDocument());
        expect(screen.getByText("24Q2*")).toBeInTheDocument();
    });
});
