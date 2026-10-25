import {render, screen} from "@testing-library/react";
import {createEditor} from "slate";
import {ContentEditor, isBlockActive, isMarkActive} from "../ContentEditor";

describe("ContentEditor", () => {
    beforeAll(() => {
        global.structuredClone = value => JSON.parse(JSON.stringify(value));
    });

    test("renders existing content", () => {
        render(
            <ContentEditor
                content={JSON.stringify([{type: "paragraph", children: [{text: "Existing content"}]}])}
                update={jest.fn()}
            />
        );

        expect(screen.getByText("Existing content")).toBeInTheDocument();
    });

    test("renders editable area when content is empty", () => {
        const {container} = render(<ContentEditor content={null} update={jest.fn()}/>);

        expect(container.querySelector('[contenteditable="true"]')).not.toBeNull();
    });

    test("renders generated sale details as indented sibling bullets", () => {
        const content = [{
            type: "bulleted-list",
            children: [
                {type: "list-item", children: [{text: "sold 7.5@600$"}]},
                {type: "list-item", children: [{text: "     - 7.5@466.66667$ - 28.33$ = +971.67$ (+27.66%)"}]},
            ],
        }];

        const {container} = render(
            <ContentEditor content={JSON.stringify(content)} update={jest.fn()}/>
        );

        expect(screen.getByText("sold 7.5@600$")).toBeInTheDocument();
        expect(screen.getByText(/- 7.5@466.66667\$ - 28.33\$ = \+971.67\$/)).toBeInTheDocument();
        expect(container.querySelectorAll("ul")).toHaveLength(1);
        expect(container.querySelectorAll("li")).toHaveLength(2);
    });

    test("renders nested sale details", () => {
        const content = [{
            type: "bulleted-list",
            children: [{
                type: "list-item",
                children: [
                    {text: "sold 7.5@600$"},
                    {
                        type: "bulleted-list",
                        children: [{
                            type: "list-item",
                            children: [{text: "- 7.5@466.66667$ - 28.33$ = +971.67$ (+27.66%)"}],
                        }],
                    },
                ],
            }],
        }];

        const {container} = render(
            <ContentEditor content={JSON.stringify(content)} update={jest.fn()}/>
        );

        expect(screen.getByText("sold 7.5@600$")).toBeInTheDocument();
        expect(screen.getByText("- 7.5@466.66667$ - 28.33$ = +971.67$ (+27.66%)")).toBeInTheDocument();
        expect(container.querySelectorAll("ul")).toHaveLength(2);
    });

    test("reports no active formats while the selection points outside the content", () => {
        const editor = createEditor();
        editor.children = [{
            type: "bulleted-list",
            children: [{type: "list-item", children: [{text: ""}]}],
        }];
        editor.selection = {
            anchor: {path: [0, 0, 1], offset: 0},
            focus: {path: [0, 0, 1], offset: 0},
        };

        expect(isMarkActive(editor, "bold")).toBe(false);
        expect(isBlockActive(editor, "bulleted-list")).toBe(false);

        editor.selection = {
            anchor: {path: [0, 0, 0], offset: 0},
            focus: {path: [0, 0, 0], offset: 0},
        };

        expect(isMarkActive(editor, "bold")).toBe(false);
        expect(isBlockActive(editor, "bulleted-list")).toBe(true);
    });
});
