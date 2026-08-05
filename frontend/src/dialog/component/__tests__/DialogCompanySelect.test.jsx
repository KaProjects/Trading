import React from "react";
import {fireEvent, render, screen} from "@testing-library/react";
import {DialogCompanySelect} from "../DialogCompanySelect";

describe("DialogCompanySelect", () => {
    test("shows its label, uses the configured default list, switches lists, and clears", () => {
        const owned = {id: "company-1", ticker: "NVDA"};
        const recent = {id: "company-2", ticker: "AMD"};
        const onChange = jest.fn();
        const props = {
            id: "test-company",
            companyLists: {
                owned: [owned],
                recent: [recent],
                all: [owned, recent],
            },
            defaultCompanyList: "owned",
            value: "",
            onChange,
        };
        const {rerender} = render(<DialogCompanySelect {...props}/>);

        const selector = screen.getByRole("combobox", {name: "Company"});
        expect(selector).toBeInTheDocument();
        expect(screen.getByText("Company")).toHaveAttribute("data-shrink", "false");
        expect(screen.getByText("not filled")).toBeInTheDocument();

        fireEvent.mouseDown(selector);
        expect(screen.getByRole("button", {name: "Company list Owned"})).toBeInTheDocument();
        expect(screen.getByRole("option", {name: "NVDA"})).toBeInTheDocument();
        expect(screen.queryByRole("option", {name: "AMD"})).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Company list Owned"}));
        fireEvent.click(screen.getByRole("button", {name: "Use company list Recent"}));
        fireEvent.click(screen.getByRole("option", {name: "AMD"}));

        expect(onChange).toHaveBeenCalledWith(recent);

        rerender(<DialogCompanySelect {...props} value={recent}/>);
        expect(screen.getByText("Company")).toHaveAttribute("data-shrink", "true");
        expect(screen.queryByText("not filled")).not.toBeInTheDocument();
        fireEvent.mouseDown(screen.getByRole("combobox", {name: "Company"}));
        fireEvent.click(screen.getByRole("option", {name: "clear"}));

        expect(onChange).toHaveBeenCalledWith("");
    });
});
