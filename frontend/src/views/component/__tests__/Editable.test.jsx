import {act, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {InputAdornment} from "@mui/material";
import {Editable} from "../Editable";

describe("Editable", () => {
    async function submitWithEnter(input) {
        await act(async () => {
            fireEvent.keyDown(input, {key: "Enter"});
        });
    }

    test("renders view content and applies top level style", () => {
        const {container} = render(
            <Editable
                value={"Initial Value"}
                label={"Value"}
                validate={() => ""}
                update={jest.fn()}
                style={{marginTop: "8px"}}
            >
                {({showValue, setEditing}) =>
                    <button onClick={() => setEditing(true)}>{showValue}</button>
                }
            </Editable>
        );

        expect(screen.getByRole("button", {name: "Initial Value"})).toBeInTheDocument();
        expect(container.firstChild).toHaveStyle("margin-top: 8px");
    });

    test("updates value on enter", async () => {
        const update = jest.fn().mockResolvedValue(null);

        render(
            <Editable
                value={"Initial Value"}
                label={"Value"}
                validate={() => ""}
                update={update}
            >
                {({showValue, setEditing}) =>
                    <button onClick={() => setEditing(true)}>{showValue}</button>
                }
            </Editable>
        );

        fireEvent.click(screen.getByRole("button", {name: "Initial Value"}));

        const input = screen.getByRole("textbox");
        fireEvent.change(input, {target: {value: "Updated Value"}});
        fireEvent.keyDown(input, {key: "Enter"});

        await waitFor(() => expect(update).toHaveBeenCalledWith("Updated Value"));
        await waitFor(() => expect(screen.getByRole("button", {name: "Updated Value"})).toBeInTheDocument());
    });

    test("syncs rendered value when value prop changes", () => {
        const {rerender} = render(
            <Editable
                value={"Initial Value"}
                label={"Value"}
                validate={() => ""}
                update={jest.fn()}
            >
                {({showValue, setEditing}) =>
                    <button onClick={() => setEditing(true)}>{showValue}</button>
                }
            </Editable>
        );

        rerender(
            <Editable
                value={"Changed Value"}
                label={"Value"}
                validate={() => ""}
                update={jest.fn()}
            >
                {({showValue, setEditing}) =>
                    <button onClick={() => setEditing(true)}>{showValue}</button>
                }
            </Editable>
        );

        expect(screen.getByRole("button", {name: "Changed Value"})).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Initial Value"})).not.toBeInTheDocument();
    });

    test("shows validation message while editing invalid value", () => {
        render(
            <Editable
                value={"Initial Value"}
                label={"Value"}
                validate={(value) => value === "" ? "required" : ""}
                update={jest.fn()}
            >
                {({showValue, setEditing}) =>
                    <button onClick={() => setEditing(true)}>{showValue}</button>
                }
            </Editable>
        );

        fireEvent.click(screen.getByRole("button", {name: "Initial Value"}));

        const input = screen.getByRole("textbox");
        fireEvent.change(input, {target: {value: ""}});

        expect(screen.getByText("required")).toBeInTheDocument();
    });

    test("updates invalid value so backend can return error", async () => {
        const update = jest.fn().mockResolvedValue({title: "Backend error"});

        render(
            <Editable
                value={"Initial Value"}
                label={"Value"}
                validate={(value) => value === "" ? "required" : ""}
                update={update}
            >
                {({showValue, setEditing}) =>
                    <button onClick={() => setEditing(true)}>{showValue}</button>
                }
            </Editable>
        );

        fireEvent.click(screen.getByRole("button", {name: "Initial Value"}));

        const input = screen.getByRole("textbox");
        fireEvent.change(input, {target: {value: ""}});
        await submitWithEnter(input);

        await waitFor(() => expect(update).toHaveBeenCalledWith(""));
        await waitFor(() => expect(screen.getByRole("textbox")).toBeInTheDocument());
    });

    test("keeps editing and renders adornment when update fails", async () => {
        const update = jest.fn().mockResolvedValue({title: "Update failed"});

        render(
            <Editable
                value={"123"}
                label={"Value"}
                validate={() => ""}
                update={update}
                startAdornment={<InputAdornment position="start">$</InputAdornment>}
            >
                {({showValue, setEditing}) =>
                    <button onClick={() => setEditing(true)}>{showValue}</button>
                }
            </Editable>
        );

        fireEvent.click(screen.getByRole("button", {name: "123"}));

        expect(screen.getByText("$")).toBeInTheDocument();

        const input = screen.getByRole("textbox");
        fireEvent.change(input, {target: {value: "150"}});
        fireEvent.keyDown(input, {key: "Enter"});

        await waitFor(() => expect(update).toHaveBeenCalledWith("150"));
        await waitFor(() => expect(screen.getByRole("textbox")).toBeInTheDocument());
    });
});
