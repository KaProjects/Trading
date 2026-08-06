import {act, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {RecordAssetAggregate} from "../RecordAssetAggregate";

describe("RecordAssetAggregate", () => {
    test("renders count, average price, percentage profit and total profit", () => {
        render(
            <RecordAssetAggregate
                asset={{quantity: 3, purchasePrice: 100, profitPercent: 25.126, profitValue: 75.378}}
                currency="$"
                update={jest.fn()}
            />
        );

        expect(screen.getByText("3@100$")).toBeInTheDocument();
        expect(screen.getByText("3@100$").closest("button")).toBeInTheDocument();
        expect(screen.getByText("3@100$")).toHaveStyle("font-size: 17px");
        expect(screen.getByTestId("record-asset-aggregate")).toHaveStyle("margin: 3px 0 2px 0");
        expect(screen.getByTestId("record-asset-profit")).toHaveTextContent("+75.38$ (+25.13%)");
        expect(screen.getByTestId("record-asset-profit")).toHaveStyle("margin-top: -3px");
        expect(screen.getByTestId("record-asset-profit")).toHaveStyle("opacity: 0.78");
        expect(screen.getByTestId("record-asset-profit-percent")).toHaveStyle("font-size: 12px");
        expect(screen.getByRole("button")).toHaveTextContent("+75.38$ (+25.13%)");
        expect(screen.queryByText("Count")).not.toBeInTheDocument();
    });

    test("formats quantity and average price with thousands separators", () => {
        render(
            <RecordAssetAggregate
                asset={{quantity: 12345.5, purchasePrice: 1234.25, profitPercent: 25, profitValue: 1234567.89}}
                currency="$"
                update={jest.fn()}
            />
        );

        expect(screen.getByText("12,345.5@1,234.25$")).toBeInTheDocument();
        expect(screen.getByTestId("record-asset-profit")).toHaveTextContent("+1,234,567.89$ (+25%)");
    });

    test("renders negative profit values and missing profit as dash", () => {
        const {rerender} = render(
            <RecordAssetAggregate
                asset={{quantity: 3, purchasePrice: 100, profitPercent: -4.2, profitValue: -12.6}}
                currency="$"
                update={jest.fn()}
            />
        );
        expect(screen.getByTestId("record-asset-profit")).toHaveTextContent("-12.6$ (-4.2%)");

        rerender(
            <RecordAssetAggregate
                asset={{quantity: 3, purchasePrice: 100, profitPercent: null, profitValue: null}}
                currency="$"
                update={jest.fn()}
            />
        );
        expect(screen.getByTestId("record-asset-profit")).toHaveTextContent("- (-)");
    });

    test("updates the stored aggregate quantity and average price", async () => {
        const update = jest.fn().mockResolvedValue(null);
        render(
            <RecordAssetAggregate
                asset={{quantity: 3, purchasePrice: 100, profitPercent: 25, profitValue: 75}}
                currency="$"
                update={update}
            />
        );

        fireEvent.click(screen.getByRole("button"));
        const input = screen.getByRole("textbox");
        fireEvent.change(input, {target: {value: "5.5@110.25"}});
        await act(async () => fireEvent.keyDown(input, {key: "Enter"}));

        await waitFor(() => expect(update).toHaveBeenCalledWith("5.5", "110.25"));
        expect(await screen.findByText("5.5@110.25$")).toBeInTheDocument();
        expect(screen.getByText("edited")).toBeInTheDocument();
    });

    test("validates the aggregate input format", async () => {
        const update = jest.fn().mockResolvedValue({title: "Backend error"});
        render(
            <RecordAssetAggregate
                asset={{quantity: 3, purchasePrice: 100, profitPercent: null, profitValue: null}}
                currency="$"
                update={update}
            />
        );

        fireEvent.click(screen.getByRole("button"));
        const input = screen.getByRole("textbox");
        fireEvent.change(input, {target: {value: "invalid"}});
        await act(async () => fireEvent.keyDown(input, {key: "Enter"}));

        expect(screen.getByText("Use format quantity@purchasePrice")).toBeInTheDocument();
    });
});
