import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

jest.mock("axios");
jest.mock("../../properties", () => ({backend: "/api"}));

const mockUseData = jest.fn();
const mockNavigate = jest.fn();

jest.mock("../../service/BackendService", () => ({
    useData: (...args) => mockUseData(...args),
}));
jest.mock("react-router-dom", () => ({
    useNavigate: () => mockNavigate,
}));
jest.mock("../component/Loader", () => ({
    Loader: ({error}) => <div data-testid="loader">{error?.message ?? "loading"}</div>,
}));

import {Cases} from "../Cases";

const cases = [
    {id: 1, name: "memory supercycle", createdAt: "2026-02-10", closedAt: null,
        companies: [
            {id: 11, ticker: "NVDA", addedAt: "2026-02-10"},
            {id: 12, ticker: "AMD", addedAt: "2026-03-01"},
        ]},
    {id: 2, name: "pc refresh", createdAt: "2025-05-01", closedAt: "2026-01-20", companies: []},
];

describe("Cases", () => {
    beforeEach(() => {
        mockNavigate.mockReset();
        axios.post.mockReset();
        mockUseData.mockReturnValue({data: cases, loaded: true, error: null});
    });

    test("reads the open cases first and the closed ones as history", () => {
        render(<Cases/>);

        const open = screen.getByTestId("case-1");
        const closed = screen.getByTestId("case-2");
        expect(open).toHaveTextContent("memory supercycle");
        expect(open).toHaveTextContent("NVDA");
        expect(screen.getByText("Closed")).toBeInTheDocument();
        expect(open.compareDocumentPosition(closed) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    test("names the companies the app does not hold alongside the ones it does", () => {
        mockUseData.mockReturnValue({
            data: [{...cases[0], companies: [
                ...cases[0].companies,
                {id: null, ticker: "Broadcom", addedAt: "2026-04-01"},
                {id: null, ticker: "TSMC", addedAt: "2026-05-01"},
            ]}],
            loaded: true,
            error: null,
        });
        render(<Cases/>);

        const open = screen.getByTestId("case-1");
        expect(open).toHaveTextContent("NVDA");
        expect(open).toHaveTextContent("Broadcom");
        expect(open).toHaveTextContent("TSMC");
    });

    test("opens the case that was clicked", () => {
        render(<Cases/>);

        fireEvent.click(screen.getByTestId("case-1"));

        expect(mockNavigate).toHaveBeenCalledWith("/cases/1");
    });

    test("names a new case before it exists, and opens it", async () => {
        axios.post.mockResolvedValue({data: {id: 7}});
        const setOpenAddCase = jest.fn();
        render(<Cases openAddCase setOpenAddCase={setOpenAddCase}/>);

        fireEvent.change(screen.getByLabelText("Case name"), {target: {value: " china exposure "}});
        fireEvent.click(screen.getByRole("button", {name: "Save"}));

        expect(axios.post).toHaveBeenCalledWith("/api/case", {name: "china exposure", companyIds: []});
        await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/cases/7"));
    });

    test("writes nothing without a name", () => {
        render(<Cases openAddCase setOpenAddCase={jest.fn()}/>);

        expect(screen.getByRole("button", {name: "Save"})).toBeDisabled();
    });

    test("asks for a name only when the app bar says so", () => {
        render(<Cases/>);

        expect(screen.queryByLabelText("Case name")).not.toBeInTheDocument();
    });

    test("says so when there are no cases", () => {
        mockUseData.mockReturnValue({data: [], loaded: true, error: null});
        render(<Cases/>);

        expect(screen.getByText("No cases yet.")).toBeInTheDocument();
    });
});
