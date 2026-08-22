import React from "react";
import {fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import axios from "axios";

const mockUseMediaQuery = jest.fn(() => false);
const mockFormatError = jest.fn(() => ({title: "Request failed", message: "Could not load todos"}));

jest.mock("axios");
jest.mock("@mui/material/useMediaQuery", () => (...args) => mockUseMediaQuery(...args));
jest.mock("../../../properties", () => ({
    backend: "/api",
}));
jest.mock("../../../service/FormattingService", () => ({
    formatError: (...args) => mockFormatError(...args),
}));
jest.mock("../../../dialog/AddTodoDialog", () => ({
    AddTodoDialog: props => props.open
        ? <div>
            <span>todo-dialog:{props.todo?.id ?? "new"}</span>
            <button onClick={() => props.todo
                ? props.onUpdated({...props.todo, content: "Updated todo", companyId: null})
                : props.onCreated({id: 9, content: "New todo", createdAt: "2026-09-09T10:00:00", companyId: null})
            }>
                {props.todo ? "update-dialog-todo" : "create-dialog-todo"}
            </button>
        </div>
        : null,
}));
jest.mock("../SnackbarErrorAlert", () => ({
    SnackbarErrorAlert: props => props.open ? <div>{props.error?.message}</div> : null,
}));

import {TodoList} from "../TodoList";

const todos = [
    {id: 2, content: "Newer", createdAt: "2026-08-22T10:00:00", companyId: null},
    {id: 1, content: "Review #NVDA", createdAt: "2026-08-21T10:00:00", companyId: 42},
];

function createProps(overrides = {}) {
    return {
        companyLists: {all: [{id: 42, ticker: "NVDA"}]},
        setCompanySelectorValue: jest.fn(),
        setCompanyListSelectorValue: jest.fn(),
        ...overrides,
    };
}

describe("TodoList", () => {
    beforeEach(() => {
        axios.get.mockReset();
        axios.delete.mockReset();
        mockUseMediaQuery.mockReset();
        mockUseMediaQuery.mockReturnValue(false);
        mockFormatError.mockReset();
        mockFormatError.mockReturnValue({title: "Request failed", message: "Could not load todos"});
    });

    test("loads and orders todos from oldest to newest", async () => {
        axios.get.mockResolvedValue({data: todos});

        render(<TodoList {...createProps()}/>);

        await screen.findByText("Review #NVDA");
        const items = screen.getAllByRole("listitem");
        expect(within(items[0]).getByText("Review #NVDA")).toBeInTheDocument();
        expect(within(items[1]).getByText("Newer")).toBeInTheDocument();
    });

    test("selects a linked company and switches to the all list", async () => {
        axios.get.mockResolvedValue({data: todos});
        const onCompanySelected = jest.fn();
        const props = createProps({onCompanySelected});

        render(<TodoList {...props}/>);
        fireEvent.click(await screen.findByRole("button", {name: "Open NVDA"}));

        expect(props.setCompanyListSelectorValue).toHaveBeenCalledWith("all");
        expect(props.setCompanySelectorValue).toHaveBeenCalledWith({id: 42, ticker: "NVDA"});
        expect(onCompanySelected).toHaveBeenCalled();
    });

    test("opens the Todo for editing from its content", async () => {
        axios.get.mockResolvedValue({data: todos});

        render(<TodoList {...createProps()}/>);
        const content = await screen.findByText("Review #NVDA");
        fireEvent.click(content);

        expect(screen.getByText("todo-dialog:1")).toBeInTheDocument();
        fireEvent.click(screen.getByText("update-dialog-todo"));
        expect(screen.getByText("Updated todo")).toBeInTheDocument();
        expect(screen.queryByText("Review #NVDA")).not.toBeInTheDocument();
    });

    test("removes a completed todo", async () => {
        axios.get.mockResolvedValue({data: todos});
        axios.delete.mockResolvedValue({});

        render(<TodoList {...createProps()}/>);
        await screen.findByText("Review #NVDA");
        fireEvent.click(screen.getByRole("button", {name: "Complete todo 1"}));

        await waitFor(() => expect(axios.delete).toHaveBeenCalledWith("/api/todo/1"));
        await waitFor(() => expect(screen.queryByText("Review #NVDA")).not.toBeInTheDocument());
    });

    test("adds a newly created todo after older todos", async () => {
        axios.get.mockResolvedValue({data: todos});

        render(<TodoList {...createProps()}/>);
        await screen.findByText("Review #NVDA");
        fireEvent.click(screen.getByRole("button", {name: "Add todo"}));
        fireEvent.click(screen.getByText("create-dialog-todo"));

        const items = screen.getAllByRole("listitem");
        expect(within(items[2]).getByText("New todo")).toBeInTheDocument();
    });

    test("does not load or render the inactive Todo tab at the compact breakpoint", () => {
        mockUseMediaQuery.mockReturnValue(true);

        render(<TodoList {...createProps()}/>);

        expect(screen.queryByRole("list", {name: "Research todos"})).not.toBeInTheDocument();
        expect(axios.get).not.toHaveBeenCalled();
    });

    test("loads and renders the active Todo tab at the compact breakpoint", async () => {
        mockUseMediaQuery.mockReturnValue(true);
        axios.get.mockResolvedValue({data: todos});

        render(<TodoList {...createProps()} active/>);

        expect(await screen.findByText("Review #NVDA")).toBeInTheDocument();
        expect(screen.getByRole("list", {name: "Research todos"})).toBeInTheDocument();
        expect(axios.get).toHaveBeenCalledWith("/api/todo");
    });

    test("shows a formatted loading error", async () => {
        axios.get.mockRejectedValue(new Error("boom"));

        render(<TodoList {...createProps()}/>);

        expect(await screen.findByText("Could not load todos")).toBeInTheDocument();
        expect(mockFormatError).toHaveBeenCalled();
    });
});
