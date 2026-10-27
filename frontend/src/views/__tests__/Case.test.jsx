import {fireEvent, render, screen, waitFor, within} from "@testing-library/react";
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
    defaultContent: () => [{type: "paragraph", children: [{text: ""}]}],
    ContentEditor: ({content, update, onValueChange, alwaysEditing, locked}) => (
        <button
            data-testid="content-editor"
            data-writing={alwaysEditing ? "yes" : "no"}
            data-locked={locked ? "yes" : "no"}
            onClick={() => (onValueChange ?? update)(
                [{type: "paragraph", children: [{text: "written"}]}])}
        >
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
    companies: [{id: 11, ticker: "NVDA", addedAt: "2026-02-10"}],
    entries: [],
    trades: [],
};

const companyLists = {all: [{id: 11, ticker: "NVDA"}, {id: 12, ticker: "AMD"}]};

describe("Case", () => {
    beforeEach(() => {
        mockNavigate.mockReset();
        axios.put.mockReset();
        axios.put.mockResolvedValue({});
        axios.delete.mockReset();
        axios.delete.mockResolvedValue({});
        axios.get.mockReset();
        axios.get.mockResolvedValue({data: {}});
        mockUseData.mockReturnValue({data: value, loaded: true, error: null});
    });

    const openMenu = () => fireEvent.click(screen.getByRole("button", {name: "Case actions"}));

    test("shows the case, the day it opened and the companies it covers", () => {
        render(<Case companyLists={companyLists}/>);

        expect(screen.getByText("memory supercycle")).toBeInTheDocument();
        expect(screen.getByText("opened 10.02.2026")).toBeInTheDocument();
        expect(within(screen.getByTestId("case-companies")).getByText("NVDA")).toBeInTheDocument();
        // The name is read here and changed from the menu, so there is nothing to type into.
        expect(screen.queryByLabelText("Case name")).not.toBeInTheDocument();
    });

    test("goes back to the list of cases", () => {
        render(<Case companyLists={companyLists}/>);

        fireEvent.click(screen.getByRole("button", {name: "Back to cases"}));

        expect(mockNavigate).toHaveBeenCalledWith("/cases");
    });

    test("renames the case from its menu", async () => {
        render(<Case companyLists={companyLists}/>);

        openMenu();
        fireEvent.click(screen.getByText("Rename"));
        fireEvent.change(screen.getByLabelText("Case name"), {target: {value: "memory cycle"}});
        fireEvent.click(screen.getByRole("button", {name: "Save"}));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {name: "memory cycle"}));
    });

    test("covers another company once the plus asks for one", async () => {
        render(<Case companyLists={companyLists}/>);

        // Nothing to pick from until the plus is pressed.
        expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Add company"}));
        const picker = screen.getByRole("combobox");
        fireEvent.focus(picker);
        fireEvent.change(picker, {target: {value: "AMD"}});
        fireEvent.click(screen.getByRole("option", {name: "AMD"}));

        // The ones already covered keep the day they joined; the new one is given none, so it
        // joins today.
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {
            companies: [{id: 11, addedAt: "2026-02-10"}, {id: 12}],
        }));
    });

    test("takes a company off the case from the menu rather than from the chip", async () => {
        mockUseData.mockReturnValue({
            data: {
                ...value,
                companies: [
                    {id: 11, ticker: "NVDA", addedAt: "2026-02-10"},
                    {id: 12, ticker: "AMD", addedAt: "2026-04-01"},
                ],
            },
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        // A chip is read, not unpicked: nothing on it takes the company off the case.
        expect(screen.queryAllByTestId("CancelIcon")).toHaveLength(0);

        openMenu();
        fireEvent.click(screen.getByText("Companies"));
        fireEvent.click(screen.getByRole("button", {name: "Remove AMD"}));
        fireEvent.click(screen.getByRole("button", {name: "Save"}));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {
            companies: [{id: 11, addedAt: "2026-02-10"}],
        }));
    });

    test("re-dates the day a company joined the case, and warns where it has traded", async () => {
        mockUseData.mockReturnValue({
            data: {
                ...value,
                companies: [
                    {id: 11, ticker: "NVDA", addedAt: "2026-02-10"},
                    {id: 12, ticker: "AMD", addedAt: "2026-04-01"},
                ],
                trades: [{id: 2, ticker: "NVDA", currency: "$", active: true,
                    purchaseDate: "2026-03-02", purchasePrice: 100, quantity: 5}],
            },
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        openMenu();
        fireEvent.click(screen.getByText("Companies"));

        // What it would cost to take this one off is said, not enforced.
        expect(screen.getByTestId("case-company-traded-NVDA")).toBeInTheDocument();
        expect(screen.queryByTestId("case-company-traded-AMD")).not.toBeInTheDocument();

        fireEvent.change(screen.getByLabelText("Added AMD"), {target: {value: "2026-01-05"}});
        fireEvent.click(screen.getByRole("button", {name: "Save"}));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {
            companies: [{id: 11, addedAt: "2026-02-10"}, {id: 12, addedAt: "2026-01-05"}],
        }));
    });

    test("records a company the app does not hold as the name it was written as", async () => {
        render(<Case companyLists={companyLists}/>);

        fireEvent.click(screen.getByRole("button", {name: "Add company"}));
        const picker = screen.getByRole("combobox");
        fireEvent.change(picker, {target: {value: "Broadcom"}});
        fireEvent.keyDown(picker, {key: "Enter", code: "Enter"});

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {
            companies: [{id: 11, addedAt: "2026-02-10"}, {name: "Broadcom"}],
        }));
    });

    test("links a written name the app does know, whatever case it was written in", async () => {
        render(<Case companyLists={companyLists}/>);

        fireEvent.click(screen.getByRole("button", {name: "Add company"}));
        const picker = screen.getByRole("combobox");
        fireEvent.change(picker, {target: {value: "amd"}});
        fireEvent.keyDown(picker, {key: "Enter", code: "Enter"});

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {
            companies: [{id: 11, addedAt: "2026-02-10"}, {id: 12}],
        }));
    });

    test("stops covering a company it only had the name of", async () => {
        mockUseData.mockReturnValue({
            data: {
                ...value,
                companies: [
                    {id: null, ticker: "Broadcom", addedAt: "2026-02-11"},
                    {id: null, ticker: "TSMC", addedAt: "2026-02-12"},
                ],
            },
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        expect(within(screen.getByTestId("case-companies")).getByText("Broadcom")).toBeInTheDocument();
        openMenu();
        fireEvent.click(screen.getByText("Companies"));
        fireEvent.click(screen.getByRole("button", {name: "Remove Broadcom"}));
        fireEvent.click(screen.getByRole("button", {name: "Save"}));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {
            companies: [{name: "TSMC", addedAt: "2026-02-12"}],
        }));
    });

    test("closes the case from its menu", async () => {
        render(<Case companyLists={companyLists}/>);

        openMenu();
        fireEvent.click(screen.getByText("Close"));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {closed: true}));
    });

    test("reopens a closed case, and only a closed one can be deleted", async () => {
        mockUseData.mockReturnValue({data: {...value, closedAt: "2026-03-01"}, loaded: true, error: null});
        render(<Case companyLists={companyLists}/>);

        openMenu();
        fireEvent.click(screen.getByText("Reopen"));
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {closed: false}));

        openMenu();
        fireEvent.click(screen.getByText("Delete"));
        // Nothing is deleted on the strength of one click on a menu.
        expect(axios.delete).not.toHaveBeenCalled();
        expect(screen.getByText(/everything written into it will be deleted/)).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Delete"}));
        await waitFor(() => expect(axios.delete).toHaveBeenCalledWith("/api/case/1"));
        await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/cases"));
    });

    test("offers no delete while the case is still open", () => {
        render(<Case companyLists={companyLists}/>);

        openMenu();

        expect(screen.getByText("Close")).toBeInTheDocument();
        expect(screen.queryByText("Delete")).not.toBeInTheDocument();
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
        axios.put.mockResolvedValue({data: {id: 9, date: today, content: null, images: [], tables: []}});
        render(<Case companyLists={companyLists}/>);

        fireEvent.click(screen.getByRole("button", {name: "Write today"}));
        expect(await screen.findByTestId(`case-entry-${today}`)).toBeInTheDocument();
        // The day is already open, so there is nothing to open again.
        expect(screen.queryByRole("button", {name: "Write today"})).not.toBeInTheDocument();

        axios.put.mockClear();
        fireEvent.click(screen.getByText("empty-content"));
        // Typed, not yet written: the day is kept until it is done.
        expect(axios.put).not.toHaveBeenCalled();

        fireEvent.click(screen.getByRole("button", {name: `Save ${today}`}));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/entry/1", {
            date: today,
            content: JSON.stringify([{type: "paragraph", children: [{text: "written"}]}]),
        }));
    });

    test("says so while nothing at all has happened in the case", () => {
        mockUseData.mockReturnValue({
            data: {...value, companies: [], entries: [], trades: []},
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        expect(screen.getByText("Nothing written yet.")).toBeInTheDocument();
    });

    test("says nothing of the sort once a company has joined the case", () => {
        // The company joining is itself something the stream has to say.
        render(<Case companyLists={companyLists}/>);

        expect(screen.queryByText("Nothing written yet.")).not.toBeInTheDocument();
        expect(screen.getByTestId("case-company-added-NVDA")).toBeInTheDocument();
    });

    test("writes a table into a day being written, and takes one away", async () => {
        axios.post.mockResolvedValue({data: {id: 3, position: 0, title: "", columns: [], rows: []}});
        mockUseData.mockReturnValue({
            data: {...value, entries: [{id: 5, date: "2026-02-11", content: "a day", images: [], tables: []}]},
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        // Tables belong to writing: nothing to add one with until the day is being written.
        expect(screen.queryByRole("button", {name: "Add table 2026-02-11"})).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Edit 2026-02-11"}));
        fireEvent.click(screen.getByRole("button", {name: "Add table 2026-02-11"}));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("/api/case/entry/5/table"));
        const table = await screen.findByTestId("case-table-3");
        expect(table).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Add column"}));
        fireEvent.change(screen.getByLabelText("Column 1"), {target: {value: "NVDA"}});
        fireEvent.click(screen.getByRole("button", {name: "Save 2026-02-11"}));
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/entry/table/3",
            expect.objectContaining({columns: ["NVDA"]})));

        fireEvent.click(screen.getByRole("button", {name: "Edit 2026-02-11"}));
        fireEvent.click(screen.getByRole("button", {name: "Delete table"}));
        await waitFor(() => expect(axios.delete).toHaveBeenCalledWith("/api/case/entry/table/3"));
    });

    test("types a value and picks a rating in a table", async () => {
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
        fireEvent.click(screen.getByRole("button", {name: "Edit 2026-02-11"}));

        fireEvent.change(screen.getByLabelText("Row 1 NVDA"), {target: {value: "75%"}});
        // The rating is picked from the list, on top of what was typed, which the grid keeps.
        fireEvent.click(screen.getByRole("button", {name: "Rating 1 NVDA"}));
        fireEvent.click(screen.getByRole("menuitem", {name: "++"}));
        expect(axios.put).not.toHaveBeenCalled();

        fireEvent.click(screen.getByRole("button", {name: "Save 2026-02-11"}));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/entry/table/3", {
            title: "margins",
            columns: ["NVDA"],
            rows: [{label: "gross", cells: [{value: "75%", rating: "++"}]}],
        }));
    });

    test("reads the trades its companies opened while it ran", () => {
        mockUseData.mockReturnValue({
            data: {
                ...value,
                trades: [{
                    id: 2, ticker: "NVDA", currency: "$", active: false, purchaseDate: "2026-03-02",
                    purchasePrice: 100, quantity: 5, sellDate: "2026-04-01", sellPrice: 120,
                    profit: 100, profitPercentage: 20,
                }],
            },
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        const trades = screen.getByTestId("case-trades");
        expect(trades).toHaveTextContent("NVDA");
        expect(trades).toHaveTextContent("2.3.'26");
        expect(trades).toHaveTextContent("+100.00$");
        expect(trades).toHaveTextContent("1.4.'26");
        expect(trades).toHaveTextContent("120.00$");
        expect(trades).toHaveTextContent("+20%");
        // What the case has made, under the trades that made it.
        expect(screen.getByTestId("case-profit-$")).toHaveTextContent("100.00$");
    });

    test("reads a day until the pencil is pressed, then writes it", async () => {
        mockUseData.mockReturnValue({
            data: {...value, entries: [{id: 5, date: "2026-02-11", content: "a day", images: [], tables: []}]},
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        // Reading: no pictures to add, no tables to add, nothing to finish.
        expect(screen.queryByTestId("content-image-add")).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Save 2026-02-11"})).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Edit 2026-02-11"}));

        expect(screen.getByTestId("content-image-add")).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Add table 2026-02-11"})).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Save 2026-02-11"}));
        await waitFor(() =>
            expect(screen.queryByRole("button", {name: "Save 2026-02-11"})).not.toBeInTheDocument());
    });

    test("deletes a row and a column of a table", async () => {
        mockUseData.mockReturnValue({
            data: {
                ...value,
                entries: [{
                    id: 5, date: "2026-02-11", content: "a day", images: [],
                    tables: [{id: 3, position: 0, title: "margins", columns: ["NVDA", "AMD"],
                        rows: [{label: "gross", cells: [{value: "75%", rating: "++"}, {value: "53%", rating: "~"}]}]}],
                }],
            },
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);
        fireEvent.click(screen.getByRole("button", {name: "Edit 2026-02-11"}));

        fireEvent.click(screen.getByRole("button", {name: "Delete column 2"}));
        fireEvent.click(screen.getByRole("button", {name: "Save 2026-02-11"}));
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/entry/table/3",
            expect.objectContaining({
                columns: ["NVDA"],
                rows: [{label: "gross", cells: [{value: "75%", rating: "++"}]}],
            })));

        axios.put.mockClear();
        fireEvent.click(screen.getByRole("button", {name: "Edit 2026-02-11"}));
        fireEvent.click(screen.getByRole("button", {name: "Delete row 1"}));
        fireEvent.click(screen.getByRole("button", {name: "Save 2026-02-11"}));
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/entry/table/3",
            expect.objectContaining({rows: []})));
    });

    test("creates the new day before writing into it, so a table has somewhere to live", async () => {
        axios.put.mockResolvedValue({data: {id: 9, date: today, content: null, images: [], tables: []}});
        axios.post.mockResolvedValue({data: {id: 4, position: 0, title: "", columns: [], rows: []}});
        render(<Case companyLists={companyLists}/>);

        fireEvent.click(screen.getByRole("button", {name: "Write today"}));

        // The day exists on the server before anything can be hung off it.
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/entry/1", {
            date: today,
            content: JSON.stringify([{type: "paragraph", children: [{text: ""}]}]),
        }));
        expect(await screen.findByRole("button", {name: `Save ${today}`})).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: `Add table ${today}`}));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("/api/case/entry/9/table"));
    });

    test("opens the day as an editor, with no second click to start writing", () => {
        mockUseData.mockReturnValue({
            data: {...value, entries: [{id: 5, date: "2026-02-11", content: "a day", images: [], tables: []}]},
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        // Reading: the text is locked and there is nothing to write with.
        expect(screen.getByTestId("content-editor")).toHaveAttribute("data-locked", "yes");

        fireEvent.click(screen.getByRole("button", {name: "Edit 2026-02-11"}));

        // Writing: the editor itself is open, and the picture and table controls with it.
        expect(screen.getByTestId("content-editor")).toHaveAttribute("data-writing", "yes");
        expect(screen.getByTestId("content-editor")).toHaveAttribute("data-locked", "no");
        expect(screen.getByTestId("content-image-add")).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Add table 2026-02-11"})).toBeInTheDocument();
    });

    test("reads the trades among the days they happened on", () => {
        mockUseData.mockReturnValue({
            data: {
                ...value,
                entries: [{id: 5, date: "2026-03-02", content: "bought into it", images: [], tables: []}],
                trades: [{
                    id: 2, ticker: "NVDA", currency: "$", active: false, purchaseDate: "2026-03-02",
                    purchasePrice: 100, purchaseTotal: 502.50, quantity: 5, sellDate: "2026-04-01",
                    sellPrice: 120, profit: 100, profitPercentage: 20,
                }],
            },
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        const bought = screen.getByTestId("case-trade-2-bought");
        const sold = screen.getByTestId("case-trade-2-sold");
        expect(bought).toHaveTextContent("bought 5@100.00$");
        // What the purchase came to altogether.
        expect(bought).toHaveTextContent("(502.50$)");
        expect(sold).toHaveTextContent("sold 5@120.00$");
        // What the sale made, in money as well as in percent, both signed.
        expect(sold).toHaveTextContent("+100.00$");
        expect(sold).toHaveTextContent("+20%");

        // The sale is the later day, so it reads first. The purchase shares its day with the
        // research written then: one block, named once by its date, the trade between the date
        // and what was written about it.
        expect(sold.compareDocumentPosition(bought) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(screen.getByTestId("case-trade-date-2026-04-01")).toBeInTheDocument();
        expect(screen.getByTestId("case-entry-date-2026-03-02")).toBeInTheDocument();
        expect(screen.queryByTestId("case-trade-date-2026-03-02")).not.toBeInTheDocument();

        const day = screen.getByTestId("case-entry-2026-03-02");
        expect(day).toContainElement(bought);
        const date = screen.getByTestId("case-entry-date-2026-03-02");
        expect(date.compareDocumentPosition(bought) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(bought.compareDocumentPosition(screen.getByText("bought into it"))
            & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    test("reads a day's pictures from the case rather than from a period", () => {
        mockUseData.mockReturnValue({
            data: {
                ...value,
                entries: [{
                    id: 5, date: "2026-02-11", content: "a day",
                    images: [{id: 7, width: 800, height: 600}], tables: [],
                }],
            },
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        // A case serves its own pictures; the period endpoint holds different ones under the
        // same ids, so reading from there shows the wrong picture rather than failing.
        expect(screen.getByTestId("content-image-7"))
            .toHaveAttribute("src", "/api/case/entry/image/7?thumbnail=true");
    });

    test("says so when a change cannot be saved", async () => {
        const refusal = new Error("Request failed with status code 400");
        refusal.name = "AxiosError";
        refusal.response = {data: "case name cannot be blank"};
        axios.put.mockRejectedValue(refusal);
        render(<Case companyLists={companyLists}/>);

        openMenu();
        fireEvent.click(screen.getByText("Rename"));
        fireEvent.change(screen.getByLabelText("Case name"), {target: {value: "x"}});
        fireEvent.click(screen.getByRole("button", {name: "Save"}));

        await waitFor(() =>
            expect(screen.getByText(/case name cannot be blank/)).toBeInTheDocument());
    });

    test("frames the day that is being written", () => {
        mockUseData.mockReturnValue({
            data: {...value, entries: [{id: 5, date: "2026-02-11", content: "a day", images: [], tables: []}]},
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        const day = screen.getByTestId("case-entry-2026-02-11");
        expect(window.getComputedStyle(day).borderColor).toBe("transparent");

        fireEvent.click(screen.getByRole("button", {name: "Edit 2026-02-11"}));

        expect(window.getComputedStyle(day).borderColor).not.toBe("transparent");
        expect(screen.getByText("writing")).toBeInTheDocument();
    });

    test("drops a table nothing was typed into instead of saving it", async () => {
        axios.post.mockResolvedValue({data: {id: 3, position: 0, title: "", columns: [], rows: []}});
        mockUseData.mockReturnValue({
            data: {...value, entries: [{id: 5, date: "2026-02-11", content: "a day", images: [], tables: []}]},
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        fireEvent.click(screen.getByRole("button", {name: "Edit 2026-02-11"}));
        fireEvent.click(screen.getByRole("button", {name: "Add table 2026-02-11"}));
        await screen.findByTestId("case-table-3");
        fireEvent.click(screen.getByRole("button", {name: "Save 2026-02-11"}));

        await waitFor(() => expect(axios.delete).toHaveBeenCalledWith("/api/case/entry/table/3"));
        expect(axios.put).not.toHaveBeenCalledWith("/api/case/entry/table/3", expect.anything());
    });

    test("keeps a part-sold position as the two trades it became", () => {
        mockUseData.mockReturnValue({
            data: {
                ...value,
                trades: [
                    {id: 2, ticker: "NVDA", currency: "$", active: false, purchaseDate: "2026-03-02",
                        purchasePrice: 100, quantity: 3, sellDate: "2026-04-01", sellPrice: 120,
                        profit: 60, profitPercentage: 20},
                    {id: 3, ticker: "NVDA", currency: "$", active: true, purchaseDate: "2026-03-02",
                        purchasePrice: 100, quantity: 2},
                ],
            },
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        const rows = screen.getByTestId("case-trades").querySelectorAll("tbody tr");
        expect(rows).toHaveLength(2);
        expect(rows[0]).toHaveTextContent("1.4.'26");
        expect(rows[1]).toHaveTextContent("open");
        // Only what was actually sold counts towards the profit.
        expect(screen.getByTestId("case-profit-$")).toHaveTextContent("60.00$");
    });

    test("cancels the writing, putting the day back as it was", async () => {
        axios.post.mockResolvedValue({data: {id: 7, position: 0, title: "", columns: [], rows: []}});
        mockUseData.mockReturnValue({
            data: {
                ...value,
                entries: [{
                    id: 5, date: "2026-02-11", content: "a day", images: [],
                    tables: [{id: 3, position: 0, title: "margins", columns: ["NVDA"],
                        rows: [{label: "gross", cells: [{value: "75%", rating: "++"}]}]}],
                }],
            },
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        fireEvent.click(screen.getByRole("button", {name: "Edit 2026-02-11"}));
        fireEvent.change(screen.getByLabelText("Row 1 NVDA"), {target: {value: "11%"}});
        fireEvent.click(screen.getByRole("button", {name: "Add table 2026-02-11"}));
        await screen.findByTestId("case-table-7");

        fireEvent.click(screen.getByRole("button", {name: "Cancel 2026-02-11"}));

        // The table added along the way is gone again, nothing was written, and the typed
        // value is back to what it was.
        await waitFor(() => expect(axios.delete).toHaveBeenCalledWith("/api/case/entry/table/7"));
        await waitFor(() => expect(screen.queryByTestId("case-table-7")).not.toBeInTheDocument());
        expect(axios.put).not.toHaveBeenCalled();
        expect(screen.queryByRole("button", {name: "Save 2026-02-11"})).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Edit 2026-02-11"}));
        expect(screen.getByLabelText("Row 1 NVDA")).toHaveValue("75%");
    });

    test("asks for a new date when a day is clicked, and moves it", async () => {
        axios.put.mockResolvedValue({data: {id: 5, date: "2026-01-09", content: "a day", images: [], tables: []}});
        mockUseData.mockReturnValue({
            data: {...value, entries: [{id: 5, date: "2026-02-11", content: "a day", images: [], tables: []}]},
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        fireEvent.click(screen.getByRole("button", {name: "Day written 2026-02-11"}));
        fireEvent.change(screen.getByLabelText("Date"), {target: {value: "2026-01-09"}});
        fireEvent.click(screen.getByRole("button", {name: "Save"}));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith(
            "/api/case/entry/5/date", {date: "2026-01-09"}));
    });

    test("asks for the day the case opened, and keeps it", async () => {
        render(<Case companyLists={companyLists}/>);

        fireEvent.click(screen.getByTestId("case-opened"));
        fireEvent.change(screen.getByLabelText("Date"), {target: {value: "2025-11-04"}});
        fireEvent.click(screen.getByRole("button", {name: "Save"}));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith(
            "/api/case/1", {createdAt: "2025-11-04"}));
    });

    test("asks for the day the case closed, not the day it opened", async () => {
        mockUseData.mockReturnValue({
            data: {...value, closedAt: "2026-09-26"},
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        // The day that was clicked is the day being asked about, not whichever comes first.
        fireEvent.click(screen.getByTestId("case-closed"));
        expect(screen.getByLabelText("Date")).toHaveValue("2026-09-26");
        fireEvent.change(screen.getByLabelText("Date"), {target: {value: "2026-10-01"}});
        fireEvent.click(screen.getByRole("button", {name: "Save"}));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith(
            "/api/case/1", {closedAt: "2026-10-01"}));
        // The opened day is not what was asked about, so it is not what was written.
        expect(axios.put).not.toHaveBeenCalledWith("/api/case/1", {createdAt: "2026-10-01"});
    });

    test("has no closing day to ask about while the case is open", () => {
        render(<Case companyLists={companyLists}/>);

        expect(screen.getByTestId("case-opened")).toBeInTheDocument();
        expect(screen.queryByTestId("case-closed")).not.toBeInTheDocument();
    });

    test("offers no writing at all once the case is closed", () => {
        mockUseData.mockReturnValue({
            data: {
                ...value,
                closedAt: "2026-09-26",
                companies: [
                    {id: 11, ticker: "NVDA", addedAt: "2026-02-10"},
                    {id: null, ticker: "Broadcom", addedAt: "2026-02-11"},
                ],
                entries: [{id: 5, date: "2026-02-11", content: "a day", images: [], tables: []}],
            },
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        expect(screen.queryByRole("button", {name: "Write today"})).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Add company"})).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Edit 2026-02-11"})).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Day written 2026-02-11"})).not.toBeInTheDocument();
        // Neither the companies it covers nor the names written beside them can be taken off.
        expect(screen.queryAllByTestId("CancelIcon")).toHaveLength(0);
        // What it holds is still read: the day, its writing and the companies it covers.
        expect(screen.getByTestId("case-entry-2026-02-11")).toBeInTheDocument();
        const covered = within(screen.getByTestId("case-companies"));
        expect(covered.getByText("NVDA")).toBeInTheDocument();
        expect(covered.getByText("Broadcom")).toBeInTheDocument();
    });

    test("reads what the app holds about its companies on the days it covers them", async () => {
        mockUseData.mockReturnValue({
            data: {
                ...value,
                trades: [{id: 2, ticker: "NVDA", currency: "$", active: true,
                    purchaseDate: "2026-03-02", purchasePrice: 100, quantity: 5}],
                mentions: [
                    // Written the day of the trade: that trade's record.
                    {kind: "RECORD", id: 9, companyId: 11, ticker: "NVDA", date: "2026-03-02"},
                    {kind: "RESEARCH", id: 4, companyId: 11, ticker: "NVDA", date: "2026-04-01"},
                    {kind: "REPORT", id: 7, companyId: 11, ticker: "NVDA", date: "2026-05-06"},
                ],
            },
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        // The record of a trade is opened from the line that says what was traded, rather than
        // from a second line saying the same day over again.
        expect(screen.getByRole("button", {name: "Open the NVDA record"})).toBeInTheDocument();
        expect(screen.queryByTestId("case-mention-RECORD-9")).not.toBeInTheDocument();

        // What has no trade of its own reads as its own line.
        expect(screen.getByTestId("case-mention-RESEARCH-4")).toHaveTextContent("new research");
        expect(screen.getByTestId("case-mention-REPORT-7")).toHaveTextContent("reported");

        axios.get.mockResolvedValue({data: {id: 4, date: "2026-04-01", content: "", images: []}});
        fireEvent.click(screen.getByRole("button", {name: "Open NVDA new research"}));

        await waitFor(() => expect(axios.get)
            .toHaveBeenCalledWith("/api/period/content/entry/4"));
    });

    test("reads the trades oldest first", () => {
        mockUseData.mockReturnValue({
            data: {
                ...value,
                trades: [
                    {id: 2, ticker: "NVDA", currency: "$", active: true, purchaseDate: "2026-05-02",
                        purchasePrice: 100, quantity: 5},
                    {id: 3, ticker: "AMD", currency: "$", active: true, purchaseDate: "2026-01-20",
                        purchasePrice: 90, quantity: 7},
                ],
            },
            loaded: true,
            error: null,
        });
        render(<Case companyLists={companyLists}/>);

        // Two companies, so the table opens summed up; the trades themselves are behind it.
        fireEvent.click(screen.getByRole("button", {name: "Show every trade"}));

        const rows = screen.getByTestId("case-trades").querySelectorAll("tbody tr");
        expect(rows[0]).toHaveTextContent("20.1.'26");
        expect(rows[1]).toHaveTextContent("2.5.'26");
    });
});
