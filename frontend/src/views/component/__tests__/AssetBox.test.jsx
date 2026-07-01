import {act, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {AssetBox} from "../AssetBox";

describe("AssetBox", () => {
    async function submitValue(input, value) {
        fireEvent.change(input, {target: {value}});
        await act(async () => {
            fireEvent.keyDown(input, {key: "Enter"});
        });
    }

    test("renders quantity, purchase price and currency", () => {
        render(
            <AssetBox
                asset={{quantity: 3, purchasePrice: 120, profitPercent: null}}
                currency={"$"}
                style={{marginLeft: "10px"}}
            />
        );

        expect(screen.getByText("3@120$")).toBeInTheDocument();
        expect(screen.getByText("3@120$").parentElement).toHaveStyle("margin-left: 10px");
    });

    test("renders positive profit percent with plus sign and rounding", () => {
        render(<AssetBox asset={{quantity: 3, purchasePrice: 120, profitPercent: 12.345}} currency={"$"}/>);

        expect(screen.getByText("+12.35%")).toBeInTheDocument();
    });

    test("renders negative profit percent", () => {
        render(<AssetBox asset={{quantity: 3, purchasePrice: 120, profitPercent: -4.2}} currency={"$"}/>);

        expect(screen.getByText("-4.2%")).toBeInTheDocument();
    });

    test("renders zero profit percent", () => {
        render(<AssetBox asset={{quantity: 3, purchasePrice: 120, profitPercent: 0}} currency={"$"}/>);

        expect(screen.getByText("0%")).toBeInTheDocument();
    });

    test("does not render profit percent when value is not numeric", () => {
        render(<AssetBox asset={{quantity: 3, purchasePrice: 120, profitPercent: "abc"}} currency={"$"}/>);

        expect(screen.getByText("3@120$")).toBeInTheDocument();
        expect(screen.queryByText(/%/)).not.toBeInTheDocument();
    });

    test("updates asset value from quantity at purchase price format", async () => {
        const update = jest.fn().mockResolvedValue(null);

        render(
            <AssetBox
                asset={{quantity: 3, purchasePrice: 120, profitPercent: null}}
                currency={"$"}
                update={update}
            />
        );

        fireEvent.click(screen.getByText("3@120$"));

        const input = screen.getByRole("textbox");
        fireEvent.change(input, {target: {value: "3.5@120.25"}});
        fireEvent.keyDown(input, {key: "Enter"});

        await waitFor(() => expect(update).toHaveBeenCalledWith("3.5", "120.25"));
        await waitFor(() => expect(screen.getByText("3.5@120.25$")).toBeInTheDocument());
    });

    test("replaces profit percent after confirmed asset update", async () => {
        const update = jest.fn().mockResolvedValue(null);

        render(
            <AssetBox
                asset={{quantity: 3, purchasePrice: 120, profitPercent: 12.345}}
                currency={"$"}
                update={update}
            />
        );

        expect(screen.getByText("+12.35%")).toBeInTheDocument();

        fireEvent.click(screen.getByText("3@120$"));

        const input = screen.getByRole("textbox");
        fireEvent.change(input, {target: {value: "3.5@120.25"}});
        fireEvent.keyDown(input, {key: "Enter"});

        await waitFor(() => expect(update).toHaveBeenCalledWith("3.5", "120.25"));
        await waitFor(() => expect(screen.getByText("edited")).toBeInTheDocument());
        expect(screen.queryByText("+12.35%")).not.toBeInTheDocument();
        expect(screen.getByText("edited")).toHaveStyle("color: rgb(224, 224, 224)");
    });

    test("hides profit percent while editing", () => {
        render(
            <AssetBox
                asset={{quantity: 3, purchasePrice: 120, profitPercent: 12.345}}
                currency={"$"}
                update={jest.fn()}
            />
        );

        expect(screen.getByText("+12.35%")).toBeInTheDocument();

        fireEvent.click(screen.getByText("3@120$"));

        expect(screen.queryByText("+12.35%")).not.toBeInTheDocument();
    });

    test("validates asset value format", async () => {
        const update = jest.fn().mockResolvedValue({title: "Backend error"});

        render(
            <AssetBox
                asset={{quantity: 3, purchasePrice: 120, profitPercent: null}}
                currency={"$"}
                update={update}
            />
        );

        fireEvent.click(screen.getByText("3@120$"));

        const input = screen.getByRole("textbox");
        await submitValue(input, "3-abc");

        expect(screen.getByText("Use format quantity@purchasePrice")).toBeInTheDocument();
        expect(update).toHaveBeenCalledWith("3-abc", undefined);
    });

    test("validates purchase price number format", async () => {
        const update = jest.fn().mockResolvedValue({title: "Backend error"});

        render(
            <AssetBox
                asset={{quantity: 3, purchasePrice: 120, profitPercent: null}}
                currency={"$"}
                update={update}
            />
        );

        fireEvent.click(screen.getByText("3@120$"));

        const input = screen.getByRole("textbox");
        await submitValue(input, "3@abc");

        expect(screen.getByText("Purchase price: not a valid number")).toBeInTheDocument();
        expect(update).toHaveBeenCalledWith("3", "abc");
    });

    test("validates quantity decimal constraint", async () => {
        const update = jest.fn().mockResolvedValue({title: "Backend error"});

        render(
            <AssetBox
                asset={{quantity: 3, purchasePrice: 120, profitPercent: null}}
                currency={"$"}
                update={update}
            />
        );

        fireEvent.click(screen.getByText("3@120$"));

        const input = screen.getByRole("textbox");
        await submitValue(input, "1.12345@120");

        expect(screen.getByText("Quantity: max decimal 4")).toBeInTheDocument();
        expect(update).toHaveBeenCalledWith("1.12345", "120");
    });

    test("validates quantity integer constraint", async () => {
        const update = jest.fn().mockResolvedValue({title: "Backend error"});

        render(
            <AssetBox
                asset={{quantity: 3, purchasePrice: 120, profitPercent: null}}
                currency={"$"}
                update={update}
            />
        );

        fireEvent.click(screen.getByText("3@120$"));

        const input = screen.getByRole("textbox");
        await submitValue(input, "12345@120");

        expect(screen.getByText("Quantity: max length 4")).toBeInTheDocument();
        expect(update).toHaveBeenCalledWith("12345", "120");
    });

    test("validates purchase price decimal constraint", async () => {
        const update = jest.fn().mockResolvedValue({title: "Backend error"});

        render(
            <AssetBox
                asset={{quantity: 3, purchasePrice: 120, profitPercent: null}}
                currency={"$"}
                update={update}
            />
        );

        fireEvent.click(screen.getByText("3@120$"));

        const input = screen.getByRole("textbox");
        await submitValue(input, "3@1.12345");

        expect(screen.getByText("Purchase price: max decimal 4")).toBeInTheDocument();
        expect(update).toHaveBeenCalledWith("3", "1.12345");
    });

    test("validates purchase price integer constraint", async () => {
        const update = jest.fn().mockResolvedValue({title: "Backend error"});

        render(
            <AssetBox
                asset={{quantity: 3, purchasePrice: 120, profitPercent: null}}
                currency={"$"}
                update={update}
            />
        );

        fireEvent.click(screen.getByText("3@120$"));

        const input = screen.getByRole("textbox");
        await submitValue(input, "3@1234567");

        expect(screen.getByText("Purchase price: max length 6")).toBeInTheDocument();
        expect(update).toHaveBeenCalledWith("3", "1234567");
    });

    test("validates quantity and purchase price are greater than zero", async () => {
        const update = jest.fn().mockResolvedValue({title: "Backend error"});

        render(
            <AssetBox
                asset={{quantity: 3, purchasePrice: 120, profitPercent: null}}
                currency={"$"}
                update={update}
            />
        );

        fireEvent.click(screen.getByText("3@120$"));

        const input = screen.getByRole("textbox");
        await submitValue(input, "0@120");

        expect(screen.getByText("Quantity: must be greater than 0")).toBeInTheDocument();
        expect(update).toHaveBeenCalledWith("0", "120");

        update.mockClear();
        await submitValue(input, "3@0");

        expect(screen.getByText("Purchase price: must be greater than 0")).toBeInTheDocument();
        expect(update).toHaveBeenCalledWith("3", "0");
    });

    test("validates quantity and purchase price are not negative", async () => {
        const update = jest.fn().mockResolvedValue({title: "Backend error"});

        render(
            <AssetBox
                asset={{quantity: 3, purchasePrice: 120, profitPercent: null}}
                currency={"$"}
                update={update}
            />
        );

        fireEvent.click(screen.getByText("3@120$"));

        const input = screen.getByRole("textbox");
        await submitValue(input, "-1@120");

        expect(screen.getByText("Quantity: negative values not allowed")).toBeInTheDocument();
        expect(update).toHaveBeenCalledWith("-1", "120");

        update.mockClear();
        await submitValue(input, "3@-1");

        expect(screen.getByText("Purchase price: negative values not allowed")).toBeInTheDocument();
        expect(update).toHaveBeenCalledWith("3", "-1");
    });
});
