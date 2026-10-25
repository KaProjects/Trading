import React from "react";
import {fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import axios from "axios";

jest.mock("axios");
jest.mock("../../properties", () => ({backend: "/api"}));

import {StockSplit} from "../StockSplit";

const companyLists = {
    all: [
        {id: 1, ticker: "AMD"},
        {id: 2, ticker: "NVDA"},
    ],
};

function openTrade(overrides = {}) {
    return {
        id: 10,
        active: true,
        purchaseDate: "2018-04-05",
        purchaseQuantity: 10,
        purchasePrice: 100,
        ...overrides,
    };
}

function closedTrade(overrides = {}) {
    return {
        id: 20,
        active: false,
        purchaseDate: "2026-04-06",
        purchaseQuantity: 30,
        purchasePrice: 130,
        sellDate: "2026-07-30",
        ...overrides,
    };
}

function arrange({open = [openTrade()], closed = [], splits = [], splitsFail = false} = {}) {
    axios.get.mockImplementation((url, config) => {
        if (url.endsWith("/company/polygon/splits")) {
            return splitsFail ? Promise.reject(new Error("boom")) : Promise.resolve({data: splits});
        }
        return Promise.resolve({data: {trades: config.params.active ? open : closed}});
    });
}

async function selectCompany(ticker = "NVDA") {
    fireEvent.mouseDown(screen.getByRole("combobox"));
    fireEvent.click(screen.getByRole("option", {name: ticker}));
    await waitFor(() => expect(screen.getByLabelText("Split from")).toBeInTheDocument());
}

function fillSplit({from = "1", to = "10", date = "2026-07-01"} = {}) {
    fireEvent.change(screen.getByLabelText("Split from"), {target: {value: from}});
    fireEvent.change(screen.getByLabelText("Split to"), {target: {value: to}});
    fireEvent.change(screen.getByLabelText("Split date"), {target: {value: date}});
}

function previewRow() {
    return within(screen.getAllByRole("row")[1]).getAllByRole("cell").map(cell => cell.textContent);
}

describe("StockSplit", () => {
    beforeEach(() => {
        axios.get.mockReset();
    });

    test("shows nothing until a company is picked", () => {
        arrange();
        render(<StockSplit companyLists={companyLists}/>);

        expect(screen.queryByLabelText("Split from")).not.toBeInTheDocument();
        expect(axios.get).not.toHaveBeenCalled();
    });

    test("loads the trades as soon as a company is picked, leaving the selector usable", async () => {
        arrange();
        render(<StockSplit companyLists={companyLists}/>);

        await selectCompany();

        expect(screen.getByRole("combobox")).not.toHaveAttribute("aria-disabled");
        expect(axios.get).toHaveBeenCalledWith("/api/trade/", {params: {active: true, companyId: 2}});
        expect(axios.get).toHaveBeenCalledWith("/api/trade/", {params: {active: false, companyId: 2}});
    });

    test("reloads for another company when the selection changes", async () => {
        arrange();
        render(<StockSplit companyLists={companyLists}/>);
        await selectCompany("NVDA");

        await selectCompany("AMD");

        expect(axios.get).toHaveBeenCalledWith("/api/trade/", {params: {active: true, companyId: 1}});
        expect(screen.getByText("Open trades for AMD")).toBeInTheDocument();
    });

    test("previews a forward split as more shares at a lower price", async () => {
        arrange();
        render(<StockSplit companyLists={companyLists}/>);
        await selectCompany();

        fillSplit({from: "1", to: "10"});

        expect(previewRow()).toEqual(["05.04.2018", "10\u2192100", "100.00\u219210.00"]);
    });

    test("previews a reverse split as fewer shares at a higher price", async () => {
        arrange();
        render(<StockSplit companyLists={companyLists}/>);
        await selectCompany();

        fillSplit({from: "10", to: "1"});

        expect(previewRow()).toEqual(["05.04.2018", "10\u21921", "100.00\u21921,000.00"]);
    });

    test("previews an uneven ratio on both quantity and price", async () => {
        arrange();
        render(<StockSplit companyLists={companyLists}/>);
        await selectCompany();

        fillSplit({from: "3", to: "4"});

        expect(previewRow()).toEqual(["05.04.2018", "10\u219213.3333", "100.00\u219275.00"]);
    });

    test("withholds the preview until the ratio and date are complete", async () => {
        arrange();
        render(<StockSplit companyLists={companyLists}/>);
        await selectCompany();

        fillSplit({from: "1", to: "10", date: ""});

        expect(previewRow()).toEqual(["05.04.2018", "10", "100.00"]);
    });

    test("warns about a trade closed on or after the split date", async () => {
        arrange({closed: [closedTrade({sellDate: "2026-07-30"})]});
        render(<StockSplit companyLists={companyLists}/>);
        await selectCompany();

        fillSplit({date: "2026-07-01"});

        expect(screen.getByText(/Closed trades on or after the split date/)).toBeInTheDocument();
        expect(screen.getByText("30 @ 130.00 bought 06.04.2026, sold 30.07.2026")).toBeInTheDocument();
    });

    test("warns about a trade closed exactly on the split date", async () => {
        arrange({closed: [closedTrade({sellDate: "2026-07-01"})]});
        render(<StockSplit companyLists={companyLists}/>);
        await selectCompany();

        fillSplit({date: "2026-07-01"});

        expect(screen.getByText(/Closed trades on or after the split date/)).toBeInTheDocument();
    });

    test("stays quiet about trades closed before the split date", async () => {
        arrange({closed: [closedTrade({sellDate: "2026-06-30"})]});
        render(<StockSplit companyLists={companyLists}/>);
        await selectCompany();

        fillSplit({date: "2026-07-01"});

        expect(screen.queryByText(/Closed trades on or after the split date/)).not.toBeInTheDocument();
    });
});

describe("StockSplit reported splits", () => {
    beforeEach(() => {
        axios.get.mockReset();
    });

    test("fills the ratio and date from a single reported split", async () => {
        arrange({splits: [{splitFrom: 1, splitTo: 10, executionDate: "2026-09-11"}]});
        render(<StockSplit companyLists={companyLists}/>);

        await selectCompany();

        await waitFor(() => expect(screen.getByLabelText("Split from")).toHaveValue("1"));
        expect(screen.getByLabelText("Split to")).toHaveValue("10");
        expect(screen.getByLabelText("Split date")).toHaveValue("2026-09-11");
        expect(screen.queryByText(/reported/)).not.toBeInTheDocument();
    });

    test("takes the latest split and lists them all when several are reported", async () => {
        arrange({splits: [
            {splitFrom: 1, splitTo: 2, executionDate: "2026-08-17"},
            {splitFrom: 3, splitTo: 4, executionDate: "2026-02-23"},
        ]});
        render(<StockSplit companyLists={companyLists}/>);

        await selectCompany();

        await waitFor(() => expect(screen.getByLabelText("Split from")).toHaveValue("1"));
        expect(screen.getByLabelText("Split to")).toHaveValue("2");
        expect(screen.getByLabelText("Split date")).toHaveValue("2026-08-17");
        expect(screen.getByText(/2 splits reported for NVDA/)).toBeInTheDocument();
        expect(screen.getByText("1:2 on 17.08.2026")).toBeInTheDocument();
        expect(screen.getByText("3:4 on 23.02.2026")).toBeInTheDocument();
    });

    test("warns and leaves the fields empty when nothing is reported", async () => {
        arrange({splits: []});
        render(<StockSplit companyLists={companyLists}/>);

        await selectCompany();

        await waitFor(() => expect(screen.getByText(/No split reported for NVDA/)).toBeInTheDocument());
        expect(screen.getByLabelText("Split from")).toHaveValue("");
        expect(screen.getByLabelText("Split date")).toHaveValue("");
    });

    test("warns when the lookup itself fails", async () => {
        arrange({splitsFail: true});
        render(<StockSplit companyLists={companyLists}/>);

        await selectCompany();

        await waitFor(() =>
            expect(screen.getByText(/Could not load reported splits for NVDA/)).toBeInTheDocument());
        expect(screen.getByLabelText("Split from")).toHaveValue("");
    });
});

describe("StockSplit apply", () => {
    beforeEach(() => {
        axios.get.mockReset();
        axios.post.mockReset();
    });

    async function reachPreview() {
        arrange();
        render(<StockSplit companyLists={companyLists}/>);
        await selectCompany();
        fillSplit({from: "1", to: "10", date: "2026-07-01"});
    }

    test("keeps the apply button visible but disabled until the inputs are complete", async () => {
        arrange();
        render(<StockSplit companyLists={companyLists}/>);
        await selectCompany();

        expect(screen.getByRole("button", {name: "Apply split"})).toBeDisabled();

        fillSplit({from: "1", to: "10", date: ""});
        expect(screen.getByRole("button", {name: "Apply split"})).toBeDisabled();

        fillSplit({from: "1", to: "10", date: "2026-07-01"});
        expect(screen.getByRole("button", {name: "Apply split"})).not.toBeDisabled();
    });

    test("keeps the apply button disabled when there is nothing to split", async () => {
        arrange({open: []});
        render(<StockSplit companyLists={companyLists}/>);
        await selectCompany();

        fillSplit({from: "1", to: "10", date: "2026-07-01"});

        expect(screen.getByRole("button", {name: "Apply split"})).toBeDisabled();
    });

    test("asks for confirmation and sends nothing when cancelled", async () => {
        await reachPreview();

        fireEvent.click(screen.getByRole("button", {name: "Apply split"}));
        expect(screen.getByText("Apply split", {selector: "h2"})).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Cancel"}));

        await waitFor(() => expect(screen.queryByRole("button", {name: "Cancel"})).not.toBeInTheDocument());
        expect(axios.post).not.toHaveBeenCalled();
    });

    test("posts the split and reports the outcome once confirmed", async () => {
        axios.post.mockResolvedValue({data: {updatedTrades: 2, recordCreated: true, warnings: []}});
        await reachPreview();

        fireEvent.click(screen.getByRole("button", {name: "Apply split"}));
        fireEvent.click(screen.getByRole("button", {name: "Apply"}));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("/api/split/", {
            companyId: 2,
            splitFrom: 1,
            splitTo: 10,
            date: "2026-07-01",
        }));
        await waitFor(() =>
            expect(screen.getByText(/2 trades updated and a record was created/)).toBeInTheDocument());
        expect(screen.getByLabelText("Split from")).toHaveValue("");
        expect(screen.getByLabelText("Split date")).toHaveValue("");
    });

    test("surfaces a warning returned alongside a successful split", async () => {
        axios.post.mockResolvedValue({data: {
            updatedTrades: 1,
            recordCreated: false,
            warnings: ["Trades were split, but no record was created because the current price is unknown."],
        }});
        await reachPreview();

        fireEvent.click(screen.getByRole("button", {name: "Apply split"}));
        fireEvent.click(screen.getByRole("button", {name: "Apply"}));

        await waitFor(() => expect(screen.getByText(/1 trade updated\./)).toBeInTheDocument());
        expect(screen.getByText(/no record was created/)).toBeInTheDocument();
    });

    test("shows the server error when the split is refused", async () => {
        const refusal = new Error("Request failed with status code 400");
        refusal.name = "AxiosError";
        refusal.response = {data: "split would make the quantity too large to store"};
        axios.post.mockRejectedValue(refusal);
        await reachPreview();

        fireEvent.click(screen.getByRole("button", {name: "Apply split"}));
        fireEvent.click(screen.getByRole("button", {name: "Apply"}));

        await waitFor(() =>
            expect(screen.getByText(/too large to store/)).toBeInTheDocument());
        expect(screen.queryByText(/trades updated/)).not.toBeInTheDocument();
    });
});
