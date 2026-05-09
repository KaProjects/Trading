import {render, screen} from "@testing-library/react";
import {BorderedSection} from "../BorderedSection";

describe("BorderedSection", () => {
    test("renders title and children", () => {
        render(
            <BorderedSection title={"Section Title"}>
                <div>Child Content</div>
            </BorderedSection>
        );

        expect(screen.getByText("Section Title")).toBeInTheDocument();
        expect(screen.getByText("Child Content")).toBeInTheDocument();
    });

    test("renders children without title", () => {
        render(
            <BorderedSection>
                <div>Child Content</div>
            </BorderedSection>
        );

        expect(screen.getByText("Child Content")).toBeInTheDocument();
    });
});
