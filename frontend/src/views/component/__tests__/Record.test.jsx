import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

jest.mock("axios");
jest.mock("../EditableTypography", () => ({
    EditableTypography: ({value, update}) => (
        <button onClick={() => update("Updated title")}>{value}</button>
    )
}));
jest.mock("../EditableValueBox", () => ({
    EditableValueBox: ({value, prefix, suffix, label, style, formatValue, validate, update, disabled}) => {
        const displayedValue = formatValue ? formatValue(String(value)) : value;
        return (
            <button
                data-testid={"editable-value-" + label}
                style={{opacity: style?.opacity, pointerEvents: style?.pointerEvents}}
                aria-disabled={disabled}
                data-empty-valid={validate?.("") === ""}
                onClick={() => !disabled && update && update(label === "Dividend yield" ? "6.25" : "Updated target")}
            >
                {label}:{prefix}{displayedValue}{suffix}
            </button>
        );
    }
}));
jest.mock("../ContentEditor", () => ({
    ContentEditor: ({content, update}) => (
        <button onClick={() => update([{type: "paragraph", children: [{text: "Updated content"}]}])}>
            {content}
        </button>
    ),
    defaultContent: () => [{type: "paragraph", children: [{text: ""}]}]
}));
jest.mock("../RecordAssetAggregate", () => ({
    RecordAssetAggregate: ({asset, currency, update}) => (
        <button onClick={() => update && update("5.5", "110.25")}>
            aggregate:{asset.quantity}@{asset.purchasePrice}{currency}:{asset.profitPercent}:{asset.profitValue}
        </button>
    )
}));

import {Record} from "../Record";

describe("Record", () => {
    const defaultContent = JSON.stringify([{type: "paragraph", children: [{text: ""}]}]);

    beforeEach(() => {
        axios.put.mockReset();
        axios.put.mockResolvedValue({});
    });

    test("renders date, summary values and asset", () => {
        render(
            <Record
                data={{
                    id: "record-1",
                    date: "2026-05-09",
                    price: 123,
                    priceToRevenues: 1,
                    priceToGrossProfit: 2,
                    priceToOperatingIncome: 3,
                    priceToNetIncome: 4,
                    dividendYield: 5,
                    targets: "T",
                    strategy: "S",
                    title: "Initial title",
                    content: "Initial content",
                    asset: {quantity: 3, purchasePrice: 100, profitPercent: 23, profitValue: 69},
                }}
                currency={"$"}
                setAlert={jest.fn()}
            />
        );

        expect(screen.getByText("09.05.2026")).toBeInTheDocument();
        expect(screen.getByTestId("record-summary")).toHaveStyle("gap: 5px");
        expect(screen.getByText("Price:$123")).toBeInTheDocument();
        expect(screen.getByTestId("editable-value-Price")).toHaveAttribute("aria-disabled", "true");
        expect(screen.getByText("Price to financials ratios:1 / 2 / 3 / 4")).toBeInTheDocument();
        expect(screen.getByTestId("editable-value-Price to financials ratios")).toHaveAttribute("aria-disabled", "true");
        expect(screen.getByText("Dividend yield:5%")).toBeInTheDocument();
        expect(screen.getByTestId("editable-value-Dividend yield")).toHaveAttribute("data-empty-valid", "true");
        expect(screen.getByText("Targets:T$")).toBeInTheDocument();
        expect(screen.getByTestId("editable-value-Price").compareDocumentPosition(
            screen.getByTestId("editable-value-Targets")
        ) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(screen.getByText("Strategy:")).toBeInTheDocument();
        expect(screen.getByText("Content:")).toBeInTheDocument();
        expect(screen.getByText("aggregate:3@100$:23:69")).toBeInTheDocument();
    });

    test("keeps empty targets visible", () => {
        render(
            <Record
                data={{
                    id: "record-1",
                    date: "2026-05-09",
                    price: 123,
                    priceToRevenues: 1,
                    priceToGrossProfit: 2,
                    priceToOperatingIncome: 3,
                    priceToNetIncome: 4,
                    dividendYield: 5,
                    targets: "",
                }}
                currency={"$"}
                setAlert={jest.fn()}
            />
        );

        expect(screen.getByTestId("editable-value-Targets")).not.toHaveStyle("opacity: 0");
        expect(screen.getByTestId("editable-value-Targets")).not.toHaveStyle("pointer-events: none");
    });

    test("keeps empty dividend yield visible", () => {
        render(
            <Record
                data={{
                    id: "record-1",
                    date: "2026-05-09",
                    price: 123,
                    priceToRevenues: 1,
                    priceToGrossProfit: 2,
                    priceToOperatingIncome: 3,
                    priceToNetIncome: 4,
                    dividendYield: null,
                    targets: "T",
                }}
                currency={"$"}
                setAlert={jest.fn()}
            />
        );

        expect(screen.getByTestId("editable-value-Dividend yield")).not.toHaveStyle("opacity: 0");
        expect(screen.getByTestId("editable-value-Dividend yield")).not.toHaveStyle("pointer-events: none");
    });

    test("does not render financial ratios when all four values are missing", () => {
        render(
            <Record
                data={{
                    id: "record-1",
                    date: "2026-05-09",
                    price: 123,
                    dividendYield: 5,
                    targets: "T",
                }}
                currency={"$"}
                setAlert={jest.fn()}
            />
        );

        expect(screen.queryByTestId("editable-value-Price to financials ratios")).not.toBeInTheDocument();
    });

    test("keeps targets visible when they are set", () => {
        render(
            <Record
                data={{
                    id: "record-1",
                    date: "2026-05-09",
                    price: 123,
                    priceToRevenues: 1,
                    priceToGrossProfit: 2,
                    priceToOperatingIncome: 3,
                    priceToNetIncome: 4,
                    dividendYield: 5,
                    targets: "T",
                }}
                currency={"$"}
                setAlert={jest.fn()}
            />
        );

        expect(screen.getByTestId("editable-value-Targets")).not.toHaveStyle("opacity: 0");
        expect(screen.getByTestId("editable-value-Targets")).not.toHaveStyle("pointer-events: none");
    });

    test("updates title and content through axios", async () => {
        render(
            <Record
                data={{
                    id: "record-1",
                    date: "2026-05-09",
                    price: 123,
                    priceToRevenues: 1,
                    priceToGrossProfit: 2,
                    priceToOperatingIncome: 3,
                    priceToNetIncome: 4,
                    dividendYield: 5,
                    targets: "T",
                    strategy: "S",
                    title: "Initial title",
                    content: "Initial content",
                }}
                currency={"$"}
                setAlert={jest.fn()}
            />
        );

        fireEvent.click(screen.getByText("Initial title"));
        fireEvent.click(screen.getByText("Initial content"));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith(
            expect.stringContaining("/record"),
            {id: "record-1", title: "Updated title"}
        ));
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith(
            expect.stringContaining("/record"),
            {
                id: "record-1",
                content: JSON.stringify([{type: "paragraph", children: [{text: "Updated content"}]}]),
            }
        ));
    });

    test("updates targets through axios and refreshes local record state", async () => {
        render(
            <Record
                data={{
                    id: "record-1",
                    date: "2026-05-09",
                    price: 123,
                    priceToRevenues: 1,
                    priceToGrossProfit: 2,
                    priceToOperatingIncome: 3,
                    priceToNetIncome: 4,
                    dividendYield: 5,
                    targets: "T",
                }}
                currency={"$"}
                setAlert={jest.fn()}
            />
        );

        fireEvent.click(screen.getByText("Targets:T$"));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith(
            expect.stringContaining("/record"),
            {id: "record-1", targets: "Updated target"}
        ));
        await waitFor(() => expect(screen.getByText("Targets:Updated target$")).toBeInTheDocument());
    });

    test("updates dividend yield through axios and refreshes local record state", async () => {
        render(
            <Record
                data={{
                    id: "record-1",
                    date: "2026-05-09",
                    price: 123,
                    priceToRevenues: 1,
                    priceToGrossProfit: 2,
                    priceToOperatingIncome: 3,
                    priceToNetIncome: 4,
                    dividendYield: 5,
                    targets: "T",
                }}
                currency={"$"}
                setAlert={jest.fn()}
            />
        );

        fireEvent.click(screen.getByText("Dividend yield:5%"));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith(
            expect.stringContaining("/record"),
            {id: "record-1", dividendYield: "6.25"}
        ));
        await waitFor(() => expect(screen.getByText("Dividend yield:6.25%")).toBeInTheDocument());
    });

    test("does not render asset when record has no asset", () => {
        render(
            <Record
                data={{
                    id: "record-1",
                    date: "2026-05-09",
                    price: 123,
                    priceToRevenues: 1,
                    priceToGrossProfit: 2,
                    priceToOperatingIncome: 3,
                    priceToNetIncome: 4,
                    dividendYield: 5,
                    targets: "T",
                    strategy: "S",
                    title: "Initial title",
                    content: "Initial content",
                }}
                currency={"$"}
                setAlert={jest.fn()}
            />
        );

        expect(screen.queryByText(/aggregate:/)).not.toBeInTheDocument();
    });

    test("does not render editor sections with default content", () => {
        render(
            <Record
                data={{
                    id: "record-1",
                    date: "2026-05-09",
                    price: 123,
                    priceToRevenues: 1,
                    priceToGrossProfit: 2,
                    priceToOperatingIncome: 3,
                    priceToNetIncome: 4,
                    dividendYield: 5,
                    targets: "T",
                    review: defaultContent,
                    strategy: defaultContent,
                    retro: defaultContent,
                    content: defaultContent,
                }}
                currency={"$"}
                setAlert={jest.fn()}
            />
        );

        expect(screen.queryByText("Review:")).not.toBeInTheDocument();
        expect(screen.queryByText("Strategy:")).not.toBeInTheDocument();
        expect(screen.queryByText("Retrospective:")).not.toBeInTheDocument();
        expect(screen.queryByText("Content:")).not.toBeInTheDocument();
        expect(screen.getByLabelText("Add review section")).toBeInTheDocument();
        expect(screen.getByLabelText("Add strategy section")).toBeInTheDocument();
        expect(screen.getByLabelText("Add retro section")).toBeInTheDocument();
        expect(screen.getByLabelText("Add content section")).toBeInTheDocument();
    });

    test("renders empty editor section after clicking add button", () => {
        render(
            <Record
                data={{
                    id: "record-1",
                    date: "2026-05-09",
                    price: 123,
                    priceToRevenues: 1,
                    priceToGrossProfit: 2,
                    priceToOperatingIncome: 3,
                    priceToNetIncome: 4,
                    dividendYield: 5,
                    targets: "T",
                }}
                currency={"$"}
                setAlert={jest.fn()}
            />
        );

        fireEvent.click(screen.getByLabelText("Add review section"));

        expect(screen.getByText("Review:")).toBeInTheDocument();
        expect(screen.queryByLabelText("Add review section")).not.toBeInTheDocument();
    });

    test("updates review, strategy, retro and content through axios", async () => {
        render(
            <Record
                data={{
                    id: "record-1",
                    date: "2026-05-09",
                    price: 123,
                    priceToRevenues: 1,
                    priceToGrossProfit: 2,
                    priceToOperatingIncome: 3,
                    priceToNetIncome: 4,
                    dividendYield: 5,
                    targets: "T",
                    review: "Initial review",
                    strategy: "Initial strategy",
                    retro: "Initial retro",
                    content: "Initial content",
                }}
                currency={"$"}
                setAlert={jest.fn()}
            />
        );

        fireEvent.click(screen.getByText("Initial review"));
        fireEvent.click(screen.getByText("Initial strategy"));
        fireEvent.click(screen.getByText("Initial retro"));
        fireEvent.click(screen.getByText("Initial content"));

        const updatedContent = JSON.stringify([{type: "paragraph", children: [{text: "Updated content"}]}]);
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith(
            expect.stringContaining("/record"),
            {id: "record-1", review: updatedContent}
        ));
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith(
            expect.stringContaining("/record"),
            {id: "record-1", strategy: updatedContent}
        ));
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith(
            expect.stringContaining("/record"),
            {id: "record-1", retro: updatedContent}
        ));
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith(
            expect.stringContaining("/record"),
            {id: "record-1", content: updatedContent}
        ));
    });

    test("updates asset through axios", async () => {
        render(
            <Record
                data={{
                    id: "record-1",
                    date: "2026-05-09",
                    price: 123,
                    priceToRevenues: 1,
                    priceToGrossProfit: 2,
                    priceToOperatingIncome: 3,
                    priceToNetIncome: 4,
                    dividendYield: 5,
                    targets: "T",
                    asset: {quantity: 3, purchasePrice: 100},
                }}
                currency={"$"}
                setAlert={jest.fn()}
            />
        );

        fireEvent.click(screen.getByText("aggregate:3@100$::"));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith(
            expect.stringContaining("/record"),
            {
                id: "record-1",
                sumAssetQuantity: "5.5",
                avgAssetPrice: "110.25",
            }
        ));
    });

    test("confirms record deletion", () => {
        const deleteRecord = jest.fn();

        render(
            <Record
                data={{
                    id: "record-1",
                    date: "2026-05-09",
                    price: 123,
                    priceToRevenues: 1,
                    priceToGrossProfit: 2,
                    priceToOperatingIncome: 3,
                    priceToNetIncome: 4,
                    dividendYield: 5,
                    targets: "T",
                }}
                currency={"$"}
                setAlert={jest.fn()}
                deleteRecord={deleteRecord}
            />
        );

        fireEvent.click(screen.getByLabelText("Delete record"));
        expect(screen.getByText("Delete record?")).toBeInTheDocument();

        fireEvent.click(screen.getByText("Cancel"));
        expect(deleteRecord).not.toHaveBeenCalled();

        fireEvent.click(screen.getByLabelText("Delete record"));
        fireEvent.click(screen.getByText("Delete"));

        expect(deleteRecord).toHaveBeenCalledWith("record-1");
    });
});
