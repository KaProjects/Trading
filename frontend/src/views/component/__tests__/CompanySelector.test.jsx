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

jest.mock("../../../components/Loader", () => (props) => (
    <div data-testid="loader">{props.error ? props.error.message : "loading"}</div>
));

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
        watching: [
            {id: "company-1", ticker: "NVDA", latestRecordDate: "2024-03-15"},
            {id: "company-2", ticker: "SHELL", latestRecordDate: "2024-02-01"},
        ],
        owned: [
            {id: "company-3", ticker: "TSLA", latestPurchaseDate: "2024-04-20"},
        ],
        unreported: [
            {id: "company-4", ticker: "CEZ", latestUnreportedPeriodEndingMonth: "2025-01"},
        ],
        deprecated: [
            {id: "company-5", ticker: "AAPL", latestRecordDate: "2023-12-31"},
        ],
        sectors: {
            Semiconductors: [
                {id: "company-1", ticker: "NVDA", latestRecordDate: "2024-03-15"},
            ],
            Energy: [
                {id: "company-6", ticker: "XOM", latestRecordDate: "2024-01-20"},
            ],
        },
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

    test("shows loader while company groups are loading", () => {
        mockUseData.mockReturnValue({
            data: null,
            loaded: false,
            error: {message: "failed"},
        });

        render(<CompanySelector {...createProps()}/>);

        expect(screen.getByTestId("loader")).toHaveTextContent("failed");
        expect(screen.queryByText("Watching")).not.toBeInTheDocument();
    });

    test("renders all company groups and requests refresh data when provided", async () => {
        mockUseData.mockReturnValue({
            data: createData(),
            loaded: true,
            error: null,
        });

        render(<CompanySelector {...createProps({refresh: "123"})}/>);

        expect(mockUseData).toHaveBeenCalledWith("/company/lists?refresh123");
        expect(await screen.findByText("Watching")).toBeInTheDocument();
        expect(screen.getByText("Owned")).toBeInTheDocument();
        expect(screen.getByText("Not Reported")).toBeInTheDocument();
        expect(screen.getByText("Deprecated")).toBeInTheDocument();
        expect(screen.getByRole("combobox")).toHaveTextContent("Semiconductors");
        expect(screen.getAllByText("NVDA")).toHaveLength(2);
        expect(screen.getByText("SHELL")).toBeInTheDocument();
        expect(screen.getByText("TSLA")).toBeInTheDocument();
        expect(screen.getByText("CEZ")).toBeInTheDocument();
        expect(screen.getByText("AAPL")).toBeInTheDocument();
    });

    test("selects clicked company and records selector event", async () => {
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

        await waitFor(() => expect(screen.queryByText("Watching")).not.toBeInTheDocument());
        expect(screen.getByText("Owned")).toBeInTheDocument();
        expect(screen.queryByText("Deprecated")).not.toBeInTheDocument();
    });

    test("changes displayed sector companies when another sector is selected", async () => {
        mockUseData.mockReturnValue({
            data: createData(),
            loaded: true,
            error: null,
        });

        render(<CompanySelector {...createProps()}/>);

        fireEvent.mouseDown(await screen.findByRole("combobox"));
        fireEvent.click(screen.getByRole("option", {name: "Energy"}));

        expect(screen.getByRole("combobox")).toHaveTextContent("Energy");
        expect(screen.getByText("XOM")).toBeInTheDocument();
        expect(screen.queryAllByText("NVDA")).toHaveLength(1);
    });

    test("resets to the first sector when refreshed data arrives", async () => {
        const useDataResponse = {
            data: createData(),
            loaded: true,
            error: null,
        };

        mockUseData.mockImplementation(() => useDataResponse);

        const {rerender} = render(<CompanySelector {...createProps()}/>);

        fireEvent.mouseDown(await screen.findByRole("combobox"));
        fireEvent.click(screen.getByRole("option", {name: "Energy"}));

        expect(screen.getByRole("combobox")).toHaveTextContent("Energy");
        expect(screen.getByText("XOM")).toBeInTheDocument();

        useDataResponse.data = createData({
            watching: [
                {id: "company-1", ticker: "NVDA", latestRecordDate: "2024-05-01"},
                {id: "company-2", ticker: "SHELL", latestRecordDate: "2024-02-01"},
            ],
        });

        rerender(<CompanySelector {...createProps()}/>);

        await waitFor(() => expect(screen.getByRole("combobox")).toHaveTextContent("Semiconductors"));
        expect(screen.getAllByText("NVDA")).toHaveLength(2);
        expect(screen.queryByText("XOM")).not.toBeInTheDocument();
    });

    test("hides lists while a company is selected and shows them again after clearing selection", async () => {
        mockUseData.mockReturnValue({
            data: createData(),
            loaded: true,
            error: null,
        });

        const {rerender} = render(<CompanySelector {...createProps({
            companySelectorValue: {id: "company-1", ticker: "NVDA"},
        })}/>);

        await waitFor(() => expect(screen.queryByText("Watching")).not.toBeInTheDocument());
        expect(screen.queryByText("Owned")).not.toBeInTheDocument();

        rerender(<CompanySelector {...createProps({
            companySelectorValue: null,
        })}/>);

        expect(await screen.findByText("Watching")).toBeInTheDocument();
        expect(screen.getByText("Owned")).toBeInTheDocument();
    });
});
