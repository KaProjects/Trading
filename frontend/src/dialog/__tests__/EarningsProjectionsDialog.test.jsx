import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";
import {EarningsProjectionsDialog} from "../EarningsProjectionsDialog";

jest.mock("axios");
jest.mock("../../properties", () => ({backend: "http://backend"}));

const latestPeriod = {
    id: "period-1",
    name: {year: "2026", type: "Q2"},
    estimate: {
        past4: 1,
        past3: 2,
        past2: 3,
        past1: 4,
        current: 11,
        next1: 7,
        next2: 18,
        next3: 14,
    },
};

beforeEach(() => {
    axios.post.mockReset();
});

test("projects prices and P/E ratios from an editable target price", () => {
    const handleClose = jest.fn();
    render(
        <EarningsProjectionsDialog
            open
            handleClose={handleClose}
            ticker="NVDA"
            currentPrice={100}
            latestPeriod={latestPeriod}
            previousPeriod={{priceHigh: 90, priceLow: 50}}
        />
    );

    expect(screen.getByRole("heading", {name: "NVDA - 26Q2 - Earnings and Price Projections"})).toBeInTheDocument();
    expect(screen.getByLabelText("Past 4")).toHaveValue("1");
    expect(screen.getByLabelText("Current")).toHaveValue("11");
    expect(screen.getByLabelText("Next 3")).toHaveValue("14");
    expect(screen.getByLabelText("Past 4")).toHaveAttribute("type", "text");
    expect(screen.getByLabelText("Save estimate")).toBeDisabled();
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

    fireEvent.change(screen.getByLabelText("Past 4"), {target: {value: "31"}});
    expect(screen.getByLabelText("target ~ ttm P/E")).toHaveTextContent("5.00");
    expect(screen.getByLabelText("target ~ current P/E")).toHaveTextContent("10.00");
    fireEvent.change(screen.getByLabelText("Past 4"), {target: {value: "31,5"}});
    expect(screen.getByLabelText("Past 4")).toHaveValue("31");
    fireEvent.change(screen.getByLabelText("Past 4"), {target: {value: "31.5"}});
    expect(screen.getByLabelText("Past 4")).toHaveValue("31.5");

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

test("persists changed current and forward estimates after confirmation", async () => {
    const triggerRefresh = jest.fn();
    axios.post.mockResolvedValue({});
    render(
        <EarningsProjectionsDialog
            open
            handleClose={jest.fn()}
            triggerRefresh={triggerRefresh}
            ticker="NVDA"
            currentPrice={100}
            latestPeriod={latestPeriod}
        />
    );

    const saveButton = screen.getByLabelText("Save estimate");
    expect(saveButton).toBeDisabled();

    fireEvent.change(screen.getByLabelText("Past 1"), {target: {value: "5"}});
    expect(saveButton).toBeDisabled();

    fireEvent.change(screen.getByLabelText("Current"), {target: {value: ""}});
    expect(saveButton).toBeDisabled();
    fireEvent.change(screen.getByLabelText("Current"), {target: {value: "12"}});
    expect(saveButton).toBeEnabled();

    fireEvent.click(saveButton);
    expect(screen.getByRole("heading", {name: "Persist Estimate"})).toBeInTheDocument();
    expect(screen.getByText(/persist new estimate values for NVDA 26Q2 as of \d{2}\.\d{2}\.\d{4}/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", {name: "Add"}));

    const date = new Date();
    const expectedDate = [
        date.getFullYear(),
        String(date.getMonth() + 1).padStart(2, "0"),
        String(date.getDate()).padStart(2, "0"),
    ].join("-");
    await waitFor(() => expect(axios.post).toHaveBeenCalledWith("http://backend/estimate/period-1", {
        date: expectedDate,
        current: "12",
        next1: "7",
        next2: "18",
        next3: "14",
    }));
    await waitFor(() => expect(triggerRefresh).toHaveBeenCalled());
    expect(saveButton).toBeDisabled();
});

test("uses blanks and dashes when price or earnings are unavailable", () => {
    render(
        <EarningsProjectionsDialog
            open
            handleClose={jest.fn()}
            ticker="INTC"
            currentPrice={null}
            latestPeriod={{name: "26Q2", estimate: {}}}
        />
    );

    expect(screen.getByLabelText("Target price")).toHaveValue(null);
    expect(screen.getByLabelText("t + 20% price")).toHaveTextContent("-");
    expect(screen.getByLabelText("target ~ ttm P/E")).toHaveTextContent("-");
    expect(screen.getByLabelText("target ~ ttm price")).toHaveTextContent("-");
    expect(screen.queryByText("P (H, Q-1)")).not.toBeInTheDocument();
    expect(screen.queryByText("P (L, Q-1)")).not.toBeInTheDocument();
    expect(screen.getAllByText("Required")).toHaveLength(8);

    fireEvent.keyDown(screen.getByLabelText("Target price"), {key: "ArrowDown"});
    expect(screen.getByLabelText("Target price")).toHaveValue(1);
    fireEvent.change(screen.getByLabelText("Target P/E"), {target: {value: ""}});
    fireEvent.click(screen.getByLabelText("Increase target p/e"));
    expect(screen.getByLabelText("Target P/E")).toHaveValue(16);
});
