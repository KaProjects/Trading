import {render, screen} from "@testing-library/react";

import {PeriodTargetSummary} from "../PeriodTargetSummary";

describe("PeriodTargetSummary", () => {
    test("renders count, maximum, minimum and average with company currency", () => {
        render(
            <PeriodTargetSummary
                stats={{count: 5, minimum: 120, average: 145.5, maximum: 175}}
                currency={"€"}
            />
        );

        expect(screen.getByText("Targets: 5@(175-120)~146€")).toBeInTheDocument();
    });

    test("uses one decimal below ten and whole numbers from ten", () => {
        render(
            <PeriodTargetSummary
                stats={{count: 3, minimum: 9.44, average: 9.75, maximum: 10}}
                currency={"$"}
            />
        );

        expect(screen.getByText("Targets: 3@(10-9.4)~9.8$")).toBeInTheDocument();
    });

    test("does not render when the period has no persisted targets", () => {
        const {container, rerender} = render(
            <PeriodTargetSummary
                stats={{count: 0, minimum: null, average: null, maximum: null}}
                currency={"$"}
            />
        );

        expect(container).toBeEmptyDOMElement();

        rerender(<PeriodTargetSummary stats={null} currency={"$"}/>);
        expect(container).toBeEmptyDOMElement();
    });
});
