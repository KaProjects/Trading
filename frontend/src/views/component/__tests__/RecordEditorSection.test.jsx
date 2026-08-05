import {fireEvent, render, screen} from "@testing-library/react";

jest.mock("../ContentEditor", () => ({
    ContentEditor: ({content, update}) => (
        <button onClick={() => update([{type: "paragraph", children: [{text: "Updated content"}]}])}>
            {content}
        </button>
    )
}));

import {RecordEditorSection} from "../RecordEditorSection";

describe("RecordEditorSection", () => {
    test("renders label and content editor", () => {
        render(
            <RecordEditorSection
                label={"Review"}
                content={"Existing review"}
                update={jest.fn()}
            />
        );

        expect(screen.getByText("Review:")).toBeInTheDocument();
        expect(screen.getByText("Existing review")).toBeInTheDocument();
    });

    test("passes updates from content editor", () => {
        const update = jest.fn();

        render(
            <RecordEditorSection
                label={"Content"}
                content={"Existing content"}
                update={update}
            />
        );

        fireEvent.click(screen.getByText("Existing content"));

        expect(update).toHaveBeenCalledWith([{type: "paragraph", children: [{text: "Updated content"}]}]);
    });
});
