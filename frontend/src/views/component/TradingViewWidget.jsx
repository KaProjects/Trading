import {Box} from "@mui/material";
import React, {useEffect, useRef} from "react";

export const TradingViewWidget = ({config, scriptUrl, title, sx}) => {
    const containerRef = useRef(null)
    const serializedConfig = JSON.stringify(config)

    useEffect(() => {
        const container = containerRef.current
        if (!container) return undefined

        const initialization = window.setTimeout(() => {
            if (!container.isConnected) return

            const widget = document.createElement("div")
            widget.className = "tradingview-widget-container__widget"
            widget.style.width = "100%"
            widget.style.height = "100%"

            const script = document.createElement("script")
            script.type = "text/javascript"
            script.src = scriptUrl
            script.async = true
            script.textContent = serializedConfig

            container.replaceChildren(widget, script)
        }, 0)

        // Once loading starts, keep the script attached to its widget container.
        return () => window.clearTimeout(initialization)
    }, [scriptUrl, serializedConfig])

    return (
        <Box
            ref={containerRef}
            className="tradingview-widget-container"
            role="region"
            aria-label={title}
            sx={{width: "100%", height: "100%", ...sx}}
        />
    )
}
