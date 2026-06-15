import React from "react";
import {fireEvent, render, screen} from "@testing-library/react";

const mockRecordEvent = jest.fn();

jest.mock("../../../service/utils", () => ({
    recordEvent: (...args) => mockRecordEvent(...args),
}));

import {MainBarSelect} from "../MainBarSelect";

describe("MainBarSelect", () => {
    const originalLocation = window.location;

    beforeAll(() => {
        delete window.location;
        window.location = {pathname: "/stats"};
    });

    afterAll(() => {
        window.location = originalLocation;
    });

    beforeEach(() => {
        mockRecordEvent.mockReset();
    });

    test("renders label placeholder and primitive options", () => {
        render(
            <MainBarSelect
                values={["2024", "2025"]}
                value=""
                setValue={jest.fn()}
                label="years"
            />
        );

        expect(screen.getByRole("combobox")).toHaveTextContent("years");

        fireEvent.mouseDown(screen.getByRole("combobox"));

        expect(screen.getByRole("option", {name: "2024"})).toBeInTheDocument();
        expect(screen.getByRole("option", {name: "2025"})).toBeInTheDocument();
    });

    test("selects primitive value and records selector event", () => {
        const setValue = jest.fn();

        render(
            <MainBarSelect
                values={["2024", "2025"]}
                value=""
                setValue={setValue}
                label="years"
            />
        );

        fireEvent.mouseDown(screen.getByRole("combobox"));
        fireEvent.click(screen.getByRole("option", {name: "2025"}));

        expect(setValue).toHaveBeenCalledWith("2025");
        expect(mockRecordEvent).toHaveBeenCalledWith("/stats#selector:years");
    });

    test("renders object options using valueKey", () => {
        render(
            <MainBarSelect
                values={[
                    {ticker: "NVDA"},
                    {ticker: "AAPL"},
                ]}
                value=""
                setValue={jest.fn()}
                label="companies"
                valueKey="ticker"
            />
        );

        fireEvent.mouseDown(screen.getByRole("combobox"));

        expect(screen.getByRole("option", {name: "NVDA"})).toBeInTheDocument();
        expect(screen.getByRole("option", {name: "AAPL"})).toBeInTheDocument();
    });

    test("renders duplicate option labels without collapsing items", () => {
        render(
            <MainBarSelect
                values={["2024", "2024"]}
                value=""
                setValue={jest.fn()}
                label="years"
            />
        );

        fireEvent.mouseDown(screen.getByRole("combobox"));

        expect(screen.getAllByRole("option", {name: "2024"})).toHaveLength(2);
    });
});
