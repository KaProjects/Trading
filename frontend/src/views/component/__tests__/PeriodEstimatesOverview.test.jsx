import {fireEvent, render, screen} from "@testing-library/react";
import {PeriodEstimatesOverview} from "../PeriodEstimatesOverview";

const overview = {
    ttm: {value: 10, change: null},
    current: {value: 14, change: 40},
    next1: {value: 18, change: 28.57},
    next2: {value: 22, change: 22.22},
    next3: {value: 26, change: 18.18},
};

describe("PeriodEstimatesOverview", () => {
    test("renders rolling four-quarter values and backend changes", () => {
        const onOpen = jest.fn();
        render(<PeriodEstimatesOverview overview={overview} onOpen={onOpen}/>);

        expect(screen.getByText("ttm")).toBeInTheDocument();
        expect(screen.getByText("current")).toBeInTheDocument();
        expect(screen.getByText("next 1")).toBeInTheDocument();
        expect(screen.getByText("next 2")).toBeInTheDocument();
        expect(screen.getByText("next 3")).toBeInTheDocument();
        expect(screen.getByText("10")).toBeInTheDocument();
        expect(screen.getByText("14")).toBeInTheDocument();
        expect(screen.getByText("(+40%)")).toBeInTheDocument();
        expect(screen.getByText("(+28.6%)")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Open estimates"}));
        expect(onOpen).toHaveBeenCalled();
    });

    test("uses dashes for unavailable rolling windows", () => {
        render(<PeriodEstimatesOverview overview={{
            ttm: {}, current: {}, next1: {}, next2: {}, next3: {},
        }}/>);

        expect(screen.getAllByText("-")).toHaveLength(5);
    });
});
