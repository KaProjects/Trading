import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import {EditableTypography} from "../EditableTypography";

describe("EditableTypography", () => {
    test("renders initial value and applies top level style", () => {
        const {container} = render(
            <EditableTypography
                value={"Initial Title"}
                label={"Title"}
                validate={() => ""}
                update={jest.fn()}
                style={{margin: "12px 15px 0 5px"}}
            />
        );

        expect(screen.getByText("Initial Title")).toBeInTheDocument();
        expect(container.firstChild).toHaveStyle("margin: 12px 15px 0 5px");
    });

    test("updates value on enter", async () => {
        const update = jest.fn().mockResolvedValue(null);

        render(
            <EditableTypography
                value={"Initial Title"}
                label={"Title"}
                validate={() => ""}
                update={update}
            />
        );

        fireEvent.click(screen.getByText("Initial Title"));

        const input = screen.getByRole("textbox");
        fireEvent.change(input, {target: {value: "Updated Title"}});
        fireEvent.keyDown(input, {key: "Enter"});

        await waitFor(() => expect(update).toHaveBeenCalledWith("Updated Title"));
        await waitFor(() => expect(screen.getByText("Updated Title")).toBeInTheDocument());
    });

    test("shows validation message while editing invalid value", () => {
        render(
            <EditableTypography
                value={"Initial Title"}
                label={"Title"}
                validate={(value) => value === "" ? "required" : ""}
                update={jest.fn()}
            />
        );

        fireEvent.click(screen.getByText("Initial Title"));

        const input = screen.getByRole("textbox");
        fireEvent.change(input, {target: {value: ""}});

        expect(screen.getByText("required")).toBeInTheDocument();
    });
});
