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
        companyLists: {},
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
            website: null,
            sector: "SEMICONDUCTORS",
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.setOpenEditCompany).toHaveBeenCalledWith(null);
    });

    test("reports a ticker that already exists, case-insensitively", () => {
        const props = createProps({
            openEditCompany: {},
            companyLists: {all: [{id: "company-1", ticker: "NVDA"}, {id: "company-2", ticker: "AMD"}]},
        });

        render(<EditCompanyDialog {...props}/>);

        const tickerField = screen.getByLabelText("Ticker");

        fireEvent.change(tickerField, {target: {value: "NVDA"}});
        expect(screen.getByText("already exists")).toBeInTheDocument();

        fireEvent.change(tickerField, {target: {value: "INTC"}});
        expect(screen.queryByText("already exists")).not.toBeInTheDocument();
    });

    test("keeps reporting the format problem before the duplicate check", () => {
        const props = createProps({
            openEditCompany: {},
            companyLists: {all: [{id: "company-1", ticker: "NVDA"}]},
        });

        render(<EditCompanyDialog {...props}/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "nvda"}});

        expect(screen.getByText("only uppercase")).toBeInTheDocument();
        expect(screen.queryByText("already exists")).not.toBeInTheDocument();
    });

    test("does not report a duplicate when editing an existing company", () => {
        const props = createProps({
            openEditCompany: {id: "company-1", ticker: "NVDA", currency: "$"},
            companyLists: {all: [{id: "company-1", ticker: "NVDA"}]},
        });

        render(<EditCompanyDialog {...props}/>);

        expect(screen.queryByLabelText("Ticker")).not.toBeInTheDocument();
        expect(screen.queryByText("already exists")).not.toBeInTheDocument();
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
                website: "https://www.nvidia.com",
            },
        });
        axios.post.mockResolvedValue({});

        const props = createProps({openEditCompany: {}});
        render(<EditCompanyDialog {...props}/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "NVDA"}});
        selectOption(0, "$");
        fireEvent.click(screen.getByRole("tab", {name: "Data"}));
        fireEvent.click(screen.getByRole("button", {name: "Try Load Company Data"}));

        await waitFor(() => expect(axios.get).toHaveBeenCalledWith(
            "/api/company/polygon/profile",
            {params: {ticker: "NVDA"}},
        ));
        await waitFor(() => expect(screen.getByLabelText("Name")).toHaveValue("NVIDIA Corporation"));
        expect(screen.getByLabelText("Description")).toHaveValue("Accelerated computing company");
        expect(screen.getByLabelText("Website")).toHaveValue("https://www.nvidia.com");

        fireEvent.click(screen.getByText("Create"));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("/api/company", {
            ticker: "NVDA",
            currency: "$",
            alphaVantageTicker: null,
            exchange: null,
            name: "NVIDIA Corporation",
            description: "Accelerated computing company",
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
            website: null,
        }));
    });

    test("splits General and Data fields into separate tabs", () => {
        const props = createProps({openEditCompany: {}});
        render(<EditCompanyDialog {...props}/>);

        expect(screen.getByLabelText("Ticker")).toBeInTheDocument();
        expect(screen.getAllByRole("combobox")).toHaveLength(3);
        expect(screen.queryByLabelText("Name")).not.toBeInTheDocument();
        expect(screen.queryByLabelText("Description")).not.toBeInTheDocument();
        expect(screen.queryByLabelText("Website")).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Try Load Company Data"})).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("tab", {name: "Data"}));

        expect(screen.getByLabelText("Name")).toBeInTheDocument();
        expect(screen.getByLabelText("Description")).toBeInTheDocument();
        expect(screen.getByLabelText("Website")).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Try Load Company Data"})).toBeInTheDocument();
        expect(screen.queryByLabelText("Ticker")).not.toBeInTheDocument();
        expect(screen.queryAllByRole("combobox")).toHaveLength(0);
    });

    test("switches back to the General tab whenever the dialog reopens", () => {
        const props = createProps({openEditCompany: {}});
        const {rerender} = render(<EditCompanyDialog {...props}/>);

        fireEvent.click(screen.getByRole("tab", {name: "Data"}));
        expect(screen.getByLabelText("Name")).toBeInTheDocument();

        rerender(<EditCompanyDialog {...props} openEditCompany={null}/>);
        rerender(<EditCompanyDialog {...props} openEditCompany={{}}/>);

        expect(screen.getByLabelText("Ticker")).toBeInTheDocument();
        expect(screen.queryByLabelText("Name")).not.toBeInTheDocument();
    });

    test("places the Alpha Vantage ticker retrieval directly below currency on the General tab", () => {
        const props = createProps({openEditCompany: {}});
        render(<EditCompanyDialog {...props}/>);

        selectOption(0, "€");

        const ticker = screen.getByLabelText("Ticker");
        const currency = screen.getAllByRole("combobox")[0];
        const findButton = screen.getByRole("button", {name: "Find Alpha Vantage tickers"});
        const sector = screen.getAllByRole("combobox")[1];

        expect(ticker.compareDocumentPosition(currency) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(currency.compareDocumentPosition(findButton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(findButton.compareDocumentPosition(sector) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
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
            website: null,
        }));
    });

    describe("Tags tab", () => {
        function companyLists() {
            return {
                owned: [{id: "company-1", ticker: "NVDA"}],
                growth: [{id: "company-1", ticker: "NVDA"}, {id: "company-2", ticker: "AMD"}],
                watchlist: [{id: "company-1", ticker: "NVDA"}],
                turnaround: [{id: "company-2", ticker: "AMD"}],
            };
        }

        test("is only shown when editing an existing company", () => {
            const {rerender} = render(<EditCompanyDialog {...createProps({openEditCompany: {}})}/>);

            expect(screen.queryByRole("tab", {name: "Tags"})).not.toBeInTheDocument();

            rerender(<EditCompanyDialog {...createProps({
                openEditCompany: {id: "company-1", ticker: "NVDA", currency: "$"},
                companyLists: companyLists(),
            })}/>);

            expect(screen.getByRole("tab", {name: "Tags"})).toBeInTheDocument();
        });

        test("lists the company's custom tags, excluding built-in lists", () => {
            const props = createProps({
                openEditCompany: {id: "company-1", ticker: "NVDA", currency: "$"},
                companyLists: companyLists(),
            });
            render(<EditCompanyDialog {...props}/>);

            fireEvent.click(screen.getByRole("tab", {name: "Tags"}));

            expect(screen.getByText("#growth")).toBeInTheDocument();
            expect(screen.getByText("#watchlist")).toBeInTheDocument();
            expect(screen.queryByText("#owned")).not.toBeInTheDocument();
            expect(screen.queryByText(/^No tags yet/)).not.toBeInTheDocument();
        });

        test("shows a placeholder when the company has no custom tags", () => {
            const props = createProps({
                openEditCompany: {id: "company-2", ticker: "AMD", currency: "$"},
                companyLists: {owned: [{id: "company-2", ticker: "AMD"}]},
            });
            render(<EditCompanyDialog {...props}/>);

            fireEvent.click(screen.getByRole("tab", {name: "Tags"}));

            expect(screen.getByText("No tags yet.")).toBeInTheDocument();
        });

        test("removes a tag immediately, without a confirmation step", async () => {
            axios.delete.mockResolvedValue({});
            const props = createProps({
                openEditCompany: {id: "company-1", ticker: "NVDA", currency: "$"},
                companyLists: companyLists(),
            });
            render(<EditCompanyDialog {...props}/>);

            fireEvent.click(screen.getByRole("tab", {name: "Tags"}));
            fireEvent.click(screen.getByRole("button", {name: "Remove tag growth"}));

            expect(screen.queryByText("Remove tag?")).not.toBeInTheDocument();
            await waitFor(() => expect(axios.delete).toHaveBeenCalledWith("/api/company/company-1/tag", {
                params: {value: "growth"},
            }));
            await waitFor(() => expect(screen.queryByText("#growth")).not.toBeInTheDocument());
            expect(screen.getByText("#watchlist")).toBeInTheDocument();
            expect(props.triggerRefresh).toHaveBeenCalled();
        });

        test("shows an alert and keeps the tag when removal fails", async () => {
            axios.delete.mockRejectedValue(new Error("failed"));
            const props = createProps({
                openEditCompany: {id: "company-1", ticker: "NVDA", currency: "$"},
                companyLists: companyLists(),
            });
            render(<EditCompanyDialog {...props}/>);

            fireEvent.click(screen.getByRole("tab", {name: "Tags"}));
            fireEvent.click(screen.getByRole("button", {name: "Remove tag growth"}));

            await waitFor(() => expect(mockFormatError).toHaveBeenCalled());
            expect(screen.getByText("#growth")).toBeInTheDocument();
        });

        test("adds a new tag via the icon button, using the same validation as the tag dialogue", async () => {
            axios.post.mockResolvedValue({});
            const props = createProps({
                openEditCompany: {id: "company-1", ticker: "NVDA", currency: "$"},
                companyLists: companyLists(),
            });
            render(<EditCompanyDialog {...props}/>);

            fireEvent.click(screen.getByRole("tab", {name: "Tags"}));
            const tagInput = screen.getByLabelText("Tag");
            const addButton = screen.getByRole("button", {name: "Add tag"});

            expect(addButton).toBeDisabled();

            fireEvent.change(tagInput, {target: {value: "has space"}});
            expect(screen.getByText("Tag must not contain spaces or tabs")).toBeInTheDocument();
            expect(addButton).toBeEnabled();

            fireEvent.change(tagInput, {target: {value: "growth"}});
            expect(screen.getByText("Tag is already assigned to this company")).toBeInTheDocument();

            fireEvent.change(tagInput, {target: {value: "owned"}});
            expect(screen.getByText("Tag name is reserved")).toBeInTheDocument();

            fireEvent.change(tagInput, {target: {value: "momentum"}});
            expect(screen.queryByText("Tag name is reserved")).not.toBeInTheDocument();
            expect(addButton).toBeEnabled();

            fireEvent.click(addButton);

            await waitFor(() => expect(axios.post).toHaveBeenCalledWith("/api/company/tag", {
                companyId: "company-1",
                value: "momentum",
            }));
            await waitFor(() => expect(screen.getByText("#momentum")).toBeInTheDocument());
            expect(tagInput).toHaveValue("");
            expect(addButton).toBeDisabled();
            expect(props.triggerRefresh).toHaveBeenCalled();
        });

        test("adds a new tag by pressing Enter in the input", async () => {
            axios.post.mockResolvedValue({});
            const props = createProps({
                openEditCompany: {id: "company-1", ticker: "NVDA", currency: "$"},
                companyLists: companyLists(),
            });
            render(<EditCompanyDialog {...props}/>);

            fireEvent.click(screen.getByRole("tab", {name: "Tags"}));
            const tagInput = screen.getByLabelText("Tag");

            fireEvent.change(tagInput, {target: {value: "momentum"}});
            fireEvent.keyDown(tagInput, {key: "Enter"});

            await waitFor(() => expect(axios.post).toHaveBeenCalledWith("/api/company/tag", {
                companyId: "company-1",
                value: "momentum",
            }));
        });

        test("offers unused custom tag keys from other companies as suggestions", () => {
            const props = createProps({
                openEditCompany: {id: "company-1", ticker: "NVDA", currency: "$"},
                companyLists: companyLists(),
            });
            render(<EditCompanyDialog {...props}/>);

            fireEvent.click(screen.getByRole("tab", {name: "Tags"}));
            fireEvent.mouseDown(screen.getByLabelText("Tag"));

            expect(screen.getByRole("option", {name: "turnaround"})).toBeInTheDocument();
            expect(screen.queryByRole("option", {name: "growth"})).not.toBeInTheDocument();
        });
    });
});
