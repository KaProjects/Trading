import {render, screen} from "@testing-library/react";
import {AssetBox} from "../AssetBox";

describe("AssetBox", () => {
    test("renders quantity, purchase price and currency", () => {
        render(<AssetBox asset={{quantity: 3, purchasePrice: 120, profitPercent: null}} currency={"$"}/>);

        expect(screen.getByText("3@120$")).toBeInTheDocument();
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
});
