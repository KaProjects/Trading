import {render, screen, waitFor} from "@testing-library/react";
import React from "react";
import {TradingViewWidget} from "../TradingViewWidget";

describe("TradingViewWidget", () => {
    test("embeds the TradingView script with its serialized configuration", async () => {
        const config = {symbol: "NASDAQ:NVDA", autosize: true};

        const {unmount} = render(
            <React.StrictMode>
                <TradingViewWidget
                    title="NVDA TradingView chart"
                    scriptUrl="https://example.test/tradingview.js"
                    config={config}
                />
            </React.StrictMode>
        );

        const widget = screen.getByRole("region", {name: "NVDA TradingView chart"});
        await waitFor(() => expect(widget.querySelector("script")).toBeInTheDocument());
        const script = widget.querySelector("script");

        expect(widget.querySelector(".tradingview-widget-container__widget")).toBeInTheDocument();
        expect(script).toHaveAttribute("src", "https://example.test/tradingview.js");
        expect(script).toHaveAttribute("type", "text/javascript");
        expect(script.async).toBe(true);
        expect(JSON.parse(script.textContent)).toEqual(config);
        expect(widget.querySelectorAll("script")).toHaveLength(1);

        unmount();
        expect(script.parentElement).not.toBeNull();
    });
});
