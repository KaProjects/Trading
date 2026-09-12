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
            <div>lists:{(props.companyLists.all ?? []).map(company => company.ticker).join(",")}</div>
            <div>actionable:{(props.companyLists.actionable ?? [])
                .map(company => `${company.ticker}(${company.importablePeriodsCount}/${company.importableTargetsCount})`)
                .join(",")}</div>
            <button onClick={() => props.updateActionableCompany("company-2", 0, 3)}>update actionable</button>
            <button onClick={() => props.updateActionableCompany("company-2", 0, 0)}>clear actionable</button>
            <button onClick={() => props.updateActionableCompany("company-1", 5, 5)}>update missing</button>
            <div>company:{props.companySelectorValue?.ticker || ""}</div>
            <div>currency:{props.currencySelectorValue || ""}</div>
            <div>years:{props.years.join(",")}</div>
            <div>sector:{props.sectorSelectorValue?.name || ""}</div>
            <div>portfolio:{props.portfolioSelectorValue?.name || ""}</div>
            <button onClick={() => props.setCurrencySelectorValue("$")}>set currency</button>
            <button onClick={() => props.setSectorSelectorValue({key: "TECH", name: "Technology"})}>set sector</button>
            <button onClick={() => props.setCompanySelectorValue({id: "company-1", ticker: "NVDA"})}>set company</button>
            <button onClick={() => props.setCompanySelectorValue("")}>clear company</button>
            <button onClick={() => props.setPortfolioSelectorValue(props.portfolios[0])}>set portfolio</button>
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

describe("App", () => {
    beforeEach(() => {
        window.history.pushState({}, "", "/");
        axios.get.mockImplementation(url => {
            if (url.endsWith("/company/lists/actionable")) {
                return Promise.resolve({
                    data: [{
                        company: {id: "company-2", ticker: "AMD"},
                        importablePeriodsCount: 1,
                        importableTargetsCount: 0,
                    }],
                });
            }
            if (url.endsWith("/company/lists")) {
                return Promise.resolve({data: {all: [{id: "company-1", ticker: "NVDA"}]}});
            }
            return Promise.resolve({
                data: {
                    currencies: ["$"],
                    sectors: [{key: "TECH", name: "Technology"}],
                    exchanges: [{key: "XNAS", name: "Nasdaq", tradingViewCode: "NASDAQ"}],
                    portfolios: [{key: "PATRIA_STANDARD", name: "Patria - Standard", abbreviation: "P"}],
                    years: ["2024", "2023"],
                },
            });
        });
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

    test("merges the actionable company list into the base company lists once it loads", async () => {
        render(<App/>);

        await waitFor(() => expect(screen.getByText("lists:NVDA")).toBeInTheDocument());
        await waitFor(() => expect(screen.getByText("actionable:AMD(1/0)")).toBeInTheDocument());
        expect(screen.getByText("lists:NVDA")).toBeInTheDocument();
    });

    test("updates the actionable entry with counts from the research view", async () => {
        render(<App/>);

        await waitFor(() => expect(screen.getByText("actionable:AMD(1/0)")).toBeInTheDocument());
        fireEvent.click(screen.getByText("update actionable"));

        expect(screen.getByText("actionable:AMD(0/3)")).toBeInTheDocument();
    });

    test("removes the actionable entry when nothing is importable anymore", async () => {
        render(<App/>);

        await waitFor(() => expect(screen.getByText("actionable:AMD(1/0)")).toBeInTheDocument());
        fireEvent.click(screen.getByText("clear actionable"));

        expect(screen.getByText("actionable:")).toBeInTheDocument();
    });

    test("ignores counts for a company that is not in the actionable list", async () => {
        render(<App/>);

        await waitFor(() => expect(screen.getByText("actionable:AMD(1/0)")).toBeInTheDocument());
        fireEvent.click(screen.getByText("update missing"));

        expect(screen.getByText("actionable:AMD(1/0)")).toBeInTheDocument();
    });

    test("stores the selected trade portfolio", async () => {
        render(<App/>);

        await waitFor(() => expect(screen.getByText("home")).toBeInTheDocument());
        fireEvent.click(screen.getByText("set portfolio"));

        expect(screen.getByText("portfolio:Patria - Standard")).toBeInTheDocument();
    });
});
