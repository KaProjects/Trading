import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import {AssetsSummary} from "../AssetsSummary";

const latest = {price: 90, datetime: "2026-07-24T16:00:00"};

const assets = {
    assets: [
        {quantity: 12, purchasePrice: 82.4, profitPercent: 9.22, profitValue: 91.2, purchaseDate: "2025-03-14"},
        {quantity: 5, purchasePrice: 95.5, profitPercent: -5.76, profitValue: -27.5, purchaseDate: "2026-01-08"},
    ],
    aggregate: {quantity: 17, purchasePrice: 86.26, profitPercent: 4.11, profitValue: 63.7},
};

describe("AssetsSummary", () => {
    test("shows the price with its timestamp and the asset aggregate", () => {
        render(<AssetsSummary latest={latest} assets={assets} currency="€"/>);

        expect(screen.getByText("€90")).toBeInTheDocument();
        expect(screen.getByText("24.07.2026")).toBeInTheDocument();
        expect(screen.getByText("16:00:00")).toBeInTheDocument();
        expect(screen.getByTestId("record-assets-aggregate")).toHaveTextContent("17@86.26€");
        expect(screen.getByTestId("record-assets-aggregate-profit")).toHaveTextContent("+63.7€(+4.11%)");
        expect(screen.queryByTestId("record-assets-list")).not.toBeInTheDocument();
    });

    test("expands and collapses the asset list on click", async () => {
        render(<AssetsSummary latest={latest} assets={assets} currency="€"/>);

        fireEvent.click(screen.getByRole("button", {name: "Show assets"}));

        const items = screen.getAllByTestId("record-assets-item");
        expect(items).toHaveLength(2);
        expect(items[0]).toHaveTextContent("14.03.2025");
        expect(items[0]).toHaveTextContent("12@82.4€");
        expect(items[0]).toHaveTextContent("+91.2€(+9.22%)");
        expect(items[1]).toHaveTextContent("08.01.2026");
        expect(items[1]).toHaveTextContent("5@95.5€");
        expect(items[1]).toHaveTextContent("-27.5€(-5.76%)");

        fireEvent.click(screen.getByRole("button", {name: "Hide assets"}));
        await waitFor(() => expect(screen.queryAllByTestId("record-assets-item")).toHaveLength(0));
    });

    test("shows only the price when there are no assets", () => {
        render(<AssetsSummary latest={latest} assets={{assets: []}} currency="€"/>);

        expect(screen.getByText("€90")).toBeInTheDocument();
        expect(screen.queryByTestId("record-assets-aggregate")).not.toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Show assets"})).toBeDisabled();
    });

    test("renders nothing without a price and assets", () => {
        const {container} = render(<AssetsSummary assets={{assets: []}} currency="€"/>);

        expect(container).toBeEmptyDOMElement();
    });
});
