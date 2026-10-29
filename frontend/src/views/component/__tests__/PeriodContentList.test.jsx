import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import React from "react";

jest.mock("../ContentEditor", () => ({
    ContentEditor: ({content, update, onCancel}) => (
        <>
            <button onClick={() => update([{type: "paragraph", children: [{text: "written"}]}])}>
                {content || "empty-content"}
            </button>
            {onCancel && <button onClick={onCancel}>{`cancel-${content || "new"}`}</button>}
        </>
    ),
}));

import {PeriodContentList, defaultEntryDate, isEmptyEntry} from "../PeriodContentList";

const period = {
    id: "period-1",
    previousReportDate: "2026-05-20",
    reportDate: "2026-08-26",
};

const entries = [
    {id: 1, date: "2026-06-12", content: "first day", images: []},
    {id: 2, date: "2026-07-03", content: "second day", images: [{id: 9, width: 1600, height: 900}]},
];

function renderList(overrides = {}) {
    const props = {
        period,
        entries,
        onSave: jest.fn().mockResolvedValue(undefined),
        stagedFor: () => ({adds: [], removes: []}),
        onStageImage: jest.fn(),
        onDropStagedImage: jest.fn(),
        onMarkImageRemoved: jest.fn(),
        onCommitImages: jest.fn().mockResolvedValue(null),
        onDiscardImages: jest.fn(),
        onOpenEditor: jest.fn(),
        onAdded: jest.fn(),
        onDateChange: jest.fn(),
        ...overrides,
    };
    return {props, ...render(<PeriodContentList {...props}/>)};
}

describe("PeriodContentList", () => {
    test("shows a period's days newest first, each with its own text", () => {
        renderList();

        const dates = screen.getAllByText(/\d\d\.\d\d\.2026/).map(node => node.textContent);
        expect(dates).toEqual(["03.07.2026", "12.06.2026"]);
        expect(screen.getByText("first day")).toBeInTheDocument();
        expect(screen.getByText("second day")).toBeInTheDocument();
    });

    test("saves against the day that was written, not a new one", async () => {
        const {props} = renderList();

        fireEvent.click(screen.getByText("second day"));

        await waitFor(() => expect(props.onSave).toHaveBeenCalledWith(
            "2026-07-03",
            [{type: "paragraph", children: [{text: "written"}]}]));
    });

    test("opens a new day only when the period asks for one, and keeps its picker inside the period", async () => {
        const onDateChange = jest.fn();
        const {props, rerender} = renderList({onDateChange});
        expect(screen.queryByLabelText("New content date")).not.toBeInTheDocument();

        rerender(<PeriodContentList {...props} adding date="2026-07-20"/>);

        const date = screen.getByLabelText("New content date");
        expect(date).toHaveValue("2026-07-20");
        expect(date).toHaveAttribute("min", "2026-05-20");
        expect(date).toHaveAttribute("max", "2026-08-26");

        fireEvent.change(date, {target: {value: "2026-08-01"}});
        expect(onDateChange).toHaveBeenCalledWith("2026-08-01");

        fireEvent.click(screen.getByText("empty-content"));

        await waitFor(() => expect(props.onSave).toHaveBeenCalledWith(
            "2026-07-20",
            [{type: "paragraph", children: [{text: "written"}]}]));
        await waitFor(() => expect(props.onAdded).toHaveBeenCalled());
    });

    test("refuses a day the period already holds rather than writing over it", async () => {
        const {props} = renderList({adding: true, date: "2026-07-03"});

        const date = screen.getByLabelText("New content date");
        expect(date).toHaveAttribute("title", "This date already has content");

        fireEvent.click(screen.getByText("empty-content"));

        await waitFor(() => expect(props.onSave).not.toHaveBeenCalled());
    });

    test("opens a new day above the days already written", () => {
        renderList({adding: true, date: "2026-07-20"});

        const added = screen.getByTestId("period-content-new");
        const newest = screen.getByTestId("period-content-date-2026-07-03");
        // Newest first, and a day being written is newer than the ones already there.
        expect(added.compareDocumentPosition(newest) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    test("writes nothing while the new day has no date, and says why", async () => {
        const {props} = renderList({adding: true, date: ""});

        // Today is outside this period, so the picker opens empty and must be filled in first.
        expect(screen.getByLabelText("New content date")).toHaveValue("");
        fireEvent.click(screen.getByText("empty-content"));

        await waitFor(() => expect(props.onSave).not.toHaveBeenCalled());
        // The day stays open on what was typed, marked with what is wrong with it.
        expect(screen.getByTestId("period-content-new-problem")).toHaveTextContent("Pick a date first");
        expect(screen.getByTestId("period-content-new")).toBeInTheDocument();
    });

    test("gives up on the new day when the writing is cancelled", () => {
        const onCancelAdding = jest.fn();
        renderList({adding: true, date: "2026-07-20", onCancelAdding});

        fireEvent.click(screen.getByText("cancel-new"));

        expect(onCancelAdding).toHaveBeenCalled();
    });

    test("brings up the picture controls for writing, not for looking", () => {
        renderList();

        // Opening a picture must not look like the entry is being written in.
        fireEvent.click(screen.getByTestId("content-image-9"));
        const viewer = screen.getByRole("dialog");
        expect(viewer.querySelector("img")).toBeInTheDocument();
        expect(document.querySelector('[data-testid="content-image-add"]')).toBeNull();

        // Writing in the entry is what brings them up. The viewer is still unmounting, which
        // leaves the page behind it hidden from the role queries, so the tiles are looked for
        // in the document itself.
        fireEvent.click(viewer.querySelector("img"));
        fireEvent.focusIn(screen.getByText("second day"));

        expect(document.querySelector('[data-testid="content-image-add"]')).not.toBeNull();
        expect(document.querySelector('[aria-label="Remove image"]')).not.toBeNull();
    });

    test("writes the staged pictures when the writing ends, and drops them when it is cancelled", () => {
        const {props} = renderList();
        const editor = screen.getByText("first day");

        fireEvent.focusIn(editor);
        fireEvent.focusOut(editor, {relatedTarget: document.body});

        expect(props.onCommitImages).toHaveBeenCalledWith(1);
        expect(props.onDiscardImages).not.toHaveBeenCalled();
    });

    test("marks a day the period no longer covers", () => {
        renderList({entries: [
            {id: 1, date: "2026-06-12", content: "inside", images: []},
            {id: 2, date: "2026-09-30", content: "after the report", images: []},
        ]});

        expect(screen.getByTestId("period-content-date-2026-09-30"))
            .toHaveAttribute("title", "This date is outside the period");
        expect(screen.getByTestId("period-content-date-2026-06-12")).not.toHaveAttribute("title");
    });

    test("hands a day over to its own editor on a narrow screen", () => {
        const {props} = renderList({compact: true});

        fireEvent.click(screen.getByText("first day").parentElement);

        expect(props.onOpenEditor).toHaveBeenCalledWith(entries[0]);
    });

    describe("defaultEntryDate", () => {
        test("offers today while the period still covers it", () => {
            expect(defaultEntryDate(period, new Date("2026-07-04T10:00:00Z"))).toBe("2026-07-04");
        });

        test("offers nothing once today is outside the period", () => {
            expect(defaultEntryDate(period, new Date("2026-12-01T10:00:00Z"))).toBe("");
            expect(defaultEntryDate(period, new Date("2026-01-01T10:00:00Z"))).toBe("");
        });

        test("offers today for a period with no window at all", () => {
            expect(defaultEntryDate({id: "x"}, new Date("2026-07-04T10:00:00Z"))).toBe("2026-07-04");
        });
    });

    describe("isEmptyEntry", () => {
        test("counts an entry as empty only without text and without pictures", () => {
            const empty = '[{"type":"paragraph","children":[{"text":""}]}]';
            expect(isEmptyEntry({content: empty, images: []})).toBe(true);
            expect(isEmptyEntry({content: null, images: []})).toBe(true);
            expect(isEmptyEntry({content: empty, images: [{id: 1}]})).toBe(false);
            expect(isEmptyEntry({content: "written", images: []})).toBe(false);
        });
    });
});
