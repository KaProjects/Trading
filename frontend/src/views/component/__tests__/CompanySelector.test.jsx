import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";

const mockRecordEvent = jest.fn();

jest.mock("../../../service/utils", () => ({
    recordEvent: (...args) => mockRecordEvent(...args),
}));

import {CompanySelector} from "../CompanySelector";

function createProps(overrides = {}) {
    return {
        companyLists: createData(),
        companySelectorValue: null,
        setCompanySelectorValue: jest.fn(),
        ...overrides,
    };
}

function createData(overrides = {}) {
    return {
        owned: [
            {id: "company-3", ticker: "TSLA", latestPurchaseDate: "2024-04-20"},
        ],
        recent: [
            {id: "company-1", ticker: "NVDA", latestRecordDate: "2024-03-15"},
        ],
        researched: [
            {id: "company-4", ticker: "CEZ", latestPeriodEndingMonth: "2025-01"},
        ],
        Energy: [
            {id: "company-6", ticker: "XOM", latestRecordDate: "2024-01-20"},
        ],
        Semiconductors: [
            {id: "company-1", ticker: "NVDA", latestRecordDate: "2024-03-15"},
        ],
        all: [
            {id: "company-5", ticker: "AAPL"},
            {id: "company-4", ticker: "CEZ"},
            {id: "company-1", ticker: "NVDA"},
            {id: "company-2", ticker: "SHELL"},
            {id: "company-3", ticker: "TSLA"},
            {id: "company-6", ticker: "XOM"},
        ],
        ...overrides,
    };
}

describe("CompanySelector", () => {
    const originalLocation = window.location;

    beforeAll(() => {
        delete window.location;
        window.location = {pathname: "/research"};
    });

    afterAll(() => {
        window.location = originalLocation;
    });

    beforeEach(() => {
        mockRecordEvent.mockReset();
    });

    test("renders one company list for every map key", async () => {
        render(<CompanySelector {...createProps()}/>);

        expect(await screen.findByText("Owned")).toBeInTheDocument();
        expect(screen.getByText("Researched")).toBeInTheDocument();
        expect(screen.getByText("Recent")).toBeInTheDocument();
        expect(screen.getByText("Semiconductors")).toBeInTheDocument();
        expect(screen.getByText("Energy")).toBeInTheDocument();
        expect(screen.getByText("All")).toBeInTheDocument();
        expect(screen.getAllByText("NVDA")).toHaveLength(3);
        expect(screen.getAllByText("TSLA")).toHaveLength(2);
        expect(screen.getAllByText("CEZ")).toHaveLength(2);
    });

    test("provides only custom list keys as tag suggestions", async () => {
        const onCustomTagsChange = jest.fn();

        render(<CompanySelector {...createProps({onCustomTagsChange})}/>);

        await waitFor(() => expect(onCustomTagsChange).toHaveBeenCalledWith(["Energy", "Semiconductors"]));
    });

    test("orders built-in lists first and custom lists naturally", async () => {
        render(<CompanySelector {...createProps()}/>);

        await screen.findByText("Owned");
        expect(screen.getAllByRole("list").map(list => list.firstChild.textContent)).toEqual([
            "Owned",
            "Recent",
            "Researched",
            "Energy",
            "Semiconductors",
            "All",
        ]);
    });

    test("shows secondary dates only for recent and researched lists", async () => {
        render(<CompanySelector {...createProps()}/>);

        expect(await screen.findByText("2024-03-15")).toBeInTheDocument();
        expect(screen.getByText("2025-01")).toBeInTheDocument();
        expect(screen.queryByText("2024-04-20")).not.toBeInTheDocument();
        expect(screen.queryByText("2024-01-20")).not.toBeInTheDocument();
    });

    test("selects a company and retains only the source list", async () => {
        const setCompanySelectorValue = jest.fn();

        render(<CompanySelector {...createProps({setCompanySelectorValue})}/>);

        fireEvent.click((await screen.findAllByText("TSLA"))[0]);

        expect(setCompanySelectorValue).toHaveBeenCalledWith({id: "company-3", ticker: "TSLA"});
        expect(mockRecordEvent).toHaveBeenCalledWith("/research#selector:companies:owned");

        await waitFor(() => expect(screen.queryByText("CEZ")).not.toBeInTheDocument());
        expect(screen.getByRole("combobox", {name: "Company list"})).toHaveTextContent("Owned");
        expect(screen.getByText("TSLA")).toBeInTheDocument();
    });

    test("switches the retained list from its title", async () => {
        render(<CompanySelector {...createProps()}/>);
        fireEvent.click((await screen.findAllByText("TSLA"))[0]);

        const listSelector = await screen.findByRole("combobox", {name: "Company list"});
        fireEvent.mouseDown(listSelector);
        fireEvent.click(screen.getByRole("option", {name: "Energy"}));

        expect(screen.getByRole("combobox", {name: "Company list"})).toHaveTextContent("Energy");
        expect(screen.getByText("XOM")).toBeInTheDocument();
        expect(screen.queryByText("TSLA")).not.toBeInTheDocument();
    });

    test("uses a selected company's first matching list when no source list is active", async () => {
        render(<CompanySelector {...createProps({
            companySelectorValue: {id: "company-1", ticker: "NVDA"},
        })}/>);

        expect(await screen.findByRole("combobox", {name: "Company list"})).toHaveTextContent("Recent");
        expect(screen.getByText("NVDA")).toBeInTheDocument();
        expect(screen.queryByText("TSLA")).not.toBeInTheDocument();
    });

    test("shows every list again after clearing the selected company", async () => {
        const {rerender} = render(<CompanySelector {...createProps({
            companySelectorValue: {id: "company-1", ticker: "NVDA"},
        })}/>);

        await screen.findByRole("combobox", {name: "Company list"});

        rerender(<CompanySelector {...createProps({companySelectorValue: null})}/>);

        expect(await screen.findByText("Owned")).toBeInTheDocument();
        expect(screen.getByText("Researched")).toBeInTheDocument();
        expect(screen.getByText("Semiconductors")).toBeInTheDocument();
        expect(screen.queryByRole("combobox", {name: "Company list"})).not.toBeInTheDocument();
    });
});
