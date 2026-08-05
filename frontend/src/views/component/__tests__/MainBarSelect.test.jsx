import React from "react";
import {fireEvent, render, screen} from "@testing-library/react";

const mockRecordEvent = jest.fn();

jest.mock("../../../service/utils", () => ({
    recordEvent: (...args) => mockRecordEvent(...args),
}));

import {MainBarSelect} from "../MainBarSelect";

describe("MainBarSelect", () => {
    const originalLocation = window.location;

    beforeAll(() => {
        delete window.location;
        window.location = {pathname: "/stats"};
    });

    afterAll(() => {
        window.location = originalLocation;
    });

    beforeEach(() => {
        mockRecordEvent.mockReset();
    });

    test("renders label placeholder and primitive options", () => {
        render(
            <MainBarSelect
                values={["2024", "2025"]}
                value=""
                setValue={jest.fn()}
                label="years"
            />
        );

        expect(screen.getByRole("combobox")).toHaveTextContent("years");

        fireEvent.mouseDown(screen.getByRole("combobox"));

        expect(screen.getByRole("option", {name: "clear"})).toHaveAttribute("aria-selected", "false");
        expect(screen.queryByRole("option", {name: "years"})).not.toBeInTheDocument();
        expect(screen.getByRole("option", {name: "2024"})).toBeInTheDocument();
        expect(screen.getByRole("option", {name: "2025"})).toBeInTheDocument();
    });

    test("selects primitive value and records selector event", () => {
        const setValue = jest.fn();

        render(
            <MainBarSelect
                values={["2024", "2025"]}
                value=""
                setValue={setValue}
                label="years"
            />
        );

        fireEvent.mouseDown(screen.getByRole("combobox"));
        fireEvent.click(screen.getByRole("option", {name: "2025"}));

        expect(setValue).toHaveBeenCalledWith("2025");
        expect(mockRecordEvent).toHaveBeenCalledWith("/stats#selector:years");
    });

    test("clears a generic selector while preserving its empty-state label", () => {
        const setValue = jest.fn();
        const props = {
            values: ["only active", "only closed"],
            value: "only active",
            setValue,
            label: "all",
        };
        const {rerender} = render(<MainBarSelect {...props}/>);

        fireEvent.mouseDown(screen.getByRole("combobox"));
        fireEvent.click(screen.getByRole("option", {name: "clear"}));

        expect(setValue).toHaveBeenCalledWith("");

        rerender(<MainBarSelect {...props} value=""/>);

        expect(screen.getByRole("combobox")).toHaveTextContent("all");
    });

    test("renders object options using valueKey", () => {
        render(
            <MainBarSelect
                values={[
                    {ticker: "NVDA"},
                    {ticker: "AAPL"},
                ]}
                value=""
                setValue={jest.fn()}
                label="companies"
                valueKey="ticker"
            />
        );

        fireEvent.mouseDown(screen.getByRole("combobox"));

        expect(screen.getByRole("option", {name: "NVDA"})).toBeInTheDocument();
        expect(screen.getByRole("option", {name: "AAPL"})).toBeInTheDocument();
    });

    test("defaults company selector to all and switches the visible company list from its header", () => {
        const setValue = jest.fn();
        const nvidia = {id: "company-1", ticker: "NVDA"};

        render(
            <MainBarSelect
                companyLists={{
                    owned: [nvidia],
                    recent: [{id: "company-3", ticker: "AMD"}],
                    all: [
                        {id: "company-2", ticker: "AAPL"},
                        nvidia,
                    ],
                }}
                defaultCompanyList="all"
                value=""
                setValue={setValue}
                label="companies"
                valueKey="ticker"
            />
        );

        expect(screen.getByRole("combobox")).toHaveTextContent("company");

        fireEvent.mouseDown(screen.getByRole("combobox"));

        expect(screen.getByRole("option", {name: "clear"})).toHaveAttribute("aria-selected", "false");
        expect(screen.getByRole("button", {name: "Company list All"})).toBeInTheDocument();
        expect(screen.getByRole("option", {name: "AAPL"})).toBeInTheDocument();
        expect(screen.getByRole("option", {name: "NVDA"})).toBeInTheDocument();
        expect(screen.queryByRole("option", {name: "AMD"})).not.toBeInTheDocument();
        expect(screen.queryByRole("option", {name: "companies"})).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Company list All"}));

        expect(screen.getByRole("button", {name: "Company list All"})).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Use company list Owned"}));

        expect(screen.getByRole("button", {name: "Company list Owned"})).toBeInTheDocument();
        expect(screen.getByRole("option", {name: "NVDA"})).toBeInTheDocument();
        expect(screen.queryByRole("option", {name: "AAPL"})).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("option", {name: "NVDA"}));

        expect(setValue).toHaveBeenCalledWith(nvidia);
        expect(mockRecordEvent).toHaveBeenCalledWith("/stats#selector:company-list:owned");
        expect(mockRecordEvent).toHaveBeenCalledWith("/stats#selector:companies");

        fireEvent.mouseDown(screen.getByRole("combobox"));

        expect(screen.getByRole("button", {name: "Company list Owned"})).toBeInTheDocument();
        expect(screen.getByRole("option", {name: "NVDA"})).toBeInTheDocument();
        expect(screen.queryByRole("option", {name: "AAPL"})).not.toBeInTheDocument();
    });

    test("keeps the selected company visible while switching between company lists", () => {
        const companyLists = {
            owned: [{id: "company-1", ticker: "NVDA"}],
            recent: [{id: "company-2", ticker: "AMD"}],
            all: [
                {id: "company-2", ticker: "AMD"},
                {id: "company-1", ticker: "NVDA"},
            ],
        };
        const props = {
            companyLists,
            defaultCompanyList: "all",
            value: {id: "company-1", ticker: "NVDA"},
            setValue: jest.fn(),
            label: "companies",
            valueKey: "ticker",
        };

        render(<MainBarSelect {...props}/>);

        expect(screen.getByRole("combobox", {hidden: true})).toHaveTextContent("NVDA");

        fireEvent.mouseDown(screen.getByRole("combobox"));
        fireEvent.click(screen.getByRole("button", {name: "Company list All"}));

        expect(screen.getByRole("combobox", {hidden: true})).toHaveTextContent("NVDA");

        fireEvent.click(screen.getByRole("button", {name: "Use company list Recent"}));

        expect(screen.getByRole("combobox", {hidden: true})).toHaveTextContent("NVDA");
        expect(screen.getByRole("option", {name: "AMD"})).toBeInTheDocument();
        expect(screen.queryByRole("option", {name: "NVDA"})).not.toBeInTheDocument();
    });

    test("clears the selected company and restores the lowercase placeholder", () => {
        const nvidia = {id: "company-1", ticker: "NVDA"};
        const companyLists = {all: [nvidia]};
        const setValue = jest.fn();
        const props = {
            companyLists,
            defaultCompanyList: "all",
            value: nvidia,
            setValue,
            label: "companies",
            valueKey: "ticker",
        };
        const {rerender} = render(<MainBarSelect {...props}/>);

        expect(screen.getByRole("combobox")).toHaveTextContent("NVDA");

        fireEvent.mouseDown(screen.getByRole("combobox"));
        expect(screen.getByRole("option", {name: "clear"})).toHaveAttribute("aria-selected", "false");
        fireEvent.click(screen.getByRole("option", {name: "clear"}));

        expect(setValue).toHaveBeenCalledWith("");

        rerender(<MainBarSelect {...props} value=""/>);

        expect(screen.getByRole("combobox")).toHaveTextContent("company");
    });

    test("matches selected object by id when value is a different object instance", () => {
        render(
            <MainBarSelect
                values={[
                    {id: "company-1", ticker: "NVDA"},
                    {id: "company-2", ticker: "AAPL"},
                ]}
                value={{id: "company-1", ticker: "NVDA"}}
                setValue={jest.fn()}
                label="companies"
                valueKey="ticker"
            />
        );

        expect(screen.getByRole("combobox")).toHaveTextContent("NVDA");
    });

    test("matches selected object by key when value is a different object instance", () => {
        render(
            <MainBarSelect
                values={[
                    {key: "TECH", name: "Technology"},
                    {key: "ENERGY", name: "Energy"},
                ]}
                value={{key: "TECH", name: "Technology"}}
                setValue={jest.fn()}
                label="sectors"
                valueKey="name"
            />
        );

        expect(screen.getByRole("combobox")).toHaveTextContent("Technology");
    });

    test("renders duplicate option labels without collapsing items", () => {
        render(
            <MainBarSelect
                values={["2024", "2024"]}
                value=""
                setValue={jest.fn()}
                label="years"
            />
        );

        fireEvent.mouseDown(screen.getByRole("combobox"));

        expect(screen.getAllByRole("option", {name: "2024"})).toHaveLength(2);
    });
});
