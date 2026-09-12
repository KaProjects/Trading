import {fireEvent, render, screen} from "@testing-library/react";
import {PeriodEstimatesOverview, formatRevenueEstimateAmount} from "../PeriodEstimatesOverview";

const overview = {
    ttm: {value: 10, change: null},
    current: {value: 14, change: 40},
    next1: {value: 18, change: 28.57},
    next2: {value: 22, change: 22.22},
    next3: {value: 26, change: 18.18},
    yearOverYearChange: 160,
};

describe("PeriodEstimatesOverview", () => {
    test("renders rolling four-quarter values and backend changes", () => {
        const onOpen = jest.fn();
        render(<PeriodEstimatesOverview overview={overview} onOpen={onOpen}/>);

        expect(screen.getByText("Earnings estimates")).toBeInTheDocument();
        expect(screen.getByText("ttm")).toBeInTheDocument();
        expect(screen.getByText("current")).toBeInTheDocument();
        expect(screen.getByText("next 1")).toBeInTheDocument();
        expect(screen.getByText("next 2")).toBeInTheDocument();
        expect(screen.getByText("next 3")).toBeInTheDocument();
        expect(screen.getByText("10")).toBeInTheDocument();
        expect(screen.getByText("14")).toBeInTheDocument();
        expect(screen.getByText("(+40%)")).toBeInTheDocument();
        expect(screen.getByText("(+28.6%)")).toBeInTheDocument();
        expect(screen.getByText("(+18.2%)")).toBeInTheDocument();
        expect(screen.getByText("(+160%)")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Open estimates"}));
        expect(onOpen).toHaveBeenCalled();
    });

    test("renders a revenue overview in millions without an open action", () => {
        render(<PeriodEstimatesOverview
            overview={{
                ttm: {value: 253490, change: null},
                current: {value: 301070, change: 18.8},
                next1: {value: 352510, change: 17.1},
                next2: {value: 406290, change: 15.3},
                next3: {value: 459270, change: 13},
            }}
            title="Revenue estimates"
            format={formatRevenueEstimateAmount}
        />);

        expect(screen.getByText("Revenue estimates")).toBeInTheDocument();
        expect(screen.queryByText("Earnings estimates")).not.toBeInTheDocument();
        expect(screen.getByText("253B")).toBeInTheDocument();
        expect(screen.getByText("301B")).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Open estimates"})).not.toBeInTheDocument();
    });

    test("renders nothing without an estimates overview", () => {
        const {container} = render(<PeriodEstimatesOverview overview={null} onOpen={jest.fn()}/>);

        expect(container).toBeEmptyDOMElement();
    });

    test("uses dashes for unavailable rolling windows", () => {
        render(<PeriodEstimatesOverview overview={{
            ttm: {}, current: {}, next1: {}, next2: {}, next3: {},
        }}/>);

        expect(screen.getAllByText("-")).toHaveLength(5);
        expect(screen.queryByText("(+160%)")).not.toBeInTheDocument();
    });
});
