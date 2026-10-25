import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

const mockFormatError = jest.fn(() => ({title: "Save failed", message: "Todo could not be saved"}));

jest.mock("axios");
jest.mock("../../properties", () => ({
    backend: "/api",
}));
jest.mock("../../service/FormattingService", () => ({
    formatError: (...args) => mockFormatError(...args),
}));

import {AddTodoDialog} from "../AddTodoDialog";

function createProps(overrides = {}) {
    return {
        open: true,
        handleClose: jest.fn(),
        onCreated: jest.fn(),
        onUpdated: jest.fn(),
        ...overrides,
    };
}

describe("AddTodoDialog", () => {
    beforeEach(() => {
        axios.post.mockReset();
        axios.put.mockReset();
        mockFormatError.mockReset();
        mockFormatError.mockReturnValue({title: "Save failed", message: "Todo could not be saved"});
    });

    test("creates a todo and returns the backend response", async () => {
        const created = {id: 7, content: "Review #NVDA earnings", createdAt: "2026-09-10T10:00:00", companyId: 42};
        axios.post.mockResolvedValue({data: created});
        const props = createProps();

        render(<AddTodoDialog {...props}/>);

        fireEvent.change(screen.getByRole("textbox", {name: "Task"}), {
            target: {value: "  Review #NVDA earnings  "},
        });
        fireEvent.click(screen.getByText("Add"));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("/api/todo", {
            content: "Review #NVDA earnings",
        }));
        expect(props.onCreated).toHaveBeenCalledWith(created);
        expect(props.handleClose).toHaveBeenCalled();
    });

    test("requires nonblank content", () => {
        render(<AddTodoDialog {...createProps()}/>);

        expect(screen.getByText("Add")).toBeDisabled();

        fireEvent.change(screen.getByRole("textbox", {name: "Task"}), {target: {value: "   "}});

        expect(screen.getByText("Add")).toBeDisabled();
        expect(screen.getByText("Task must not be blank")).toBeInTheDocument();
    });

    test("prefills and updates an existing todo", async () => {
        const todo = {id: 7, content: "Review #NVDA", createdAt: "2026-09-10T10:00:00", companyId: 42};
        const updated = {...todo, content: "Review #AMD", companyId: 43};
        axios.put.mockResolvedValue({data: updated});
        const props = createProps({todo});

        render(<AddTodoDialog {...props}/>);

        expect(screen.getByText("Edit Todo")).toBeInTheDocument();
        expect(screen.getByRole("textbox", {name: "Task"})).toHaveValue("Review #NVDA");

        fireEvent.change(screen.getByRole("textbox", {name: "Task"}), {target: {value: " Review #AMD "}});
        fireEvent.click(screen.getByText("Save"));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/todo/7", {
            content: "Review #AMD",
        }));
        expect(props.onUpdated).toHaveBeenCalledWith(updated);
        expect(props.onCreated).not.toHaveBeenCalled();
        expect(props.handleClose).toHaveBeenCalled();
    });

    test("clears a backend error when a field is edited", async () => {
        axios.post.mockRejectedValue(new Error("boom"));

        render(<AddTodoDialog {...createProps()}/>);
        fireEvent.change(screen.getByRole("textbox", {name: "Task"}), {target: {value: "Review"}});
        fireEvent.click(screen.getByText("Add"));

        expect(await screen.findByText("Todo could not be saved")).toBeInTheDocument();

        fireEvent.change(screen.getByRole("textbox", {name: "Task"}), {target: {value: "Updated review"}});
        expect(screen.queryByText("Todo could not be saved")).not.toBeInTheDocument();
    });
});
