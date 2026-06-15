import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

jest.mock("axios");
jest.mock("../EditableTypography", () => ({
    EditableTypography: ({value, update}) => (
        <button onClick={() => update("Updated title")}>{value}</button>
    )
}));
jest.mock("../ContentEditor", () => ({
    ContentEditor: ({content, update}) => (
        <button onClick={() => update([{type: "paragraph", children: [{text: "Updated content"}]}])}>
            {content}
        </button>
    )
}));
jest.mock("../AssetBox", () => ({
    AssetBox: ({asset, currency}) => (
        <div>{asset.quantity}@{asset.purchasePrice}{currency}</div>
    )
}));

import {Record} from "../Record";

describe("Record", () => {
    beforeEach(() => {
        axios.put.mockReset();
        axios.put.mockResolvedValue({});
    });

    test("renders date, summary values and asset", () => {
        render(
            <Record
                record={{
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
                    asset: {quantity: 3, purchasePrice: 100},
                }}
                currency={"$"}
                setAlert={jest.fn()}
            />
        );

        expect(screen.getByText("09.05.2026")).toBeInTheDocument();
        expect(screen.getByText("$123")).toBeInTheDocument();
        expect(screen.getByText("PS:1")).toBeInTheDocument();
        expect(screen.getByText("PG:2")).toBeInTheDocument();
        expect(screen.getByText("PO:3")).toBeInTheDocument();
        expect(screen.getByText("PE:4")).toBeInTheDocument();
        expect(screen.getByText("DY:5")).toBeInTheDocument();
        expect(screen.getByText("t:T")).toBeInTheDocument();
        expect(screen.getByText("s:S")).toBeInTheDocument();
        expect(screen.getByText("3@100$")).toBeInTheDocument();
    });

    test("updates title and content through axios", async () => {
        render(
            <Record
                record={{
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

    test("does not render asset when record has no asset", () => {
        render(
            <Record
                record={{
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

        expect(screen.queryByText("3@100$")).not.toBeInTheDocument();
    });
});
