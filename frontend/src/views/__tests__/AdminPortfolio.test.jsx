import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
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
        sellDate: null,
    },
    {
        id: 102,
        company: companies[1],
        purchaseDate: "2025-02-03",
        purchaseQuantity: 20,
        purchasePrice: 140,
        sellDate: "2025-04-05",
    },
];

describe("AdminPortfolio", () => {
    beforeEach(() => {
        mockUseData.mockReset();
        mockUseData.mockReturnValue({data: trades, loaded: true, error: null});
        axios.put.mockReset();
    });

    test("lists unassigned trades and filters by company", async () => {
        render(<AdminPortfolio companies={companies} portfolios={portfolios}/>);

        expect(mockUseData).toHaveBeenCalledWith("/admin/portfolio/trades?filter");
        expect(screen.getByText("AMD")).toBeInTheDocument();
        expect(screen.getByText("NVDA")).toBeInTheDocument();

        fireEvent.mouseDown(screen.getAllByRole("combobox")[0]);
        fireEvent.click(screen.getByRole("option", {name: "NVDA"}));

        await waitFor(() => expect(mockUseData).toHaveBeenLastCalledWith(
            "/admin/portfolio/trades?filter&companyId=2"
        ));
    });

    test("confirms and bulk assigns the selected portfolio", async () => {
        axios.put.mockResolvedValue({});
        render(<AdminPortfolio companies={companies} portfolios={portfolios}/>);

        fireEvent.click(screen.getByLabelText("Select trade 101"));
        fireEvent.click(screen.getByLabelText("Select trade 102"));

        fireEvent.mouseDown(screen.getAllByRole("combobox")[1]);
        fireEvent.click(screen.getByRole("option", {name: "Patria - Margin (Pm)"}));
        fireEvent.click(screen.getByRole("button", {name: "Assign portfolio"}));

        expect(screen.getByText("Assign Patria - Margin (Pm) to 2 selected trades?")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Confirm"}));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("http://backend/admin/portfolio", {
            tradeIds: [101, 102],
            portfolio: "PATRIA_MARGIN",
        }));
        expect(await screen.findByText("Portfolio assigned successfully.")).toBeInTheDocument();
    });
});
