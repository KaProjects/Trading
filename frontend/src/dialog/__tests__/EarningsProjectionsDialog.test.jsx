import {fireEvent, render, screen} from "@testing-library/react";
import {EarningsProjectionsDialog} from "../EarningsProjectionsDialog";

const earnings = {
    ttm: {value: 10},
    current: {value: 20},
    next1: {value: 25},
    next2: {value: 40},
    next3: {value: 50},
};

test("projects prices and P/E ratios from an editable target price", () => {
    const handleClose = jest.fn();
    render(
        <EarningsProjectionsDialog
            open
            handleClose={handleClose}
            ticker="NVDA"
            currentPrice={100}
            earnings={earnings}
            previousPeriod={{priceHigh: 90, priceLow: 50}}
        />
    );

    expect(screen.getByRole("heading", {name: "NVDA - Earnings and Price Projections"})).toBeInTheDocument();
    expect(screen.getByRole("columnheader", {name: "Price"})).toBeInTheDocument();
    expect(screen.getByLabelText("Target price")).toHaveValue(100);
    expect(screen.getByLabelText("Target price").value).toBe("100.00");
    expect(screen.getByLabelText("Target price")).toHaveAttribute("type", "number");
    expect(screen.getByLabelText("t + 20% price")).toHaveTextContent("120");
    expect(screen.getByLabelText("target ~ ttm P/E")).toHaveTextContent("10.00");
    expect(screen.getByLabelText("target ~ current P/E")).toHaveTextContent("5.00");

    fireEvent.change(screen.getByLabelText("Target price"), {target: {value: "200"}});

    expect(screen.getByLabelText("t + 20% price")).toHaveTextContent("240");
    expect(screen.getByLabelText("target ~ ttm P/E")).toHaveTextContent("20.00");
    expect(screen.getByLabelText("target ~ next 3 P/E")).toHaveTextContent("4.00");

    expect(screen.getByRole("columnheader", {name: "P/E"})).toBeInTheDocument();
    expect(screen.getByLabelText("Target P/E")).toHaveValue(30);
    expect(screen.getByLabelText("Target P/E")).toHaveAttribute("type", "number");
    expect(screen.getByLabelText("t + 15 P/E")).toHaveTextContent("45");
    expect(screen.getByLabelText("target ~ ttm price")).toHaveTextContent("300");
    expect(screen.getByLabelText("target ~ current price")).toHaveTextContent("600");

    fireEvent.change(screen.getByLabelText("Target P/E"), {target: {value: "20"}});

    expect(screen.getByLabelText("t - 15 P/E")).toHaveTextContent("5");
    expect(screen.getByLabelText("target ~ ttm price")).toHaveTextContent("200");
    expect(screen.getByLabelText("target ~ next 3 price")).toHaveTextContent("1,000");
    expect(screen.getByLabelText("P (H, Q-1) P/E")).toHaveTextContent("9.00");
    expect(screen.getByLabelText("P (H, Q-1) ttm price")).toHaveTextContent("90");
    expect(screen.getByLabelText("P (H, Q-1) current price")).toHaveTextContent("180");
    expect(screen.getByLabelText("P (L, Q-1) P/E")).toHaveTextContent("5.00");
    expect(screen.getByLabelText("P (L, Q-1) next 3 price")).toHaveTextContent("250");

    fireEvent.keyDown(screen.getByLabelText("Target price"), {key: "ArrowUp"});
    expect(screen.getByLabelText("Target price")).toHaveValue(210);
    expect(screen.getByLabelText("Target price").value).toBe("210.00");
    fireEvent.click(screen.getByLabelText("Decrease target price"));
    expect(screen.getByLabelText("Target price")).toHaveValue(199.5);
    expect(screen.getByLabelText("Target price").value).toBe("199.50");

    fireEvent.keyDown(screen.getByLabelText("Target P/E"), {key: "ArrowDown"});
    expect(screen.getByLabelText("Target P/E")).toHaveValue(15);
    fireEvent.click(screen.getByLabelText("Decrease target p/e"));
    expect(screen.getByLabelText("Target P/E")).toHaveValue(15);

    fireEvent.click(screen.getByRole("button", {name: "Close"}));
    expect(handleClose).toHaveBeenCalled();
});

test("uses blanks and dashes when price or earnings are unavailable", () => {
    render(
        <EarningsProjectionsDialog
            open
            handleClose={jest.fn()}
            ticker="INTC"
            currentPrice={null}
            earnings={{ttm: {}, current: {}, next1: {}, next2: {}, next3: {}}}
        />
    );

    expect(screen.getByLabelText("Target price")).toHaveValue(null);
    expect(screen.getByLabelText("t + 20% price")).toHaveTextContent("-");
    expect(screen.getByLabelText("target ~ ttm P/E")).toHaveTextContent("-");
    expect(screen.getByLabelText("target ~ ttm price")).toHaveTextContent("-");
    expect(screen.queryByText("P (H, Q-1)")).not.toBeInTheDocument();
    expect(screen.queryByText("P (L, Q-1)")).not.toBeInTheDocument();

    fireEvent.keyDown(screen.getByLabelText("Target price"), {key: "ArrowDown"});
    expect(screen.getByLabelText("Target price")).toHaveValue(1);
    fireEvent.change(screen.getByLabelText("Target P/E"), {target: {value: ""}});
    fireEvent.click(screen.getByLabelText("Increase target p/e"));
    expect(screen.getByLabelText("Target P/E")).toHaveValue(16);
});
