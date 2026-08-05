import {fireEvent, render, screen} from "@testing-library/react";
import {AssetBox} from "../AssetBox";

describe("AssetBox", () => {
    test("renders quantity, purchase price and currency", () => {
        render(
            <AssetBox
                asset={{quantity: 3, purchasePrice: 120, profitPercent: null}}
                currency={"$"}
                style={{marginLeft: "10px"}}
            />
        );

        expect(screen.getByText("3@120$")).toBeInTheDocument();
        expect(screen.getByTestId("asset-box")).toHaveStyle("margin-left: 10px");
    });

    test("renders positive profit percent with plus sign and rounding", () => {
        render(<AssetBox asset={{quantity: 3, purchasePrice: 120, profitPercent: 12.345}} currency={"$"}/>);

        expect(screen.getByText("+12.35%")).toBeInTheDocument();
    });

    test("renders negative and zero profit percentages", () => {
        const {rerender} = render(
            <AssetBox asset={{quantity: 3, purchasePrice: 120, profitPercent: -4.2}} currency={"$"}/>
        );
        expect(screen.getByText("-4.2%")).toBeInTheDocument();

        rerender(<AssetBox asset={{quantity: 3, purchasePrice: 120, profitPercent: 0}} currency={"$"}/>);
        expect(screen.getByText("0%")).toBeInTheDocument();
    });

    test("does not render profit percent when value is not numeric", () => {
        render(<AssetBox asset={{quantity: 3, purchasePrice: 120, profitPercent: "abc"}} currency={"$"}/>);

        expect(screen.queryByText(/%/)).not.toBeInTheDocument();
    });

    test("is view-only", () => {
        render(<AssetBox asset={{quantity: 3, purchasePrice: 120, profitPercent: 12}} currency={"$"}/>);

        fireEvent.click(screen.getByText("3@120$"));
        expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    });
});
