import {fireEvent, render, screen} from "@testing-library/react";
import {DialogDatePicker} from "../DialogDatePicker";

describe("DialogDatePicker", () => {
    test("renders with date input defaults", () => {
        const {container} = render(<DialogDatePicker label={"Date"} value={"2026-05-09"}/>);

        const input = container.querySelector("input");

        expect(input).toBeRequired();
        expect(input).toHaveAttribute("type", "date");
        expect(input).toHaveValue("2026-05-09");
        expect(input).toHaveAttribute("aria-invalid", "false");
    });

    test("shows invalid state when empty and no custom validator is provided", () => {
        const {container} = render(<DialogDatePicker label={"Date"} value={""}/>);

        expect(container.querySelector("input")).toHaveAttribute("aria-invalid", "true");
    });

    test("uses custom validation message when validator is provided", () => {
        const {container} = render(
            <DialogDatePicker
                label={"Date"}
                value={"2026-05-09"}
                validate={() => "invalid date"}
            />
        );

        expect(screen.getByText("invalid date")).toBeInTheDocument();
        expect(container.querySelector("input")).toHaveAttribute("aria-invalid", "true");
    });

    test("forwards change handler props", () => {
        const onChange = jest.fn();

        const {container} = render(
            <DialogDatePicker
                label={"Date"}
                value={"2026-05-09"}
                onChange={onChange}
            />
        );

        fireEvent.change(container.querySelector("input"), {target: {value: "2026-05-10"}});

        expect(onChange).toHaveBeenCalled();
    });
});
