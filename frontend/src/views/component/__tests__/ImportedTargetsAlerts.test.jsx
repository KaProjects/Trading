import {fireEvent, render, screen} from "@testing-library/react";
import React from "react";

import {ImportedTargetsAlerts} from "../ImportedTargetsAlerts";

const groups = [
    {
        periodId: "period-1",
        periodName: "25Q1",
        targets: [
            {date: "2025-03-20", institution: "Alpha Research", price: 180, rating: "Buy"},
            {date: "2025-03-21", institution: "Beta Capital", price: 192.5, rating: "Hold"},
        ],
    },
    {
        periodId: "period-2",
        periodName: "24Q4",
        targets: [{date: "2025-01-20", institution: "Gamma Markets", price: 150, rating: "Buy"}],
    },
];

describe("ImportedTargetsAlerts", () => {
    test("announces every period that took targets in, with its institutions and prices", () => {
        render(<ImportedTargetsAlerts groups={groups} currency={"$"} onDismiss={jest.fn()}/>);

        expect(screen.getByText("New targets for 25Q1")).toBeInTheDocument();
        expect(screen.getByText("Alpha Research")).toBeInTheDocument();
        expect(screen.getByText("180.00$")).toBeInTheDocument();
        expect(screen.getByText("Beta Capital")).toBeInTheDocument();
        expect(screen.getByText("192.50$")).toBeInTheDocument();

        expect(screen.getByText("New targets for 24Q4")).toBeInTheDocument();
        expect(screen.getByText("Gamma Markets")).toBeInTheDocument();
        expect(screen.getByText("150.00$")).toBeInTheDocument();
    });

    test("dismisses one period's card without touching the other", () => {
        const onDismiss = jest.fn();

        render(<ImportedTargetsAlerts groups={groups} currency={"$"} onDismiss={onDismiss}/>);

        fireEvent.click(screen.getAllByRole("button", {name: "Close"})[0]);

        expect(onDismiss).toHaveBeenCalledTimes(1);
        expect(onDismiss).toHaveBeenCalledWith("period-1");
    });

    test("gives up after a while on its own", () => {
        jest.useFakeTimers();
        const onDismiss = jest.fn();

        try {
            render(<ImportedTargetsAlerts groups={[groups[0]]} currency={"$"} onDismiss={onDismiss}/>);

            expect(onDismiss).not.toHaveBeenCalled();
            jest.advanceTimersByTime(20000);
            expect(onDismiss).toHaveBeenCalledWith("period-1");
        } finally {
            jest.useRealTimers();
        }
    });

    test("renders nothing when no targets were taken in", () => {
        const {container} = render(
            <ImportedTargetsAlerts groups={[]} currency={"$"} onDismiss={jest.fn()}/>
        );

        expect(container).toBeEmptyDOMElement();
    });
});
