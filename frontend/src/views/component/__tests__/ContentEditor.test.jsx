import {render, screen} from "@testing-library/react";
import {ContentEditor} from "../ContentEditor";

describe("ContentEditor", () => {
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
});
