import {fireEvent, render, screen} from "@testing-library/react";
import {MarketOverview} from "../MarketOverview";

describe("MarketOverview", () => {
    test("shows the market cap and beta, each over its label", () => {
        render(<MarketOverview marketCap={4234000} beta={1.74}/>);

        const widget = screen.getByTestId("market-overview");
        expect(widget).toHaveTextContent("mcap");
        expect(widget).toHaveTextContent("4.23T");
        expect(widget).toHaveTextContent("beta");
        expect(widget).toHaveTextContent("1.74");
    });

    test("shows the market cap alone until the processor supplies a beta", () => {
        render(<MarketOverview marketCap={1000}/>);

        expect(screen.getByTestId("market-overview")).toHaveTextContent("1B");
        expect(screen.queryByText("beta")).not.toBeInTheDocument();
    });

    test("shows beta alone when the market cap cannot be computed", () => {
        render(<MarketOverview beta={0.82}/>);

        expect(screen.getByTestId("market-overview")).toHaveTextContent("0.82");
        expect(screen.queryByText("mcap")).not.toBeInTheDocument();
    });

    test("offers to fold the widgets away, and says which way it will go", () => {
        const onToggleCollapse = jest.fn();

        const {rerender} = render(
            <MarketOverview marketCap={1000} beta={1.2} collapsible onToggleCollapse={onToggleCollapse}/>
        );

        fireEvent.click(screen.getByRole("button", {name: "Hide widgets"}));
        expect(onToggleCollapse).toHaveBeenCalled();

        rerender(
            <MarketOverview marketCap={1000} beta={1.2} collapsible collapsed onToggleCollapse={onToggleCollapse}/>
        );
        expect(screen.getByRole("button", {name: "Show widgets"})).toBeInTheDocument();
    });

    test("offers nothing to fold when the page did not ask for it", () => {
        render(<MarketOverview marketCap={1000} beta={1.2}/>);

        expect(screen.queryByRole("button")).not.toBeInTheDocument();
    });

    test("renders nothing when it has neither", () => {
        render(<MarketOverview/>);

        expect(screen.queryByTestId("market-overview")).not.toBeInTheDocument();
    });

    test("ignores a beta that is not a number", () => {
        render(<MarketOverview marketCap={1000} beta="n/a"/>);

        expect(screen.queryByText("beta")).not.toBeInTheDocument();
    });
});
