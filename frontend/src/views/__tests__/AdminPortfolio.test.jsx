import React from "react";
import {fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import axios from "axios";

const mockUseData = jest.fn();

jest.mock("axios");
jest.mock("../../properties", () => ({backend: "http://backend"}));
jest.mock("../../service/BackendService", () => ({
    useData: (...args) => mockUseData(...args),
}));
jest.mock("../component/Loader", () => ({
    Loader: ({error}) => <div>{error?.message ?? "loading"}</div>,
}));

import {AdminPortfolio} from "../AdminPortfolio";

const companies = [
    {id: 1, ticker: "AMD"},
    {id: 2, ticker: "NVDA"},
    {id: 3, ticker: "INTC"},
];
const portfolios = [
    {key: "PATRIA_MARGIN", name: "Patria - Margin", abbreviation: "Pm"},
    {key: "REVOLUT_STANDARD", name: "Revolut - Standard", abbreviation: "R"},
];
const trades = [
    {
        id: 101,
        company: companies[0],
        purchaseDate: "2025-01-02",
        purchaseQuantity: 10,
        purchasePrice: 120.5,
        purchaseFees: 1.25,
        sellDate: null,
        sellPrice: null,
        sellFees: null,
    },
    {
        id: 102,
        company: companies[1],
        purchaseDate: "2025-02-03",
        purchaseQuantity: 20,
        purchasePrice: 140,
        purchaseFees: 2,
        sellDate: "2025-04-05",
        sellPrice: 160,
        sellFees: 2.5,
    },
];

describe("AdminPortfolio", () => {
    beforeEach(() => {
        mockUseData.mockReset();
        mockUseData.mockReturnValue({data: trades, loaded: true, error: null});
        axios.put.mockReset();
    });

    test("lists the requested trade values and filters locally by companies with unassigned trades", async () => {
        render(<AdminPortfolio companyLists={{all: companies}} portfolios={portfolios}/>);

        expect(mockUseData).toHaveBeenCalledWith("/admin/portfolio/trades?filter");
        const table = screen.getByRole("table", {name: "Trades without portfolio"});
        expect(within(table).getAllByRole("columnheader").map(header => header.textContent)).toEqual([
            "",
            "Company",
            "Quantity",
            "Purchase date",
            "Purchase price",
            "Purchase fees",
            "Sale date",
            "Sale price",
            "Sale fees",
        ]);
        expect(within(table).queryByRole("columnheader", {name: "ID"})).not.toBeInTheDocument();
        expect(within(table).getByText("1.25")).toBeInTheDocument();
        expect(within(table).getByText("160")).toBeInTheDocument();
        expect(within(table).getByText("2.5")).toBeInTheDocument();

        fireEvent.mouseDown(screen.getAllByRole("combobox")[0]);
        expect(screen.getByRole("option", {name: "AMD"})).toBeInTheDocument();
        expect(screen.getByRole("option", {name: "NVDA"})).toBeInTheDocument();
        expect(screen.queryByRole("option", {name: "INTC"})).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole("option", {name: "NVDA"}));

        expect(mockUseData).toHaveBeenLastCalledWith("/admin/portfolio/trades?filter");
        expect(within(table).getByText("NVDA")).toBeInTheDocument();
        expect(within(table).queryByText("AMD")).not.toBeInTheDocument();
    });

    test("selects and clears every visible trade from the header checkbox", () => {
        render(<AdminPortfolio companyLists={{all: companies}} portfolios={portfolios}/>);

        const selectAll = screen.getByLabelText("Select all trades");
        const firstTrade = screen.getByLabelText("Select trade 101");
        const secondTrade = screen.getByLabelText("Select trade 102");

        fireEvent.click(selectAll);

        expect(selectAll).toBeChecked();
        expect(firstTrade).toBeChecked();
        expect(secondTrade).toBeChecked();

        fireEvent.click(selectAll);

        expect(selectAll).not.toBeChecked();
        expect(firstTrade).not.toBeChecked();
        expect(secondTrade).not.toBeChecked();
    });

    test("assigns the selected portfolio and resets both selectors", async () => {
        axios.put.mockResolvedValue({});
        render(<AdminPortfolio companyLists={{all: companies}} portfolios={portfolios}/>);

        fireEvent.mouseDown(screen.getAllByRole("combobox")[0]);
        fireEvent.click(screen.getByRole("option", {name: "NVDA"}));
        fireEvent.click(screen.getByLabelText("Select trade 102"));

        fireEvent.mouseDown(screen.getAllByRole("combobox")[1]);
        fireEvent.click(screen.getByRole("option", {name: "Patria - Margin (Pm)"}));
        fireEvent.click(screen.getByRole("button", {name: "Assign portfolio"}));

        expect(screen.getByText("Assign Patria - Margin (Pm) to 1 selected trade?")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Confirm"}));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("http://backend/admin/portfolio", {
            tradeIds: [102],
            portfolio: "PATRIA_MARGIN",
        }));
        const successAlert = await screen.findByRole("alert");
        expect(successAlert).toHaveTextContent("Portfolio assigned successfully.");
        expect(successAlert.parentElement).toBe(screen.getByRole("button", {name: "Assign portfolio"}).parentElement);
        await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
        expect(document.getElementById("admin-portfolio-company-label").parentElement.querySelector("input"))
            .toHaveValue("");
        expect(document.getElementById("admin-portfolio-value-label").parentElement.querySelector("input"))
            .toHaveValue("");
        expect(screen.getByRole("button", {name: "Assign portfolio"})).toBeDisabled();
        expect(mockUseData.mock.calls.at(-1)[0]).toMatch(/^\/admin\/portfolio\/trades\?filter&refresh=\d+$/);
    });
});
