import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

const mockFormatError = jest.fn(() => ({title: "Save failed", message: "Company could not be saved"}));
const dialogTextFieldModule = {
    DialogTextField: ({id, label, value = "", onChange, validate, required = true, multiline, minRows, ...props}) => {
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

jest.mock("axios");
jest.mock("../../properties", () => ({
    backend: "/api",
}));
jest.mock("../../service/FormattingService", () => ({
    formatError: (...args) => mockFormatError(...args),
}));
jest.mock("../component/DialogTextField", () => dialogTextFieldModule);

import {EditCompanyDialog} from "../EditCompanyDialog";

function selectOption(index, optionText) {
    fireEvent.mouseDown(screen.getAllByRole("combobox")[index]);
    fireEvent.click(screen.getByRole("option", {name: optionText}));
}

function createProps(overrides = {}) {
    return {
        openEditCompany: null,
        setOpenEditCompany: jest.fn(),
        triggerRefresh: jest.fn(),
        currencies: ["$", "€"],
        sectors: [
            {key: "SEMICONDUCTORS", name: "Semiconductors"},
            {key: "ENERGY", name: "Energy"},
        ],
        exchanges: [
            {key: "XAMS", name: "Euronext Amsterdam"},
            {key: "XNAS", name: "Nasdaq"},
            {key: "XPAR", name: "Euronext Paris"},
        ],
        ...overrides,
    };
}

describe("EditCompanyDialog", () => {
    beforeEach(() => {
        axios.get.mockReset();
        axios.post.mockReset();
        axios.put.mockReset();
        mockFormatError.mockClear();
    });

    test("creates a company in add mode", async () => {
        axios.post.mockResolvedValue({});

        const props = createProps({
            openEditCompany: {},
        });

        render(<EditCompanyDialog {...props}/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "NVDA"}});
        selectOption(0, "€");
        selectOption(1, "Semiconductors");
        fireEvent.click(screen.getByText("Create"));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("/api/company", {
            ticker: "NVDA",
            currency: "€",
            alphaVantageTicker: null,
            exchange: null,
            name: null,
            description: null,
            logoUrl: null,
            website: null,
            sector: "SEMICONDUCTORS",
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.setOpenEditCompany).toHaveBeenCalledWith(null);
    });

    test("updates a company in edit mode", async () => {
        axios.put.mockResolvedValue({});

        const props = createProps({
            openEditCompany: {
                id: "company-1",
                ticker: "NVDA",
                currency: "$",
                sector: {key: "SEMICONDUCTORS"},
                exchange: {key: "XNAS"},
                name: "NVIDIA Corporation",
                description: "Accelerated computing company",
                logoUrl: "https://example.test/nvda.svg",
                website: "https://www.nvidia.com",
            },
        });

        render(<EditCompanyDialog {...props}/>);

        expect(screen.queryByLabelText("Ticker")).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Find Alpha Vantage tickers"})).not.toBeInTheDocument();
        expect(screen.queryByLabelText("Alpha Vantage ticker")).not.toBeInTheDocument();

        fireEvent.click(screen.getByText("Edit"));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/company", {
            id: "company-1",
            ticker: "NVDA",
            currency: "$",
            alphaVantageTicker: null,
            exchange: "XNAS",
            name: "NVIDIA Corporation",
            description: "Accelerated computing company",
            logoUrl: "https://example.test/nvda.svg",
            website: "https://www.nvidia.com",
            sector: "SEMICONDUCTORS",
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.setOpenEditCompany).toHaveBeenCalledWith(null);
    });

    test("shows Alpha Vantage search only for non-USD currencies and keeps the selector hidden", () => {
        const props = createProps({openEditCompany: {}});
        render(<EditCompanyDialog {...props}/>);

        expect(screen.queryByRole("button", {name: "Find Alpha Vantage tickers"})).not.toBeInTheDocument();
        expect(screen.queryByLabelText("Alpha Vantage ticker")).not.toBeInTheDocument();

        selectOption(0, "€");

        expect(screen.getByRole("button", {name: "Find Alpha Vantage tickers"})).toBeInTheDocument();
        expect(screen.queryByLabelText("Alpha Vantage ticker")).not.toBeInTheDocument();

        selectOption(0, "$");

        expect(screen.queryByRole("button", {name: "Find Alpha Vantage tickers"})).not.toBeInTheDocument();
        expect(screen.queryByLabelText("Alpha Vantage ticker")).not.toBeInTheDocument();
    });

    test("loads Polygon company data into editable inputs", async () => {
        axios.get.mockResolvedValue({
            data: {
                name: "NVIDIA Corporation",
                description: "Accelerated computing company",
                logoUrl: "https://example.test/nvda.svg",
                website: "https://www.nvidia.com",
            },
        });
        axios.post.mockResolvedValue({});

        const props = createProps({openEditCompany: {}});
        render(<EditCompanyDialog {...props}/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "NVDA"}});
        selectOption(0, "$");
        fireEvent.click(screen.getByRole("button", {name: "Try Load Company Data"}));

        await waitFor(() => expect(axios.get).toHaveBeenCalledWith(
            "/api/company/polygon/profile",
            {params: {ticker: "NVDA"}},
        ));
        await waitFor(() => expect(screen.getByLabelText("Name")).toHaveValue("NVIDIA Corporation"));
        expect(screen.getByLabelText("Description")).toHaveValue("Accelerated computing company");
        expect(screen.getByLabelText("Logo URL")).toHaveValue("https://example.test/nvda.svg");
        expect(screen.getByLabelText("Website")).toHaveValue("https://www.nvidia.com");

        fireEvent.click(screen.getByText("Create"));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("/api/company", {
            ticker: "NVDA",
            currency: "$",
            alphaVantageTicker: null,
            exchange: null,
            name: "NVIDIA Corporation",
            description: "Accelerated computing company",
            logoUrl: "https://example.test/nvda.svg",
            website: "https://www.nvidia.com",
        }));
    });

    test("searches and selects a currency-matching Alpha Vantage ticker", async () => {
        axios.get.mockResolvedValue({
            data: [
                {
                    symbol: "ASML.AMS",
                    name: "ASML Holding N.V.",
                    region: "Amsterdam",
                    currency: "EUR",
                },
                {
                    symbol: "ASME.FRK",
                    name: "ASML Holding NV",
                    region: "Frankfurt",
                    currency: "EUR",
                },
            ],
        });
        axios.post.mockResolvedValue({});

        const props = createProps({openEditCompany: {}});
        render(<EditCompanyDialog {...props}/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "ASML"}});
        selectOption(0, "€");
        fireEvent.click(screen.getByRole("button", {name: "Find Alpha Vantage tickers"}));

        await waitFor(() => expect(axios.get).toHaveBeenCalledWith(
            "/api/company/alpha-vantage/tickers",
            {params: {ticker: "ASML", currency: "€"}}
        ));

        await waitFor(() => expect(screen.getByLabelText("Alpha Vantage ticker")).toBeInTheDocument());
        expect(screen.queryByRole("button", {name: "Find Alpha Vantage tickers"})).not.toBeInTheDocument();
        fireEvent.mouseDown(screen.getByLabelText("Alpha Vantage ticker"));
        fireEvent.click(screen.getByRole("option", {
            name: "ASML.AMS — ASML Holding N.V. (Amsterdam)",
        }));
        fireEvent.click(screen.getByText("Create"));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("/api/company", {
            ticker: "ASML",
            currency: "€",
            alphaVantageTicker: "ASML.AMS",
            exchange: null,
            name: null,
            description: null,
            logoUrl: null,
            website: null,
        }));
    });

    test("creates a company with a selected exchange", async () => {
        axios.post.mockResolvedValue({});
        const props = createProps({openEditCompany: {}});

        render(<EditCompanyDialog {...props}/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "LVMH"}});
        selectOption(0, "€");
        selectOption(2, "Euronext Paris");
        fireEvent.click(screen.getByText("Create"));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("/api/company", {
            ticker: "LVMH",
            currency: "€",
            alphaVantageTicker: null,
            exchange: "XPAR",
            name: null,
            description: null,
            logoUrl: null,
            website: null,
        }));
    });
});
