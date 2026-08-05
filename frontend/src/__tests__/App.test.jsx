import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";
import {App} from "../App";

jest.mock("axios");

jest.mock("../views/component/Loader", () => ({
    Loader: () => <div>loader</div>,
}));

jest.mock("../views/component/MainBar", () => ({
    MainBar: (props) => (
        <div>
            <div>company:{props.companySelectorValue?.ticker || ""}</div>
            <div>currency:{props.currencySelectorValue || ""}</div>
            <div>years:{props.years.join(",")}</div>
            <div>sector:{props.sectorSelectorValue?.name || ""}</div>
            <button onClick={() => props.setCurrencySelectorValue("$")}>set currency</button>
            <button onClick={() => props.setSectorSelectorValue({key: "TECH", name: "Technology"})}>set sector</button>
            <button onClick={() => props.setCompanySelectorValue({id: "company-1", ticker: "NVDA"})}>set company</button>
            <button onClick={() => props.setCompanySelectorValue("")}>clear company</button>
        </div>
    ),
}));

jest.mock("../views/Home", () => ({
    Home: () => <div>home</div>,
}));

jest.mock("../views/Trades", () => ({
    Trades: () => <div>trades</div>,
}));

jest.mock("../views/Research", () => ({
    Research: () => <div>research</div>,
}));

jest.mock("../views/Dividends", () => ({
    Dividends: () => <div>dividends</div>,
}));

jest.mock("../views/Stats", () => ({
    Stats: () => <div>stats</div>,
}));

jest.mock("../views/Companies", () => ({
    Companies: () => <div>companies</div>,
}));

jest.mock("../views/Analytics", () => ({
    Analytics: () => <div>analytics</div>,
}));

jest.mock("../views/AdminPortfolio", () => ({
    AdminPortfolio: (props) => <div>admin-portfolios:{props.portfolios.map(portfolio => portfolio.key).join(",")}</div>,
}));

describe("App", () => {
    beforeEach(() => {
        window.history.pushState({}, "", "/");
        axios.get.mockResolvedValue({
            data: {
                companies: [{id: "company-1", ticker: "NVDA"}],
                currencies: ["$"],
                sectors: [{key: "TECH", name: "Technology"}],
                portfolios: [{key: "PATRIA_STANDARD", name: "Patria - Standard", abbreviation: "P"}],
                years: ["2024", "2023"],
            },
        });
    });

    test("loads the hidden admin portfolio route directly", async () => {
        window.history.pushState({}, "", "/admin/portfolio");

        render(<App/>);

        expect(await screen.findByText("admin-portfolios:PATRIA_STANDARD")).toBeInTheDocument();
    });

    test("resets currency and sector when actual company is selected", async () => {
        render(<App/>);

        await waitFor(() => expect(screen.getByText("home")).toBeInTheDocument());
        expect(screen.getByText("years:2024,2023")).toBeInTheDocument();

        fireEvent.click(screen.getByText("set currency"));
        fireEvent.click(screen.getByText("set sector"));

        expect(screen.getByText("currency:$")).toBeInTheDocument();
        expect(screen.getByText("sector:Technology")).toBeInTheDocument();

        fireEvent.click(screen.getByText("set company"));

        expect(screen.getByText("company:NVDA")).toBeInTheDocument();
        expect(screen.getByText("currency:")).toBeInTheDocument();
        expect(screen.getByText("sector:")).toBeInTheDocument();
    });

    test("does not reset currency and sector when company is cleared", async () => {
        render(<App/>);

        await waitFor(() => expect(screen.getByText("home")).toBeInTheDocument());

        fireEvent.click(screen.getByText("set currency"));
        fireEvent.click(screen.getByText("set sector"));
        fireEvent.click(screen.getByText("clear company"));

        expect(screen.getByText("company:")).toBeInTheDocument();
        expect(screen.getByText("currency:$")).toBeInTheDocument();
        expect(screen.getByText("sector:Technology")).toBeInTheDocument();
    });
});
