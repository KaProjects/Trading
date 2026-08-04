import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import {EditableValueBox} from "../EditableValueBox";

describe("EditableValueBox", () => {
    test("renders initial value with suffix and applies top level style", () => {
        const {container} = render(
            <EditableValueBox
                value={"123"}
                suffix={"$"}
                label={"Targets"}
                validate={() => ""}
                update={jest.fn()}
                style={{marginTop: "-4px"}}
            />
        );

        expect(screen.getByText("123$")).toBeInTheDocument();
        expect(container.firstChild).toHaveStyle("margin-top: -4px");
    });

    test("renders a prefix and does not enter editing mode when disabled", () => {
        render(
            <EditableValueBox
                value={"123"}
                prefix={"$"}
                label={"Price"}
                validate={() => ""}
                update={jest.fn()}
                disabled
            />
        );

        const button = screen.getByRole("button");
        expect(button).toHaveTextContent("$123");
        expect(button).toHaveAttribute("aria-disabled", "true");

        fireEvent.click(button);

        expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    });

    test("renders optional secondary content inside the editable button", () => {
        render(
            <EditableValueBox
                value={"3@100"}
                suffix={"$"}
                label={"Asset aggregate"}
                validate={() => ""}
                update={jest.fn()}
                secondary={<span>+75$ (+25%)</span>}
            />
        );

        const button = screen.getByRole("button");
        expect(button).toHaveTextContent("3@100$");
        expect(button).toHaveTextContent("+75$ (+25%)");
    });

    test("formats only the displayed value and keeps the raw value for editing", () => {
        render(
            <EditableValueBox
                value={"12345.5@1234.25"}
                suffix={"$"}
                label={"Asset aggregate"}
                valueStyle={{fontSize: 16, fontWeight: 500}}
                formatValue={() => "12,345.5@1,234.25"}
                validate={() => ""}
                update={jest.fn()}
            />
        );

        expect(screen.getByText("12,345.5@1,234.25$")).toHaveStyle("font-size: 16px; font-weight: 500");
        fireEvent.click(screen.getByRole("button"));
        expect(screen.getByRole("textbox")).toHaveValue("12345.5@1234.25");
    });

    test("accepts mui sx selectors through style prop", () => {
        const {container} = render(
            <EditableValueBox
                value={"123"}
                suffix={"$"}
                label={"Targets"}
                validate={() => ""}
                update={jest.fn()}
                style={{
                    opacity: 0,
                    ".mainContainer:hover &": {
                        opacity: 1,
                        pointerEvents: "auto",
                    },
                }}
            />
        );

        expect(screen.getByText("123$")).toBeInTheDocument();
        expect(container.firstChild).toHaveStyle("opacity: 0");
    });

    test("renders add icon when value is empty", () => {
        render(
            <EditableValueBox
                value={null}
                suffix={"$"}
                label={"Targets"}
                validate={() => ""}
                update={jest.fn()}
            />
        );

        expect(screen.getByRole("button")).toBeInTheDocument();
        expect(screen.queryByText("$")).not.toBeInTheDocument();
    });

    test("updates value on enter", async () => {
        const update = jest.fn().mockResolvedValue(null);

        render(
            <EditableValueBox
                value={"123"}
                suffix={"$"}
                label={"Targets"}
                validate={() => ""}
                update={update}
            />
        );

        fireEvent.click(screen.getByRole("button"));

        const input = screen.getByRole("textbox");
        fireEvent.change(input, {target: {value: "150"}});
        fireEvent.keyDown(input, {key: "Enter"});

        await waitFor(() => expect(update).toHaveBeenCalledWith("150"));
        await waitFor(() => expect(screen.getByText("150$")).toBeInTheDocument());
    });

    test("submits an empty nullable value", async () => {
        const update = jest.fn().mockResolvedValue(null);

        render(
            <EditableValueBox
                value={5}
                suffix="%"
                label="Dividend yield"
                validate={() => ""}
                update={update}
            />
        );

        fireEvent.click(screen.getByRole("button"));
        const input = screen.getByRole("textbox");
        fireEvent.change(input, {target: {value: ""}});
        fireEvent.keyDown(input, {key: "Enter"});

        await waitFor(() => expect(update).toHaveBeenCalledWith(""));
        await waitFor(() => expect(screen.queryByRole("textbox")).not.toBeInTheDocument());
    });

    test("shows validation message while editing invalid value", () => {
        render(
            <EditableValueBox
                value={"123"}
                suffix={"$"}
                label={"Targets"}
                validate={(value) => value === "" ? "required" : ""}
                update={jest.fn()}
            />
        );

        fireEvent.click(screen.getByRole("button"));

        const input = screen.getByRole("textbox");
        fireEvent.change(input, {target: {value: ""}});

        expect(screen.getByText("required")).toBeInTheDocument();
    });

    test("keeps editing when update fails", async () => {
        const update = jest.fn().mockResolvedValue({title: "Update failed", message: "Could not save"});

        render(
            <EditableValueBox
                value={"123"}
                suffix={"$"}
                label={"Targets"}
                validate={() => ""}
                update={update}
            />
        );

        fireEvent.click(screen.getByRole("button"));

        const input = screen.getByRole("textbox");
        fireEvent.change(input, {target: {value: "150"}});
        fireEvent.keyDown(input, {key: "Enter"});

        await waitFor(() => expect(update).toHaveBeenCalledWith("150"));
        await waitFor(() => expect(screen.getByRole("textbox")).toBeInTheDocument());
    });
});
