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
    useParams: () => ({caseId: "1"}),
}));
jest.mock("../component/Loader", () => ({
    Loader: ({error}) => <div data-testid="loader">{error?.message ?? "loading"}</div>,
}));
jest.mock("../component/ContentEditor", () => ({
    ContentEditor: ({content, update}) => (
        <button onClick={() => update([{type: "paragraph", children: [{text: "written"}]}])}>
            {content || "empty-content"}
        </button>
    ),
}));

import {Case} from "../Case";

const today = new Date().toISOString().substring(0, 10);

const value = {
    id: 1,
    name: "memory supercycle",
    createdAt: "2026-02-10",
    closedAt: null,
    companies: [{id: 11, ticker: "NVDA"}],
    entries: [],
};

const companyLists = {all: [{id: 11, ticker: "NVDA"}, {id: 12, ticker: "AMD"}]};

describe("Case", () => {
    beforeEach(() => {
        mockNavigate.mockReset();
        axios.put.mockReset();
        axios.put.mockResolvedValue({});
        axios.delete.mockReset();
        axios.delete.mockResolvedValue({});
        mockUseData.mockReturnValue({data: value, loaded: true, error: null});
    });

    test("shows the case, the day it opened and the companies it covers", () => {
        render(<Case companyLists={companyLists}/>);

        expect(screen.getByLabelText("Case name")).toHaveValue("memory supercycle");
        expect(screen.getByText("opened 10.02.2026")).toBeInTheDocument();
        expect(screen.getByText("NVDA")).toBeInTheDocument();
    });

    test("names the case again once the writing is done", async () => {
        render(<Case companyLists={companyLists}/>);

        const name = screen.getByLabelText("Case name");
        fireEvent.change(name, {target: {value: "memory cycle"}});
        fireEvent.blur(name);

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {name: "memory cycle"}));
    });

    test("covers another company, and stops covering one", async () => {
        render(<Case companyLists={companyLists}/>);

        fireEvent.click(screen.getByRole("button", {name: "Open"}));
        fireEvent.click(screen.getByRole("option", {name: "AMD"}));
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {companyIds: [11, 12]}));

        axios.put.mockClear();
        fireEvent.click(screen.getByTestId("CancelIcon"));
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {companyIds: []}));
    });

    test("closes the case, and reopens a closed one", async () => {
        render(<Case companyLists={companyLists}/>);
        fireEvent.click(screen.getByRole("button", {name: "Close"}));
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {closed: true}));

        axios.put.mockClear();
        mockUseData.mockReturnValue({data: {...value, closedAt: "2026-03-01"}, loaded: true, error: null});
        render(<Case companyLists={companyLists}/>);
        fireEvent.click(screen.getAllByRole("button", {name: "Reopen"})[0]);
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {closed: false}));
    });

    test("goes back to the list once the case is deleted", async () => {
        render(<Case companyLists={companyLists}/>);

        fireEvent.click(screen.getByRole("button", {name: "Delete case"}));

        await waitFor(() => expect(axios.delete).toHaveBeenCalledWith("/api/case/1"));
        await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/cases"));
    });

    test("reads the days written into the case, newest first", () => {
        mockUseData.mockReturnValue({
            data: {
                ...value,
                entries: [
                    {id: 5, date: "2026-02-11", content: "the first day", images: []},
                    {id: 6, date: "2026-02-20", content: "a later day", images: []},
                ],
            },
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        const later = screen.getByTestId("case-entry-2026-02-20");
        const first = screen.getByTestId("case-entry-2026-02-11");
        expect(later.compareDocumentPosition(first) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(screen.getByTestId("case-entry-date-2026-02-20")).toHaveTextContent("20.02.2026");
    });

    test("opens today for writing, and offers it only while today is unwritten", async () => {
        axios.put.mockResolvedValue({data: {id: 9, date: today, content: "written", images: []}});
        render(<Case companyLists={companyLists}/>);

        fireEvent.click(screen.getByRole("button", {name: "Write today"}));
        expect(screen.getByTestId(`case-entry-${today}`)).toBeInTheDocument();
        // The day is already open, so there is nothing to open again.
        expect(screen.queryByRole("button", {name: "Write today"})).not.toBeInTheDocument();

        fireEvent.click(screen.getByText("empty-content"));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/entry/1", {
            date: today,
            content: JSON.stringify([{type: "paragraph", children: [{text: "written"}]}]),
        }));
    });

    test("says so while nothing has been written", () => {
        render(<Case companyLists={companyLists}/>);

        expect(screen.getByText("Nothing written yet.")).toBeInTheDocument();
    });

    test("writes a table into a day, and takes one away", async () => {
        axios.post.mockResolvedValue({data: {id: 3, position: 0, title: "", columns: [], rows: []}});
        mockUseData.mockReturnValue({
            data: {...value, entries: [{id: 5, date: "2026-02-11", content: "a day", images: [], tables: []}]},
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        fireEvent.click(screen.getByRole("button", {name: "Add table 2026-02-11"}));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("/api/case/entry/5/table"));
        const table = await screen.findByTestId("case-table-3");
        expect(table).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Add column"}));
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/entry/table/3",
            expect.objectContaining({columns: [""]})));

        fireEvent.click(screen.getByRole("button", {name: "Delete table"}));
        await waitFor(() => expect(axios.delete).toHaveBeenCalledWith("/api/case/entry/table/3"));
    });

    test("types a value and cycles a rating in a table", async () => {
        mockUseData.mockReturnValue({
            data: {
                ...value,
                entries: [{
                    id: 5, date: "2026-02-11", content: "a day", images: [],
                    tables: [{id: 3, position: 0, title: "margins", columns: ["NVDA"],
                        rows: [{label: "gross", cells: [{value: "", rating: ""}]}]}],
                }],
            },
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        fireEvent.change(screen.getByLabelText("Row 1 NVDA"), {target: {value: "75%"}});
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/entry/table/3",
            expect.objectContaining({
                rows: [{label: "gross", cells: [{value: "75%", rating: ""}]}],
            })));

        // The rating is cycled on top of what was typed, which the grid keeps.
        axios.put.mockClear();
        fireEvent.click(screen.getByRole("button", {name: "Rating 1 NVDA"}));
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/entry/table/3",
            expect.objectContaining({
                rows: [{label: "gross", cells: [{value: "75%", rating: "++"}]}],
            })));
    });
});
