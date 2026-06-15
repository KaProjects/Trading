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
    CompanySelector: (props) => (
        <div data-testid="company-selector">company-selector:{props.refresh}</div>
    ),
}));
jest.mock("../component/PeriodFinancials", () => ({
    PeriodFinancials: (props) => (
        <div data-testid="period-financials">expand:{String(props.expand)} financials:{props.financials.length}</div>
    ),
}));
jest.mock("../../dialog/AddRecordDialog", () => (props) => props.open ? <div>add-record-dialog</div> : null);
jest.mock("../../dialog/AddPeriodDialog", () => (props) => props.open ? <div>add-period-dialog</div> : null);
jest.mock("../../dialog/AddPeriodFinancialDialog", () => (props) => props.open ? <div>add-period-financial-dialog</div> : null);
jest.mock("../../dialog/ImportPeriodDialog", () => (props) => props.open ? <div>import-period-dialog</div> : null);
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
    Record: ({record}) => <div>record:{record.id}</div>
}));
jest.mock("../component/Period", () => ({
    Period: ({period, openDialog}) => (
        <div>
            <span>period:{period.id}</span>
            <button onClick={openDialog}>open-period-dialog:{period.id}</button>
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
            watching: false,
        },
        financials: [{period: "25FY"}],
        ttm: {
            revenue: 1000,
            grossProfit: 500,
            operatingIncome: 300,
            netIncome: 200,
            dividend: 20,
        },
        periods: [{id: "period-1"}],
        newerCachedPeriods: [],
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
        mockFormatError.mockReset();
        sessionStorage.clear();
    });

    test("fetches data and renders the research view", async () => {
        axios.get.mockResolvedValue({data: createResearchData()});

        const toggleRecordsSelectors = jest.fn();

        render(
            <Research
                companySelectorValue={companySelectorValue}
                toggleRecordsSelectors={toggleRecordsSelectors}
            />
        );

        expect(toggleRecordsSelectors).toHaveBeenCalled();
        expect(screen.getByTestId("loader")).toBeInTheDocument();

        await waitFor(() => expect(axios.get).toHaveBeenCalledWith("http://backend/research/company-1"));
        await waitFor(() => expect(screen.getByText("AAPL")).toBeInTheDocument());

        expect(screen.getByText("Research")).toBeInTheDocument();
        expect(screen.getByText("Technology")).toBeInTheDocument();
        expect(screen.getByTestId("period-financials")).toHaveTextContent("expand:false financials:1");
        expect(screen.getByText("datetime:2026-05-09T10:11:12")).toBeInTheDocument();
        expect(screen.getByText("Market Cap: $1B")).toBeInTheDocument();
        expect(screen.getByText("Dividend Yield: 2%")).toBeInTheDocument();
        expect(screen.getByText("asset:3@100$")).toBeInTheDocument();
        expect(screen.getByText("period:period-1")).toBeInTheDocument();
        expect(screen.getByText("record:record-1")).toBeInTheDocument();
    });

    test("expands financials when session storage requests it", async () => {
        sessionStorage.setItem("showFinancials", "true");
        axios.get.mockResolvedValue({data: createResearchData()});

        render(
            <Research
                companySelectorValue={companySelectorValue}
                toggleRecordsSelectors={jest.fn()}
            />
        );

        await waitFor(() => expect(screen.getByTestId("period-financials")).toHaveTextContent("expand:true financials:1"));
        expect(sessionStorage.getItem("showFinancials")).toBeNull();
    });

    test("updates watching status after confirm", async () => {
        axios.get.mockResolvedValue({data: createResearchData()});
        axios.put.mockResolvedValue({});

        render(
            <Research
                companySelectorValue={companySelectorValue}
                toggleRecordsSelectors={jest.fn()}
            />
        );

        await waitFor(() => expect(screen.getByText("AAPL")).toBeInTheDocument());

        fireEvent.click(screen.getAllByRole("button")[0]);
        expect(screen.getByText("Are you sure to watch the company?")).toBeInTheDocument();

        fireEvent.click(screen.getByText("Confirm"));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("http://backend/company", expect.objectContaining({
            id: "company-1",
            sector: "TECH",
            watching: true,
        })));

        fireEvent.click(screen.getAllByRole("button")[0]);
        expect(screen.getByText("Are you sure to unwatch the company?")).toBeInTheDocument();
    });

    test("shows formatted fetch error in loader", async () => {
        const formatted = {title: "Failed", message: "network"};
        const error = {name: "AxiosError", message: "boom"};
        axios.get.mockRejectedValue(error);
        mockFormatError.mockReturnValue(formatted);

        render(
            <Research
                companySelectorValue={companySelectorValue}
                toggleRecordsSelectors={jest.fn()}
            />
        );

        await waitFor(() => expect(mockFormatError).toHaveBeenCalledWith(error));
        expect(screen.getByTestId("loader")).toHaveTextContent(JSON.stringify(formatted));
    });
});
