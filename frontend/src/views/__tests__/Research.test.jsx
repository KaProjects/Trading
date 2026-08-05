import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

jest.mock("axios");
jest.mock("../../properties", () => ({
    backend: "http://backend",
}));

jest.mock("../component/Loader", () => ({
    Loader: (props) => (
        <div data-testid="loader">{props.error ? JSON.stringify(props.error) : "loading"}</div>
    ),
}));
jest.mock("../component/CompanySelector", () => ({
    BUILT_IN_LIST_TITLES: {owned: "Owned", recent: "Recent", researched: "Researched", all: "All"},
    CompanySelector: (props) => (
        <div data-testid="company-selector">
            company-selector:{props.refresh}
            <button onClick={() => props.onCustomTagsChange(["growth", "income"])}>provide-tags</button>
        </div>
    ),
}));
jest.mock("../component/PeriodFinancials", () => ({
    PeriodFinancials: (props) => (
        <button data-testid="period-financials" onClick={props.onOpen}>financial-overview</button>
    ),
}));
jest.mock("../component/PeriodEstimatesOverview", () => ({
    PeriodEstimatesOverview: (props) => (
        <button data-testid="period-estimates-overview" onClick={props.onOpen}>estimate-overview:{props.overview?.current?.value}</button>
    ),
}));
jest.mock("../../dialog/FinancialsDialog", () => ({
    FinancialsDialog: (props) => props.open ? <div>financials-dialog:{props.ticker}:{props.financials.length}</div> : null,
}));
jest.mock("../../dialog/EarningsProjectionsDialog", () => ({
    EarningsProjectionsDialog: (props) => props.open
        ? <div>earnings-projections-dialog:{props.ticker}:{props.currentPrice}:{props.latestPeriod.name}:{props.latestPeriod.estimate.current}:{props.previousPeriod.priceHigh}</div>
        : null,
}));
jest.mock("../../dialog/AddRecordDialog", () => ({
    AddRecordDialog: (props) => props.open ? <div>add-record-dialog</div> : null
}));
jest.mock("../../dialog/AddPeriodDialog", () => ({
    AddPeriodDialog: (props) => props.open ? <div>add-period-dialog</div> : null
}));
jest.mock("../../dialog/AddPeriodFinancialDialog", () => ({
    AddPeriodFinancialDialog: (props) => props.open ? <div>add-period-financial-dialog</div> : null
}));
jest.mock("../../dialog/ImportPeriodDialog", () => ({
    ImportPeriodDialog: (props) => props.open
        ? <div>import-period-dialog:{props.periods.map(period => period.name).join(",")}</div>
        : null
}));
jest.mock("../../dialog/AddEstimateDialog", () => ({
    AddEstimateDialog: (props) => props.open
        ? <div>add-estimate-dialog:{props.period.id}</div>
        : null
}));
jest.mock("../../dialog/AddTagDialog", () => ({
    AddTagDialog: (props) => props.open
        ? <div>add-tag-dialog:{props.companyId}:{props.suggestions.join(",")}</div>
        : null,
}));
jest.mock("../component/SnackbarErrorAlert", () => ({
    SnackbarErrorAlert: (props) => (
        <div data-testid="snackbar">{props.error ? JSON.stringify(props.error) : "null"}|{String(props.open)}</div>
    )
}));
jest.mock("../component/AssetBox", () => ({
    AssetBox: ({asset, currency}) => <div>asset:{asset.quantity}@{asset.purchasePrice}{currency}</div>
}));
jest.mock("../component/DateTime", () => ({
    DateTime: ({value}) => <div>datetime:{value}</div>
}));
jest.mock("../component/Record", () => ({
    Record: ({data, deleteRecord}) => (
        <div>
            <span>record:{data.id}</span>
            <button onClick={() => deleteRecord(data.id)}>delete:{data.id}</button>
        </div>
    )
}));
jest.mock("../component/Period", () => ({
    Period: ({period, openDialog, openEstimateDialog}) => (
        <div>
            <span>period:{period.id}</span>
            <button onClick={openDialog}>open-period-dialog:{period.id}</button>
            <button onClick={openEstimateDialog}>open-estimate-dialog:{period.id}</button>
        </div>
    )
}));

const mockFormatError = jest.fn();

jest.mock("../../service/FormattingService", () => {
    const actual = jest.requireActual("../../service/FormattingService");
    return {
        ...actual,
        formatError: (...args) => mockFormatError(...args),
    };
});

import {Research} from "../Research";

const companySelectorValue = {id: "company-1"};

function createResearchData(overrides = {}) {
    return {
        company: {
            id: "company-1",
            ticker: "AAPL",
            currency: "$",
            sector: {key: "TECH", name: "Technology"},
            tags: ["growth", "owned", "recent", "researched"],
        },
        financials: [{period: "25FY"}],
        ttm: {
            revenue: 1000,
            grossProfit: 500,
            operatingIncome: 300,
            netIncome: 200,
            dividend: 20,
        },
        estimateOverview: {
            ttm: {value: 10},
            current: {value: 14, change: 40},
            next1: {value: 18, change: 28.57},
            next2: {value: 22, change: 22.22},
            next3: {value: 26, change: 18.18},
        },
        periods: [
            {
                id: "period-1",
                name: "26Q2",
                estimate: {
                    past4: 1,
                    past3: 2,
                    past2: 3,
                    past1: 4,
                    current: 5,
                    next1: 6,
                    next2: 7,
                    next3: 8,
                },
            },
            {id: "period-2", priceHigh: 140, priceLow: 80},
        ],
        importablePeriods: [],
        latest: {
            price: 123.45,
            datetime: "2026-05-09T10:11:12",
        },
        indicators: {
            marketCap: 1000,
            ttm: {
                dividendYield: 2,
                marketCapToRevenues: 3,
                marketCapToGrossProfit: 4,
                marketCapToOperatingIncome: 5,
                marketCapToNetIncome: 6,
            },
        },
        assets: {
            assets: [{quantity: 3, purchasePrice: 100}],
        },
        records: [{id: "record-1"}],
        ...overrides,
    };
}

describe("Research", () => {
    beforeEach(() => {
        axios.get.mockReset();
        axios.put.mockReset();
        axios.delete.mockReset();
        mockFormatError.mockReset();
    });

    test("fetches data and renders the research view", async () => {
        axios.get.mockResolvedValue({data: createResearchData()});

        render(
            <Research
                companySelectorValue={companySelectorValue}
            />
        );

        expect(screen.getByTestId("loader")).toBeInTheDocument();

        await waitFor(() => expect(axios.get).toHaveBeenCalledWith("http://backend/research/company-1"));
        await waitFor(() => expect(screen.getByText("AAPL")).toBeInTheDocument());

        expect(screen.getByText("Research")).toBeInTheDocument();
        expect(screen.getByText("Technology")).toBeInTheDocument();
        expect(screen.getByText("#growth")).toBeInTheDocument();
        expect(screen.queryByText("#owned")).not.toBeInTheDocument();
        expect(screen.getByTestId("period-financials")).toHaveTextContent("financial-overview");
        expect(screen.getByTestId("period-estimates-overview")).toHaveTextContent("estimate-overview:14");
        expect(screen.getByText("datetime:2026-05-09T10:11:12")).toBeInTheDocument();
        expect(screen.getByText("Market Cap: $1B")).toBeInTheDocument();
        expect(screen.getByText("Dividend Yield: 2%")).toBeInTheDocument();
        expect(screen.getByText("asset:3@100$")).toBeInTheDocument();
        expect(screen.getByText("period:period-1")).toBeInTheDocument();
        expect(screen.getByText("record:record-1")).toBeInTheDocument();
    });

    test("opens add tag with custom list suggestions", async () => {
        axios.get.mockResolvedValue({data: createResearchData()});

        render(<Research companySelectorValue={companySelectorValue}/>);

        await screen.findByText("AAPL");
        fireEvent.click(screen.getByText("provide-tags"));
        fireEvent.click(screen.getByRole("button", {name: "Add tag"}));

        expect(screen.getByText("add-tag-dialog:company-1:growth,income")).toBeInTheDocument();
    });

    test("opens financials dialog from the overview", async () => {
        axios.get.mockResolvedValue({data: createResearchData()});

        render(
            <Research
                companySelectorValue={companySelectorValue}
            />
        );

        await screen.findByTestId("period-financials");
        fireEvent.click(screen.getByTestId("period-financials"));
        expect(screen.getByText("financials-dialog:AAPL:1")).toBeInTheDocument();
    });

    test("opens earnings projections dialog from the estimates overview", async () => {
        axios.get.mockResolvedValue({data: createResearchData()});

        render(<Research companySelectorValue={companySelectorValue}/>);

        await screen.findByTestId("period-estimates-overview");
        fireEvent.click(screen.getByTestId("period-estimates-overview"));
        expect(screen.getByText("earnings-projections-dialog:AAPL:123.45:26Q2:5:140")).toBeInTheDocument();
    });

    test("opens the estimate dialog for a period", async () => {
        axios.get.mockResolvedValue({data: createResearchData()});

        render(<Research companySelectorValue={companySelectorValue}/>);

        await screen.findByText("open-estimate-dialog:period-1");
        fireEvent.click(screen.getByText("open-estimate-dialog:period-1"));

        expect(screen.getByText("add-estimate-dialog:period-1")).toBeInTheDocument();
    });

    test("shows the import count and opens the dialog with lightweight period names", async () => {
        axios.get.mockResolvedValue({data: createResearchData({
            importablePeriods: [{name: "26Q3"}, {name: "26Q4"}],
        })});

        render(
            <Research
                companySelectorValue={companySelectorValue}
            />
        );

        await waitFor(() => expect(screen.getByText("AAPL")).toBeInTheDocument());

        expect(screen.getByText("2")).toBeInTheDocument();
        fireEvent.click(screen.getByTestId("CloudDownloadIcon").closest("button"));

        expect(screen.getByText("import-period-dialog:26Q3,26Q4")).toBeInTheDocument();
    });

    test("hides the import button when there are no importable periods", async () => {
        axios.get.mockResolvedValue({data: createResearchData()});

        render(<Research companySelectorValue={companySelectorValue}/>);

        await screen.findByText("AAPL");
        expect(screen.queryByTestId("CloudDownloadIcon")).not.toBeInTheDocument();
    });

    test("shows formatted fetch error in loader", async () => {
        const formatted = {title: "Failed", message: "network"};
        const error = {name: "AxiosError", message: "boom"};
        axios.get.mockRejectedValue(error);
        mockFormatError.mockReturnValue(formatted);

        render(
            <Research
                companySelectorValue={companySelectorValue}
            />
        );

        await waitFor(() => expect(mockFormatError).toHaveBeenCalledWith(error));
        expect(screen.getByTestId("loader")).toHaveTextContent(JSON.stringify(formatted));
    });

    test("deletes record and removes it from the current view", async () => {
        axios.get.mockResolvedValue({data: createResearchData({
            records: [{id: "record-1"}, {id: "record-2"}],
        })});
        axios.delete.mockResolvedValue({});

        render(
            <Research
                companySelectorValue={companySelectorValue}
            />
        );

        await waitFor(() => expect(screen.getByText("record:record-1")).toBeInTheDocument());
        expect(screen.getByText("record:record-2")).toBeInTheDocument();

        fireEvent.click(screen.getByText("delete:record-1"));

        await waitFor(() => expect(axios.delete).toHaveBeenCalledWith("http://backend/record/record-1"));
        await waitFor(() => expect(screen.queryByText("record:record-1")).not.toBeInTheDocument());
        expect(screen.getByText("record:record-2")).toBeInTheDocument();
    });

    test("shows formatted error when record delete fails", async () => {
        const formatted = {title: "Delete failed", message: "record could not be deleted"};
        const error = {name: "AxiosError", message: "boom"};
        axios.get.mockResolvedValue({data: createResearchData()});
        axios.delete.mockRejectedValue(error);
        mockFormatError.mockReturnValue(formatted);

        render(
            <Research
                companySelectorValue={companySelectorValue}
            />
        );

        await waitFor(() => expect(screen.getByText("record:record-1")).toBeInTheDocument());

        fireEvent.click(screen.getByText("delete:record-1"));

        await waitFor(() => expect(mockFormatError).toHaveBeenCalledWith(error));
        expect(screen.getByTestId("snackbar")).toHaveTextContent(JSON.stringify(formatted));
        expect(screen.getByText("record:record-1")).toBeInTheDocument();
    });
});
