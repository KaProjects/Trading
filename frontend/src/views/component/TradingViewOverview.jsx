import {
    Box,
    ButtonBase,
    Dialog,
    DialogContent,
    DialogTitle,
    IconButton,
    useTheme,
} from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import {useEffect, useRef, useState} from "react";
import {TradingViewWidget} from "./TradingViewWidget";

const SINGLE_TICKER_TAG = "tv-single-ticker"
const SINGLE_TICKER_SCRIPT_ID = "tradingview-single-ticker-script"
const SINGLE_TICKER_SCRIPT_URL = "https://widgets.tradingview-widget.com/w/en/tv-single-ticker.js"
const ADVANCED_CHART_SCRIPT_URL = "https://s3.tradingview.com/external-embedding/embed-widget-advanced-chart.js"
const SINGLE_TICKER_BODY_HEIGHT = "76px"
const SINGLE_TICKER_ERROR_SELECTOR = "tv-error-boundary[has-error]"
const SINGLE_TICKER_LOAD_TIMEOUT = 10000
let singleTickerScriptPromise

function loadSingleTicker() {
    if (customElements.get(SINGLE_TICKER_TAG)) return Promise.resolve()
    if (singleTickerScriptPromise) return singleTickerScriptPromise

    singleTickerScriptPromise = new Promise((resolve, reject) => {
        let script = document.getElementById(SINGLE_TICKER_SCRIPT_ID)
        if (!script) {
            script = document.createElement("script")
            script.id = SINGLE_TICKER_SCRIPT_ID
            script.type = "module"
            script.src = SINGLE_TICKER_SCRIPT_URL
            document.head.appendChild(script)
        }

        const timeout = window.setTimeout(
            () => reject(new Error("TradingView ticker did not load")),
            SINGLE_TICKER_LOAD_TIMEOUT
        )
        script.addEventListener("error", () => reject(new Error("TradingView ticker failed to load")), {once: true})
        customElements.whenDefined(SINGLE_TICKER_TAG).then(() => {
            window.clearTimeout(timeout)
            resolve()
        })
    }).catch(error => {
        singleTickerScriptPromise = undefined
        throw error
    })

    return singleTickerScriptPromise
}

const TradingViewSingleTicker = ({symbol, onUnavailable}) => {
    const containerRef = useRef(null)
    const onUnavailableRef = useRef(onUnavailable)

    useEffect(() => {
        onUnavailableRef.current = onUnavailable
    }, [onUnavailable])

    useEffect(() => {
        let active = true
        let ticker
        let observer

        loadSingleTicker().then(() => {
            if (!active || !containerRef.current) return

            let renderRoot
            const originalAttachShadow = Element.prototype.attachShadow
            Element.prototype.attachShadow = function(options) {
                const root = originalAttachShadow.call(this, options)
                if (this.localName === SINGLE_TICKER_TAG) renderRoot = root
                return root
            }

            try {
                ticker = document.createElement(SINGLE_TICKER_TAG)
                ticker.setAttribute("symbol", symbol)
                ticker.style.display = "block"
                ticker.style.width = "100%"
                containerRef.current.replaceChildren(ticker)
            } finally {
                Element.prototype.attachShadow = originalAttachShadow
            }

            renderRoot ??= ticker.shadowRoot
            if (!renderRoot) return

            const detectError = () => {
                if (renderRoot.querySelector(SINGLE_TICKER_ERROR_SELECTOR)) {
                    onUnavailableRef.current?.()
                }
            }
            observer = new MutationObserver(detectError)
            observer.observe(renderRoot, {
                attributes: true,
                childList: true,
                subtree: true,
                attributeFilter: ["has-error"],
            })
            detectError()
        }).catch(() => {
            if (active) onUnavailableRef.current?.()
        })

        return () => {
            active = false
            observer?.disconnect()
            ticker?.remove()
        }
    }, [symbol])

    return <Box ref={containerRef} sx={{display: "block", width: "100%"}}/>
}

export const TradingViewOverview = ({company, sx, onUnavailable}) => {
    const [open, setOpen] = useState(false)
    const theme = useTheme()
    const exchange = company?.exchange?.tradingViewCode
    const symbol = exchange && company?.ticker ? `${exchange}:${company.ticker}` : null

    useEffect(() => setOpen(false), [symbol])

    if (!symbol) return null

    const colorTheme = theme.palette.mode === "dark" ? "dark" : "light"
    const chartConfig = {
        autosize: true,
        symbol,
        interval: "D",
        timezone: "Etc/UTC",
        theme: colorTheme,
        style: "1",
        locale: "en",
        allow_symbol_change: false,
        calendar: false,
        details: false,
        hide_side_toolbar: true,
        hide_top_toolbar: false,
        hide_legend: false,
        hide_volume: false,
        hotlist: false,
        save_image: false,
        withdateranges: true,
        support_host: "https://www.tradingview.com",
    }

    return (
        <>
            <Box sx={{
                ...sx,
                position: "relative",
                minWidth: 0,
                height: SINGLE_TICKER_BODY_HEIGHT,
                overflow: "hidden",
            }}>
                <Box sx={{pointerEvents: "none"}}>
                    <TradingViewSingleTicker symbol={symbol} onUnavailable={onUnavailable}/>
                </Box>
                <ButtonBase
                    aria-label={`Open ${company.ticker} TradingView chart`}
                    disableRipple
                    onClick={() => setOpen(true)}
                    sx={{position: "absolute", inset: 0, zIndex: 1}}
                />
            </Box>
            <Dialog
                open={open}
                onClose={() => setOpen(false)}
                maxWidth="lg"
                fullWidth
                slotProps={{
                    paper: {
                        sx: {
                            height: {xs: "100dvh", sm: "82vh"},
                            maxHeight: {xs: "100dvh", sm: "900px"},
                        },
                    },
                }}
            >
                <DialogTitle sx={{display: "flex", alignItems: "center", justifyContent: "space-between", paddingY: "8px"}}>
                    {company.ticker} chart
                    <IconButton aria-label="Close TradingView chart" onClick={() => setOpen(false)}>
                        <CloseIcon/>
                    </IconButton>
                </DialogTitle>
                <DialogContent sx={{padding: 0, display: "flex", minHeight: 0}}>
                    {open &&
                        <TradingViewWidget
                            key={`${symbol}-${colorTheme}`}
                            title={`${company.ticker} TradingView chart`}
                            scriptUrl={ADVANCED_CHART_SCRIPT_URL}
                            config={chartConfig}
                        />
                    }
                </DialogContent>
            </Dialog>
        </>
    )
}
