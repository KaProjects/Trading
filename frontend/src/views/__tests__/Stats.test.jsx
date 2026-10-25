import React from "react";
import {render, screen} from "@testing-library/react";

jest.mock("../component/CompanyStats", () => ({
    CompanyStats: ({type}) => <div>company-stats:{type}</div>,
}));

jest.mock("../component/PeriodStats", () => ({
    PeriodStats: ({type}) => <div>period-stats:{type}</div>,
}));

jest.mock("../component/ProfitLossStats", () => ({
    ProfitLossStats: () => <div>profit-loss-stats</div>,
}));

import {Stats} from "../Stats";

function createProps(overrides = {}) {
    return {
        statsTabsIndex: 0,
        ...overrides,
    };
}

describe("Stats", () => {
    test("renders company stats for companies tab", () => {
        render(<Stats {...createProps({statsTabsIndex: 0})}/>);

        expect(screen.getByText("company-stats:company")).toBeInTheDocument();
    });

    test("renders monthly stats for monthly tab", () => {
        render(<Stats {...createProps({statsTabsIndex: 1})}/>);

        expect(screen.getByText("period-stats:monthly")).toBeInTheDocument();
    });

    test("renders quarterly stats for quarterly tab", () => {
        render(<Stats {...createProps({statsTabsIndex: 2})}/>);

        expect(screen.getByText("period-stats:quarterly")).toBeInTheDocument();
    });

    test("renders yearly stats for yearly tab", () => {
        render(<Stats {...createProps({statsTabsIndex: 3})}/>);

        expect(screen.getByText("period-stats:yearly")).toBeInTheDocument();
    });

    test("renders profit and loss chart for P/L tab", () => {
        render(<Stats {...createProps({statsTabsIndex: 4})}/>);

        expect(screen.getByText("profit-loss-stats")).toBeInTheDocument();
    });
});
