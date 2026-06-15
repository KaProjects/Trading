import {fireEvent, render, screen} from "@testing-library/react";
import {DialogTextField} from "../DialogTextField";

describe("DialogTextField", () => {
    test("renders with standard dialog text field defaults", () => {
        const validate = jest.fn().mockReturnValue("");

        const {container} = render(<DialogTextField label={"Ticker"} value={"NVDA"} validate={validate}/>);

        const input = container.querySelector("input");

        expect(input).toBeRequired();
        expect(input).toHaveValue("NVDA");
        expect(validate).toHaveBeenCalled();
    });

    test("shows validation error and helper text", () => {
        const {container} = render(
            <DialogTextField
                label={"Ticker"}
                value={""}
                validate={() => "not filled"}
            />
        );

        expect(screen.getByText("not filled")).toBeInTheDocument();
        expect(container.querySelector("input")).toHaveAttribute("aria-invalid", "true");
    });

    test("forwards change handler props", () => {
        const onChange = jest.fn();

        const {container} = render(
            <DialogTextField
                label={"Ticker"}
                value={""}
                validate={() => ""}
                onChange={onChange}
            />
        );

        fireEvent.change(container.querySelector("input"), {target: {value: "AAPL"}});

        expect(onChange).toHaveBeenCalled();
    });
});
