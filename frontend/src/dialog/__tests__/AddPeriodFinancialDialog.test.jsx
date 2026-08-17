import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

const mockFormatError = jest.fn(() => ({title: "Save failed", message: "Financial data could not be saved"}));
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
jest.mock("../component/DialogTextField", () => dialogTextFieldModule);
jest.mock("../component/DialogDatePicker", () => dialogDatePickerModule);

import {AddPeriodFinancialDialog} from "../AddPeriodFinancialDialog";

const comparisonData = {
    reportDate: "2024-02-15",
    firebase: {
        shares: "11",
        revenue: "21",
        grossProfit: "31",
        operatingIncome: "41",
        netIncome: "51",
        dividend: "0.5",
        adjustedEps: "1.21",
        priceHigh: "141",
        priceLow: "91",
        capex: "-61",
        freeCashFlow: "71",
    },
    polygon: {
        shares: "10",
        revenue: "20",
        grossProfit: "30",
        operatingIncome: "40",
        netIncome: "50",
        dividend: null,
        adjustedEps: "1.18",
        priceHigh: "140.25",
        priceLow: "90.75",
        capex: "-60",
        freeCashFlow: "70",
    },
    warnings: [],
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
        period: {
            id: "period-1",
            name: {
                year: "2024",
                type: "Q1",
            },
            reportDate: "2024-02-15",
            previousReportDate: "2023-11-15",
        },
        ...overrides,
    };
}

describe("AddPeriodFinancialDialog", () => {
    beforeEach(() => {
        axios.get.mockReset();
        axios.put.mockReset();
        mockFormatError.mockClear();
        axios.get.mockResolvedValue({data: comparisonData});
    });

    test("loads Gemini and third-party suggestions and submits updated financial data", async () => {
        axios.put.mockResolvedValue({});

        const props = createProps();

        render(<AddPeriodFinancialDialog {...props}/>);

        await waitFor(() => expect(axios.get).toHaveBeenCalledWith(
            "http://backend/research/company-1/import/period/24Q1"
        ));
        expect(await screen.findByText("Gemini")).toBeInTheDocument();
        expect(screen.getByText("External")).toBeInTheDocument();
        expect(screen.getByLabelText("Report Date")).toHaveValue("");
        expect(screen.getByLabelText("Shares (in Millions)")).toHaveValue("");
        expect(screen.getByLabelText("Adjusted EPS")).toHaveValue("");

        fireEvent.click(screen.getByRole("button", {
            name: "Use External value for Shares (in Millions)",
        }));
        fireEvent.change(screen.getByLabelText("Report Date"), {target: {value: "2024-02-15"}});
        expect(screen.getByLabelText("Shares (in Millions)")).toHaveValue("10");
        fireEvent.click(screen.getByRole("button", {
            name: "Use Gemini value for Revenue (in Millions)",
        }));
        fireEvent.click(screen.getByRole("button", {
            name: "Use External value for Adjusted EPS",
        }));
        fireEvent.click(screen.getByRole("button", {
            name: "Use Gemini value for CapEx (in Millions)",
        }));
        fireEvent.click(screen.getByRole("button", {
            name: "Use External value for Free Cash Flow (in Millions)",
        }));
        fireEvent.change(screen.getByLabelText("Gross Profit (in Millions)"), {target: {value: "3"}});
        fireEvent.change(screen.getByLabelText("Operating Income (in Millions)"), {target: {value: "4"}});
        fireEvent.change(screen.getByLabelText("Net Income (in Millions)"), {target: {value: "5"}});
        fireEvent.change(screen.getByLabelText("Dividend (in Millions)"), {target: {value: "0.5"}});
        fireEvent.change(screen.getByLabelText("Highest Price"), {target: {value: "125"}});
        fireEvent.change(screen.getByLabelText("Lowest Price"), {target: {value: "95"}});

        fireEvent.click(screen.getByRole("button", {name: "Create"}));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("http://backend/period/financial", {
            id: "period-1",
            reportDate: "2024-02-15",
            priceLow: "95",
            priceHigh: "125",
            shares: "10",
            revenue: "21",
            grossProfit: "3",
            operatingIncome: "4",
            netIncome: "5",
            dividend: "0.5",
            adjustedEps: "1.18",
            capex: "-61",
            freeCashFlow: "70",
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.handleClose).toHaveBeenCalled();
    });

    test("shows formatted error when submit fails", async () => {
        axios.put.mockRejectedValue(new Error("boom"));

        const props = createProps();

        render(<AddPeriodFinancialDialog {...props}/>);

        await screen.findByText("Gemini");
        fireEvent.change(screen.getByLabelText("Report Date"), {target: {value: "2024-02-15"}});
        fireEvent.change(screen.getByLabelText("Shares (in Millions)"), {target: {value: "1"}});
        fireEvent.change(screen.getByLabelText("Revenue (in Millions)"), {target: {value: "2"}});
        fireEvent.change(screen.getByLabelText("Gross Profit (in Millions)"), {target: {value: "3"}});
        fireEvent.change(screen.getByLabelText("Operating Income (in Millions)"), {target: {value: "4"}});
        fireEvent.change(screen.getByLabelText("Net Income (in Millions)"), {target: {value: "5"}});
        fireEvent.change(screen.getByLabelText("Dividend (in Millions)"), {target: {value: "0.5"}});
        fireEvent.change(screen.getByLabelText("Adjusted EPS"), {target: {value: "1.18"}});
        fireEvent.change(screen.getByLabelText("Highest Price"), {target: {value: "125"}});
        fireEvent.change(screen.getByLabelText("Lowest Price"), {target: {value: "95"}});
        fireEvent.click(screen.getByRole("button", {name: "Create"}));

        await waitFor(() => expect(mockFormatError).toHaveBeenCalled());
        expect(props.triggerRefresh).not.toHaveBeenCalled();
        expect(props.handleClose).not.toHaveBeenCalled();
    });

    test("requires core financials and submits empty optional fields as null", async () => {
        axios.put.mockResolvedValue({});

        render(<AddPeriodFinancialDialog {...createProps()}/>);

        await screen.findByText("Gemini");
        expect(screen.getByLabelText("Report Date")).toBeRequired();
        expect(screen.getByLabelText("Shares (in Millions)")).toBeRequired();
        expect(screen.getByLabelText("Revenue (in Millions)")).toBeRequired();
        expect(screen.getByLabelText("Net Income (in Millions)")).toBeRequired();
        expect(screen.getByLabelText("Gross Profit (in Millions)")).not.toBeRequired();
        expect(screen.getByLabelText("Operating Income (in Millions)")).not.toBeRequired();
        expect(screen.getByLabelText("Dividend (in Millions)")).not.toBeRequired();
        expect(screen.getByLabelText("Adjusted EPS")).not.toBeRequired();
        expect(screen.getByLabelText("Highest Price")).not.toBeRequired();
        expect(screen.getByLabelText("Lowest Price")).not.toBeRequired();
        expect(screen.getByLabelText("CapEx (in Millions)")).not.toBeRequired();
        expect(screen.getByLabelText("Free Cash Flow (in Millions)")).not.toBeRequired();

        fireEvent.change(screen.getByLabelText("Report Date"), {target: {value: "2024-02-15"}});
        fireEvent.change(screen.getByLabelText("Shares (in Millions)"), {target: {value: "10"}});
        fireEvent.change(screen.getByLabelText("Revenue (in Millions)"), {target: {value: "20"}});
        fireEvent.change(screen.getByLabelText("Net Income (in Millions)"), {target: {value: "5"}});
        fireEvent.click(screen.getByRole("button", {name: "Create"}));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("http://backend/period/financial", {
            id: "period-1",
            reportDate: "2024-02-15",
            shares: "10",
            revenue: "20",
            netIncome: "5",
            grossProfit: null,
            operatingIncome: null,
            dividend: null,
            adjustedEps: null,
            priceHigh: null,
            priceLow: null,
            capex: null,
            freeCashFlow: null,
        }));
    });

    test("opens with empty suggestion columns when no import data is available", async () => {
        axios.get.mockResolvedValue({data: {firebase: {}, polygon: {}, warnings: []}});

        render(<AddPeriodFinancialDialog {...createProps()}/>);

        expect(await screen.findByText("Gemini")).toBeInTheDocument();
        expect(screen.getByText("External")).toBeInTheDocument();
        expect(screen.getByLabelText("Report Date")).toHaveValue("");
        expect(screen.getByLabelText("Revenue (in Millions)")).toHaveValue("");
        expect(mockFormatError).not.toHaveBeenCalled();
    });

    test("edits a reported period without loading suggestions", async () => {
        axios.put.mockResolvedValue({});
        const props = createProps({
            period: {
                ...createProps().period,
                shares: 123,
                priceHigh: 125,
                priceLow: 95,
                financial: {
                    revenue: {value: 21},
                    grossProfit: {value: 31},
                    operatingIncome: {value: 41},
                    netIncome: {value: 51},
                    dividend: 0.5,
                    adjustedEps: 1.21,
                    capex: {value: -61},
                    freeCashFlow: {value: 71},
                },
            },
        });

        render(<AddPeriodFinancialDialog {...props} edit/>);

        expect(axios.get).not.toHaveBeenCalled();
        expect(screen.queryByText("Gemini")).not.toBeInTheDocument();
        expect(screen.getByRole("heading", {name: "Edit Period for NVDA 24Q1"})).toBeInTheDocument();
        expect(screen.getByLabelText("Report Date")).toHaveValue("2024-02-15");
        expect(screen.getByLabelText("Shares (in Millions)")).toHaveValue("123");
        expect(screen.getByLabelText("CapEx (in Millions)")).toHaveValue("-61");
        expect(screen.getByLabelText("Free Cash Flow (in Millions)")).toHaveValue("71");
        expect(screen.queryByLabelText("Name")).not.toBeInTheDocument();
        expect(screen.queryByLabelText("Ending Month")).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Update"}));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("http://backend/period/financial", {
            id: "period-1",
            reportDate: "2024-02-15",
            shares: "123",
            revenue: "21",
            grossProfit: "31",
            operatingIncome: "41",
            netIncome: "51",
            dividend: "0.5",
            adjustedEps: "1.21",
            priceHigh: "125",
            priceLow: "95",
            capex: "-61",
            freeCashFlow: "71",
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.handleClose).toHaveBeenCalled();
    });
});
