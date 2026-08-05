import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

const mockFormatError = jest.fn(() => ({title: "Save failed", message: "Tag could not be saved"}));

jest.mock("axios");
jest.mock("../../properties", () => ({
    backend: "http://backend",
}));
jest.mock("../../service/FormattingService", () => ({
    formatError: (...args) => mockFormatError(...args),
}));

import {AddTagDialog} from "../AddTagDialog";

function createProps(overrides = {}) {
    return {
        open: true,
        companyId: "company-1",
        suggestions: ["growth", "income"],
        handleClose: jest.fn(),
        triggerRefresh: jest.fn(),
        ...overrides,
    };
}

describe("AddTagDialog", () => {
    beforeEach(() => {
        axios.post.mockReset();
        mockFormatError.mockReset();
        mockFormatError.mockReturnValue({title: "Save failed", message: "Tag could not be saved"});
    });

    test("submits a tag and refreshes the research data", async () => {
        axios.post.mockResolvedValue({});
        const props = createProps();

        render(<AddTagDialog {...props}/>);

        fireEvent.change(screen.getByRole("combobox", {name: /Tag/}), {target: {value: "growth"}});
        fireEvent.click(screen.getByText("Add"));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("http://backend/company/tag", {
            companyId: "company-1",
            value: "growth",
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.handleClose).toHaveBeenCalled();
    });

    test("keeps add disabled for an empty tag", () => {
        render(<AddTagDialog {...createProps()}/>);

        expect(screen.getByText("Add")).toBeDisabled();
    });

    test("filters tags already assigned to the company from suggestions", () => {
        render(<AddTagDialog {...createProps({currentTags: ["growth"]})}/>);

        fireEvent.mouseDown(screen.getByRole("combobox", {name: /Tag/}));

        expect(screen.queryByRole("option", {name: "growth"})).not.toBeInTheDocument();
        expect(screen.getByRole("option", {name: "income"})).toBeInTheDocument();
    });

    test("rejects a tag already assigned to the company", () => {
        render(<AddTagDialog {...createProps({currentTags: ["growth"]})}/>);

        fireEvent.change(screen.getByRole("combobox", {name: /Tag/}), {target: {value: "GROWTH"}});

        expect(screen.getByText("Add")).toBeDisabled();
        expect(screen.getByText("Tag is already assigned to this company")).toBeInTheDocument();
    });

    test.each(["owned", "Recent", "RESEARCHED", "all"])("rejects reserved tag %p", value => {
        render(<AddTagDialog {...createProps()}/>);

        fireEvent.change(screen.getByRole("combobox", {name: /Tag/}), {target: {value}});

        expect(screen.getByText("Add")).toBeDisabled();
        expect(screen.getByText("Tag name is reserved")).toBeInTheDocument();
    });

    test.each(["high growth", "high\tgrowth", " growth", "growth "])(
        "rejects whitespace in tag %p",
        value => {
            render(<AddTagDialog {...createProps()}/>);

            fireEvent.change(screen.getByRole("combobox", {name: /Tag/}), {target: {value}});

            expect(screen.getByText("Add")).toBeDisabled();
            expect(screen.getByText("Tag must not contain spaces or tabs")).toBeInTheDocument();
        }
    );

    test("shows a formatted backend error", async () => {
        axios.post.mockRejectedValue(new Error("boom"));
        const props = createProps();

        render(<AddTagDialog {...props}/>);
        fireEvent.change(screen.getByRole("combobox", {name: /Tag/}), {target: {value: "new-tag"}});
        await waitFor(() => expect(screen.getByText("Add")).toBeEnabled());
        fireEvent.click(screen.getByText("Add"));

        await waitFor(() => expect(mockFormatError).toHaveBeenCalled());
        expect(screen.getByText("Tag could not be saved")).toBeInTheDocument();
        expect(props.triggerRefresh).not.toHaveBeenCalled();
        expect(props.handleClose).not.toHaveBeenCalled();
    });
});
