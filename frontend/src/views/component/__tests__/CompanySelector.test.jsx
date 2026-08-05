import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";

const mockUseData = jest.fn();
const mockRecordEvent = jest.fn();

jest.mock("../../../service/BackendService", () => ({
    useData: (...args) => mockUseData(...args),
}));

jest.mock("../../../service/utils", () => ({
    recordEvent: (...args) => mockRecordEvent(...args),
}));

jest.mock("../Loader", () => ({
    Loader: (props) => (
        <div data-testid="loader">{props.error ? props.error.message : "loading"}</div>
    ),
}));

import {CompanySelector} from "../CompanySelector";

function createProps(overrides = {}) {
    return {
        refresh: "",
        companies: [
            {id: "company-1", ticker: "NVDA"},
            {id: "company-2", ticker: "SHELL"},
            {id: "company-3", ticker: "TSLA"},
            {id: "company-4", ticker: "CEZ"},
            {id: "company-5", ticker: "AAPL"},
            {id: "company-6", ticker: "XOM"},
        ],
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
        mockUseData.mockReset();
        mockRecordEvent.mockReset();
    });

    test("shows loader while company lists are loading", () => {
        mockUseData.mockReturnValue({
            data: null,
            loaded: false,
            error: {message: "failed"},
        });

        render(<CompanySelector {...createProps()}/>);

        expect(screen.getByTestId("loader")).toHaveTextContent("failed");
        expect(screen.queryByText("Owned")).not.toBeInTheDocument();
    });

    test("renders one company list for every map key and requests refresh data when provided", async () => {
        mockUseData.mockReturnValue({
            data: createData(),
            loaded: true,
            error: null,
        });

        render(<CompanySelector {...createProps({refresh: "123"})}/>);

        expect(mockUseData).toHaveBeenCalledWith("/company/lists?refresh123");
        expect(await screen.findByText("Owned")).toBeInTheDocument();
        expect(screen.getByText("Researched")).toBeInTheDocument();
        expect(screen.getByText("Recent")).toBeInTheDocument();
        expect(screen.getByText("Semiconductors")).toBeInTheDocument();
        expect(screen.getByText("Energy")).toBeInTheDocument();
        expect(screen.getAllByText("NVDA")).toHaveLength(2);
        expect(screen.getByText("TSLA")).toBeInTheDocument();
        expect(screen.getByText("CEZ")).toBeInTheDocument();
    });

    test("provides only custom list keys as tag suggestions", async () => {
        mockUseData.mockReturnValue({
            data: createData(),
            loaded: true,
            error: null,
        });
        const onCustomTagsChange = jest.fn();

        render(<CompanySelector {...createProps({onCustomTagsChange})}/>);

        await waitFor(() => expect(onCustomTagsChange).toHaveBeenCalledWith(["Energy", "Semiconductors"]));
    });

    test("orders built-in lists first and custom lists naturally", async () => {
        mockUseData.mockReturnValue({
            data: createData(),
            loaded: true,
            error: null,
        });

        render(<CompanySelector {...createProps()}/>);

        await screen.findByText("Owned");
        expect(screen.getAllByRole("list").map(list => list.firstChild.textContent)).toEqual([
            "Owned",
            "Recent",
            "Researched",
            "Energy",
            "Semiconductors",
        ]);
    });

    test("selects a company and retains only the source list", async () => {
        mockUseData.mockReturnValue({
            data: createData(),
            loaded: true,
            error: null,
        });

        const setCompanySelectorValue = jest.fn();

        render(<CompanySelector {...createProps({setCompanySelectorValue})}/>);

        fireEvent.click(await screen.findByText("TSLA"));

        expect(setCompanySelectorValue).toHaveBeenCalledWith({id: "company-3", ticker: "TSLA"});
        expect(mockRecordEvent).toHaveBeenCalledWith("/research#selector:companies:owned");

        await waitFor(() => expect(screen.queryByText("CEZ")).not.toBeInTheDocument());
        expect(screen.getByRole("combobox", {name: "Company list"})).toHaveTextContent("Owned");
        expect(screen.getByText("TSLA")).toBeInTheDocument();
    });

    test("switches the retained list from its title", async () => {
        mockUseData.mockReturnValue({
            data: createData(),
            loaded: true,
            error: null,
        });

        render(<CompanySelector {...createProps()}/>);
        fireEvent.click(await screen.findByText("TSLA"));

        const listSelector = await screen.findByRole("combobox", {name: "Company list"});
        fireEvent.mouseDown(listSelector);
        fireEvent.click(screen.getByRole("option", {name: "Energy"}));

        expect(screen.getByRole("combobox", {name: "Company list"})).toHaveTextContent("Energy");
        expect(screen.getByText("XOM")).toBeInTheDocument();
        expect(screen.queryByText("TSLA")).not.toBeInTheDocument();
    });

    test("uses a selected company's first matching list when no source list is active", async () => {
        mockUseData.mockReturnValue({
            data: createData(),
            loaded: true,
            error: null,
        });

        render(<CompanySelector {...createProps({
            companySelectorValue: {id: "company-1", ticker: "NVDA"},
        })}/>);

        expect(await screen.findByRole("combobox", {name: "Company list"})).toHaveTextContent("Recent");
        expect(screen.getByText("NVDA")).toBeInTheDocument();
        expect(screen.queryByText("TSLA")).not.toBeInTheDocument();
    });

    test("shows every list again after clearing the selected company", async () => {
        mockUseData.mockReturnValue({
            data: createData(),
            loaded: true,
            error: null,
        });

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
