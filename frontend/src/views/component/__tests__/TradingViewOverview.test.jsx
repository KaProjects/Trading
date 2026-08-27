import {fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {TradingViewOverview} from "../TradingViewOverview";

const company = {
    ticker: "NVDA",
    exchange: {tradingViewCode: "NASDAQ"},
};

class MockTradingViewTicker extends HTMLElement {
    connectedCallback() {
        if (this.errorBoundary) return;

        const root = this.attachShadow({mode: "closed"});
        this.errorBoundary = document.createElement("tv-error-boundary");
        root.appendChild(this.errorBoundary);
    }

    reportError() {
        this.errorBoundary.setAttribute("has-error", "");
    }
}

describe("TradingViewOverview", () => {
    beforeAll(() => {
        if (!customElements.get("tv-single-ticker")) {
            customElements.define("tv-single-ticker", MockTradingViewTicker);
        }
    });

    beforeEach(() => document.getElementById("tradingview-single-ticker-script")?.remove());

    test("renders the compact ticker and opens the advanced chart", async () => {
        const {container} = render(<TradingViewOverview company={company}/>);

        await waitFor(() => expect(container.querySelector("tv-single-ticker")).toBeInTheDocument());
        const ticker = container.querySelector("tv-single-ticker");
        expect(ticker).toHaveAttribute("symbol", "NASDAQ:NVDA");
        expect(ticker.style.height).toBe("");
        expect(screen.getByRole("button", {name: "Open NVDA TradingView chart"}).parentElement)
            .toHaveStyle({height: "76px", overflow: "hidden"});
        expect(screen.queryByText("TradingView")).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Open NVDA TradingView chart"}));

        const dialog = await screen.findByRole("dialog");
        expect(within(dialog).getByText("NVDA chart")).toBeInTheDocument();
        const chart = within(dialog).getByRole("region", {name: "NVDA TradingView chart"});
        await waitFor(() => expect(chart.querySelector("script")).toBeInTheDocument());
        const script = chart.querySelector("script");
        expect(script).toHaveAttribute(
            "src",
            "https://s3.tradingview.com/external-embedding/embed-widget-advanced-chart.js"
        );
        expect(JSON.parse(script.textContent)).toMatchObject({
            symbol: "NASDAQ:NVDA",
            autosize: true,
            allow_symbol_change: false,
        });

        fireEvent.click(within(dialog).getByRole("button", {name: "Close TradingView chart"}));
        await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    });

    test("reports a TradingView data error", async () => {
        const onUnavailable = jest.fn();
        const {container} = render(
            <TradingViewOverview company={company} onUnavailable={onUnavailable}/>
        );

        await waitFor(() => expect(container.querySelector("tv-single-ticker")).toBeInTheDocument());
        container.querySelector("tv-single-ticker").reportError();

        await waitFor(() => expect(onUnavailable).toHaveBeenCalledTimes(1));
    });

    test("renders nothing when the company has no TradingView exchange", () => {
        const {container} = render(<TradingViewOverview company={{ticker: "NVDA", exchange: null}}/>);

        expect(container).toBeEmptyDOMElement();
        expect(document.getElementById("tradingview-single-ticker-script")).not.toBeInTheDocument();
    });
});
