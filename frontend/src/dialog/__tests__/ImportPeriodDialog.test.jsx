import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

const mockFormatError = jest.fn();
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

import {ImportPeriodDialog} from "../ImportPeriodDialog";

const candidates = [
    {
        name: "24Q1",
        isReported: true,
        endingMonth: "2024-03",
    },
    {
        name: "24Q2",
        isReported: false,
        endingMonth: "2024-06",
    },
];

function createProps(overrides = {}) {
    return {
        open: true,
        handleClose: jest.fn(),
        triggerRefresh: jest.fn(),
        company: {
            id: "company-1",
            ticker: "NVDA",
        },
        periods: candidates,
        ...overrides,
    };
}

function createImportData(overrides = {}) {
    return {
        name: "24Q1",
        endingMonth: "2024-03",
        reportDate: "2024-02-15",
        isReported: true,
        firebase: {
            shares: "11",
            revenue: "21",
            grossProfit: "31",
            operatingIncome: "41",
            netIncome: "51",
            dividend: "61",
            adjustedEps: "1.21",
            priceHigh: "141",
            priceLow: "91",
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
        },
        warnings: [],
        ...overrides,
    };
}

describe("ImportPeriodDialog", () => {
    beforeEach(() => {
        axios.get.mockReset();
        axios.post.mockReset();
        mockFormatError.mockReset();
        mockFormatError.mockReturnValue({
            title: "Import failed",
            message: "Period could not be imported",
        });
        axios.get.mockResolvedValue({data: createImportData()});
    });

    test("loads a reported period with separate Gemini and third-party suggestions", async () => {
        axios.post.mockResolvedValue({});

        const props = createProps();

        render(<ImportPeriodDialog {...props}/>);

        expect(await screen.findByText("24Q1")).toBeInTheDocument();
        expect(axios.get).not.toHaveBeenCalled();
        fireEvent.click(screen.getByText("24Q1"));

        await waitFor(() => expect(axios.get).toHaveBeenCalledWith(
            "http://backend/research/company-1/import/period/24Q1"
        ));
        expect(await screen.findByLabelText("Name")).toHaveValue("24Q1");
        expect(screen.getByRole("heading", {name: "Import Period 24Q1"})).toBeInTheDocument();
        expect(screen.getByLabelText("Ending Month")).toHaveValue("2024-03");
        expect(screen.getByLabelText("Report Date")).toHaveValue("2024-02-15");
        expect(screen.getByText("Gemini")).toBeInTheDocument();
        expect(screen.getByText("External")).toBeInTheDocument();
        expect(screen.getByLabelText("Shares (in Millions)")).toHaveValue("");
        expect(screen.getByLabelText("Adjusted EPS")).toHaveValue("");

        fireEvent.click(await screen.findByRole("button", {
            name: "Use External value for Shares (in Millions)",
        }));
        expect(screen.getByLabelText("Shares (in Millions)")).toHaveValue("10");

        fireEvent.click(screen.getByRole("button", {
            name: "Use External value for Adjusted EPS",
        }));
        expect(screen.getByLabelText("Adjusted EPS")).toHaveValue("1.18");

        fireEvent.change(screen.getByLabelText("Revenue (in Millions)"), {target: {value: "20"}});
        fireEvent.change(screen.getByLabelText("Gross Profit (in Millions)"), {target: {value: "30"}});
        fireEvent.change(screen.getByLabelText("Operating Income (in Millions)"), {target: {value: "40"}});
        fireEvent.change(screen.getByLabelText("Net Income (in Millions)"), {target: {value: "50"}});
        fireEvent.change(screen.getByLabelText("Dividend (in Millions)"), {target: {value: "60"}});
        fireEvent.change(screen.getByLabelText("Highest Price"), {target: {value: "140.25"}});
        fireEvent.change(screen.getByLabelText("Lowest Price"), {target: {value: "90.75"}});

        fireEvent.click(screen.getByRole("button", {name: "Create"}));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("http://backend/period/import", {
            companyId: "company-1",
            name: "24Q1",
            isReported: true,
            endingMonth: "2024-03",
            reportDate: "2024-02-15",
            shares: "10",
            revenue: "20",
            grossProfit: "30",
            operatingIncome: "40",
            netIncome: "50",
            dividend: "60",
            adjustedEps: "1.18",
            priceHigh: "140.25",
            priceLow: "90.75",
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.handleClose).toHaveBeenCalled();
    });

    test("shows a loader while reported period data is being retrieved", async () => {
        let resolveRequest;
        axios.get.mockImplementation(() => new Promise(resolve => {
            resolveRequest = resolve;
        }));

        render(<ImportPeriodDialog {...createProps()}/>);

        fireEvent.click(await screen.findByText("24Q1"));

        expect(screen.getByRole("progressbar")).toBeInTheDocument();

        resolveRequest({data: createImportData()});
        expect(await screen.findByLabelText("Name")).toHaveValue("24Q1");
        expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    });

    test("opens an unreported period without loading suggestions and can return to the list", async () => {
        render(<ImportPeriodDialog {...createProps()}/>);

        fireEvent.click(await screen.findByText("24Q2*"));

        expect(screen.getByLabelText("Name")).toHaveValue("24Q2");
        expect(screen.getByLabelText("Ending Month")).toHaveValue("2024-06");
        expect(screen.queryByLabelText("Report Date")).not.toBeInTheDocument();
        expect(axios.get).not.toHaveBeenCalled();
        expect(screen.queryByText("Gemini")).not.toBeInTheDocument();
        expect(screen.queryByText("External")).not.toBeInTheDocument();

        fireEvent.click(screen.getByText("Back"));

        await waitFor(() => expect(screen.getByText("24Q1")).toBeInTheDocument());
        expect(screen.getByText("24Q2*")).toBeInTheDocument();
    });

    test("submits an unreported period through its dedicated endpoint", async () => {
        axios.post.mockResolvedValue({});
        const props = createProps();

        render(<ImportPeriodDialog {...props}/>);

        fireEvent.click(await screen.findByText("24Q2*"));
        fireEvent.click(screen.getByRole("button", {name: "Create"}));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith(
            "http://backend/period/import/unreported",
            {
                companyId: "company-1",
                name: "24Q2",
                endingMonth: "2024-06",
            }
        ));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.handleClose).toHaveBeenCalled();
    });

    test("keeps available source data and explains partial Polygon failures", async () => {
        const importData = createImportData({
            polygon: {
                shares: null,
                revenue: null,
                grossProfit: null,
                operatingIncome: null,
                netIncome: null,
                dividend: null,
                adjustedEps: null,
                priceHigh: "140.25",
                priceLow: "90.75",
            },
            warnings: ["Polygon.io financial data could not be loaded: rate limit exceeded"],
        });
        axios.get.mockResolvedValue({data: importData});

        render(<ImportPeriodDialog {...createProps()}/>);

        fireEvent.click(await screen.findByText("24Q1"));

        expect(await screen.findByText("Some Polygon.io data could not be loaded")).toBeInTheDocument();
        expect(screen.getByText(
            "Polygon.io financial data could not be loaded: rate limit exceeded"
        )).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {
            name: "Use Gemini value for Revenue (in Millions)",
        }));
        expect(screen.getByLabelText("Revenue (in Millions)")).toHaveValue("21");
        expect(screen.getByRole("button", {
            name: "Use External value for Highest Price",
        })).toBeInTheDocument();
    });

    test("clears a failed import alert whenever a field is edited", async () => {
        axios.post.mockRejectedValue({message: "invalid import"});

        render(<ImportPeriodDialog {...createProps()}/>);

        fireEvent.click(await screen.findByText("24Q1"));
        await screen.findByLabelText("Name");

        fireEvent.change(screen.getByLabelText("Shares (in Millions)"), {target: {value: "10"}});
        fireEvent.change(screen.getByLabelText("Revenue (in Millions)"), {target: {value: "20"}});
        fireEvent.change(screen.getByLabelText("Gross Profit (in Millions)"), {target: {value: "30"}});
        fireEvent.change(screen.getByLabelText("Operating Income (in Millions)"), {target: {value: "40"}});
        fireEvent.change(screen.getByLabelText("Net Income (in Millions)"), {target: {value: "50"}});
        fireEvent.change(screen.getByLabelText("Dividend (in Millions)"), {target: {value: "60"}});
        fireEvent.change(screen.getByLabelText("Adjusted EPS"), {target: {value: "1.18"}});
        fireEvent.change(screen.getByLabelText("Highest Price"), {target: {value: "140.25"}});
        fireEvent.change(screen.getByLabelText("Lowest Price"), {target: {value: "90.75"}});
        fireEvent.click(screen.getByRole("button", {name: "Create"}));

        expect(await screen.findByText("Import failed")).toBeInTheDocument();

        fireEvent.change(screen.getByLabelText("Revenue (in Millions)"), {target: {value: "21"}});
        expect(screen.queryByText("Import failed")).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Create"}));
        expect(await screen.findByText("Import failed")).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {
            name: "Use Gemini value for Revenue (in Millions)",
        }));

        expect(screen.queryByText("Import failed")).not.toBeInTheDocument();
    });

    test("shows the backend failure reason and returns to the period list", async () => {
        const error = {name: "AxiosError", message: "backend unavailable"};
        axios.get.mockRejectedValue(error);

        render(<ImportPeriodDialog {...createProps()}/>);

        fireEvent.click(await screen.findByText("24Q1"));

        await waitFor(() => expect(mockFormatError).toHaveBeenCalledWith(error));
        expect(await screen.findByText("Import failed")).toBeInTheDocument();
        expect(screen.getByText("Period could not be imported")).toBeInTheDocument();
        expect(screen.getByText("24Q1")).toBeInTheDocument();
    });

    test("shows an empty state when no periods are available", async () => {
        render(<ImportPeriodDialog {...createProps({periods: []})}/>);

        expect(await screen.findByText("No periods available for import.")).toBeInTheDocument();
        expect(axios.get).not.toHaveBeenCalled();
    });
});
