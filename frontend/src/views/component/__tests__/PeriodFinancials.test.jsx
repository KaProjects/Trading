import {fireEvent, render, screen} from "@testing-library/react";
import {FinancialsTable, PeriodFinancials} from "../PeriodFinancials";

const ttm = {
    revenue: {value: 1500, margin: 100},
    grossProfit: {value: 600, margin: 40},
    operatingIncome: {value: 300, margin: 20},
    netIncome: {value: 150, margin: 10},
    dividend: 25,
    dividendMargin: 2,
    capex: {value: 50, margin: 3},
    freeCashFlow: {value: 120, margin: 8},
};

const indicators = {
    marketCapToRevenues: 3.456,
    marketCapToGrossProfit: 12.34,
    marketCapToOperatingIncome: 45.67,
    marketCapToNetIncome: 123.4,
    marketCapToFreeCashFlow: 9.87,
    dividendYield: 2.5,
};

const completeQuarterFinancials = [
    {period: {year: "2025", type: "Q4"}, dividend: 7, capex: {value: 14}, freeCashFlow: {value: 32}},
    {period: {year: "2025", type: "Q3"}, dividend: 6, capex: {value: 13}, freeCashFlow: {value: 31}},
    {period: {year: "2025", type: "Q2"}, dividend: 6, capex: {value: 12}, freeCashFlow: {value: 29}},
    {period: {year: "2025", type: "Q1"}, dividend: 6, capex: {value: 11}, freeCashFlow: {value: 28}},
];

const financials = [{
    period: {year: "2025", type: "FY"},
    revenue: {value: 1500, margin: 100, yoy: 25},
    grossProfit: {value: 600, margin: 40, yoy: 50, qoq: 20},
    operatingIncome: {value: 300, margin: 20, yoy: -10},
    netIncome: {value: 150, margin: 10, qoq: 0},
    dividend: 25,
    capex: {value: -50, margin: -3},
    freeCashFlow: {value: 100, margin: 7, yoy: 10, qoq: 5},
}];

describe("PeriodFinancials", () => {
    test("renders only the compact summary and opens financials", () => {
        const onOpen = jest.fn();
        render(<PeriodFinancials ttm={ttm} financials={completeQuarterFinancials} onOpen={onOpen}/>);

        expect(screen.getByText("Financials")).toBeInTheDocument();
        expect(screen.queryByText("TTM")).not.toBeInTheDocument();
        expect(screen.queryByText("(100%)")).not.toBeInTheDocument();
        expect(screen.getByText("1.5B")).toBeInTheDocument();
        expect(screen.getByText("op. income")).toBeInTheDocument();
        expect(screen.getByText("net income")).toBeInTheDocument();
        expect(screen.getByText("dividend")).toBeInTheDocument();
        expect(screen.getByText("capex")).toBeInTheDocument();
        expect(screen.getByText("fcf")).toBeInTheDocument();
        expect(screen.getByText("120M")).toBeInTheDocument();
        expect(screen.queryByText("Dividend")).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Open financials"}));
        expect(onOpen).toHaveBeenCalled();
    });

    test("renders nothing without trailing financials", () => {
        const {container} = render(<PeriodFinancials ttm={null} onOpen={jest.fn()}/>);

        expect(container).toBeEmptyDOMElement();
    });

    test("omits free cash flow from the summary when it has no value", () => {
        render(<PeriodFinancials
            ttm={{...ttm, freeCashFlow: {value: null, margin: null}}}
            financials={completeQuarterFinancials}
            onOpen={jest.fn()}
        />);

        expect(screen.queryByText("fcf")).not.toBeInTheDocument();
    });

    test("omits optional profit values from the summary when they are absent", () => {
        render(<PeriodFinancials
            ttm={{
                ...ttm,
                grossProfit: {value: null, margin: null},
                operatingIncome: {value: null, margin: null},
            }}
            onOpen={jest.fn()}
        />);

        expect(screen.queryByText("gross profit")).not.toBeInTheDocument();
        expect(screen.queryByText("op. income")).not.toBeInTheDocument();
        expect(screen.getByText("net income")).toBeInTheDocument();
    });

    test("omits profit values and the dividend from the summary when they are zero", () => {
        render(<PeriodFinancials
            ttm={{
                ...ttm,
                grossProfit: {value: 0, margin: 0},
                operatingIncome: {value: 0, margin: 0},
                dividend: 0,
                dividendMargin: 0,
            }}
            financials={completeQuarterFinancials}
            onOpen={jest.fn()}
        />);

        expect(screen.queryByText("gross profit")).not.toBeInTheDocument();
        expect(screen.queryByText("op. income")).not.toBeInTheDocument();
        expect(screen.queryByText("dividend")).not.toBeInTheDocument();
        expect(screen.getByText("revenue")).toBeInTheDocument();
        expect(screen.getByText("net income")).toBeInTheDocument();
    });

    test.each([
        ["fewer than four quarters", completeQuarterFinancials.slice(0, 3)],
        ["a gap in the latest quarters", [
            completeQuarterFinancials[0],
            completeQuarterFinancials[1],
            {...completeQuarterFinancials[2], period: {year: "2025", type: "Q1"}},
            {...completeQuarterFinancials[3], period: {year: "2024", type: "Q4"}},
        ]],
    ])("omits annual-flow metrics for %s", (description, latestFinancials) => {
        render(<PeriodFinancials ttm={ttm} financials={latestFinancials} onOpen={jest.fn()}/>);

        expect(screen.queryByText("dividend")).not.toBeInTheDocument();
        expect(screen.queryByText("capex")).not.toBeInTheDocument();
        expect(screen.queryByText("fcf")).not.toBeInTheDocument();
    });

    test("omits only the annual-flow metric with a missing latest value", () => {
        const latestFinancials = completeQuarterFinancials.map((financial, index) => index === 2
            ? {...financial, dividend: null}
            : financial);

        render(<PeriodFinancials ttm={ttm} financials={latestFinancials} onOpen={jest.fn()}/>);

        expect(screen.queryByText("dividend")).not.toBeInTheDocument();
        expect(screen.getByText("capex")).toBeInTheDocument();
        expect(screen.getByText("fcf")).toBeInTheDocument();
    });

    test("shows one decimal only for fractional margins below ten percent", () => {
        render(<PeriodFinancials
            ttm={{
                ...ttm,
                dividendMargin: 2.44,
                capex: {...ttm.capex, margin: 8},
                freeCashFlow: {...ttm.freeCashFlow, margin: 10.6},
            }}
            financials={completeQuarterFinancials}
            onOpen={jest.fn()}
        />);

        expect(screen.getByText("(2.4%)")).toBeInTheDocument();
        expect(screen.getByText("(8%)")).toBeInTheDocument();
        expect(screen.getByText("(11%)")).toBeInTheDocument();
    });

    test("shows the price indicators below the summary values", () => {
        render(<PeriodFinancials
            ttm={ttm}
            financials={completeQuarterFinancials}
            indicators={indicators}
            onOpen={jest.fn()}
        />);

        expect(screen.getByText("3.46x")).toBeInTheDocument();
        expect(screen.getByText("12.3x")).toBeInTheDocument();
        expect(screen.getByText("45.7x")).toBeInTheDocument();
        expect(screen.getByText("123x")).toBeInTheDocument();
        expect(screen.getByText("9.87x")).toBeInTheDocument();
        expect(screen.getByText("2.5%")).toBeInTheDocument();
        expect(screen.getByText("capex").parentElement).toHaveTextContent(/^\(3%\)50Mcapex$/);
    });

    test("omits price indicators that are not set", () => {
        render(<PeriodFinancials
            ttm={ttm}
            financials={completeQuarterFinancials}
            indicators={{
                marketCapToRevenues: 3.456,
                marketCapToGrossProfit: null,
                marketCapToOperatingIncome: undefined,
                marketCapToNetIncome: 0,
                marketCapToFreeCashFlow: 9.87,
                dividendYield: 0,
            }}
            onOpen={jest.fn()}
        />);

        expect(screen.getByText("3.46x")).toBeInTheDocument();
        expect(screen.getByText("9.87x")).toBeInTheDocument();
        expect(screen.getByText("gross profit").parentElement).toHaveTextContent(/^\(40%\)600Mgross profit$/);
        expect(screen.getByText("op. income").parentElement).toHaveTextContent(/^\(20%\)300Mop. income$/);
        expect(screen.getByText("net income").parentElement).toHaveTextContent(/^\(10%\)150Mnet income$/);
        expect(screen.getByText("dividend").parentElement).toHaveTextContent(/^\(2%\)25Mdividend$/);
    });

    test("shows the market cap at the right edge of the summary", () => {
        render(<PeriodFinancials
            ttm={ttm}
            financials={completeQuarterFinancials}
            indicators={indicators}
            marketCap={4234}
            onOpen={jest.fn()}
        />);

        const label = screen.getByText("m. cap.");
        expect(label.parentElement).toHaveTextContent(/^m. cap.4.23B$/);
        expect(label.parentElement).toHaveStyle("margin-left: auto");
    });

    test("omits the market cap when it is not set", () => {
        render(<PeriodFinancials ttm={ttm} financials={completeQuarterFinancials} onOpen={jest.fn()}/>);

        expect(screen.queryByText("m. cap.")).not.toBeInTheDocument();
    });

    test("renders the detailed financial table separately", () => {
        render(<FinancialsTable financials={financials} fontSize={16}/>);

        expect(screen.getByText("Period")).toBeInTheDocument();
        expect(screen.getByText("Revenue")).toBeInTheDocument();
        expect(screen.getByText("CapEx")).toBeInTheDocument();
        expect(screen.getByText("FCF")).toBeInTheDocument();
        expect(screen.getByText("25FY")).toBeInTheDocument();
        expect(screen.getByText("+50% / +20%")).toBeInTheDocument();
        expect(screen.getByText("+10% / +5%")).toBeInTheDocument();
    });

    test("shows a dash when a dividend is zero or absent", () => {
        render(<FinancialsTable financials={[
            {...financials[0], period: {year: "2025", type: "Q1"}, dividend: 0},
            {...financials[0], period: {year: "2024", type: "Q4"}, dividend: null},
        ]}/>);

        expect(screen.getAllByText("-")).toHaveLength(2);
    });

    test("shows a dash when CapEx or free cash flow is absent", () => {
        render(<FinancialsTable financials={[{
            ...financials[0],
            capex: {value: null, margin: null},
            freeCashFlow: {value: null, margin: null},
        }]}/>);

        expect(screen.getAllByText("-")).toHaveLength(2);
    });

    test("shows a dash when gross profit or operating income is absent", () => {
        render(<FinancialsTable financials={[{
            ...financials[0],
            grossProfit: {value: null, margin: null},
            operatingIncome: {value: null, margin: null},
        }]}/>);

        expect(screen.getAllByText("-")).toHaveLength(2);
    });
});
