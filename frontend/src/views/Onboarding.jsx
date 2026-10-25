import React, {useEffect, useState} from "react";
import {
    Alert,
    AlertTitle,
    Box,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    Link,
    Paper,
    Tab,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Tabs,
    TextField,
    Typography,
} from "@mui/material";
import ArrowRightAltIcon from "@mui/icons-material/ArrowRightAlt";
import axios from "axios";
import useMediaQuery from "@mui/material/useMediaQuery";
import {backend} from "../properties";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutline";
import RemoveCircleOutlineIcon from "@mui/icons-material/RemoveCircleOutline";
import {COMPACT_TABS_BREAKPOINT, MainBarTabs} from "./component/MainBarTabs";
import {SWIPE_AREA_SX, useSwipeNavigation} from "./component/useSwipeNavigation";
import {EditCompanyDialog} from "../dialog/EditCompanyDialog";
import {
    formatDate,
    formatDecimals,
    formatError,
    formatMillionsRounded,
    formatPercent,
} from "../service/FormattingService";

const TICKER_PATTERN = /^[A-Z][A-Z0-9.-]{0,14}$/

const FINANCIAL_ROWS = [
    {label: "Ending", value: quarter => quarter.endingMonth},
    {label: "Reported", value: quarter => formatDate(quarter.reportDate)},
    {label: "Revenue", value: quarter => millions(quarter.revenue)},
    {label: "Gross P.", value: quarter => millions(quarter.grossProfit)},
    {label: "Op. Inc.", value: quarter => millions(quarter.operatingIncome)},
    {label: "Net Inc.", value: quarter => millions(quarter.netIncome)},
    {label: "Dividend", value: quarter => millions(quarter.dividend)},
    {label: "CapEx", value: quarter => millions(quarter.capex)},
    {label: "FCF", value: quarter => millions(quarter.freeCashFlow)},
    {label: "Shares", value: quarter => millions(quarter.shares)},
    {label: "Adj. EPS", value: quarter => decimals(quarter.adjustedEps)},
    {label: "L - H", value: quarter => quarter.priceMin === null || quarter.priceMax === null
        ? "-"
        : `${decimals(quarter.priceMin)} - ${decimals(quarter.priceMax)}`},
]

function millions(value) {
    return value === null || value === undefined ? "-" : formatMillionsRounded(Number(value))
}

function decimals(value) {
    return value === null || value === undefined ? "-" : formatDecimals(Number(value), 0, 2)
}

/**
 * Loads one onboarding tab. A manual section is not fetched when the ticker is checked:
 * it waits for run(), because asking Gemini for analyst research is slow and costs a
 * request per attempt, so it should be a deliberate act rather than a side effect of
 * pressing Check. Changing the ticker always clears whatever was loaded before.
 */
function useOnboardingSection(path, ticker, manual = false) {
    const [data, setData] = useState(null)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState(null)
    // Which ticker the user asked this section for. Storing the ticker rather than a counter
    // means a new ticker makes the section inactive again on its own, with no second effect
    // resetting state underneath a request that is already in flight.
    const [requestedFor, setRequestedFor] = useState(null)

    const active = Boolean(ticker) && (!manual || requestedFor === ticker)

    useEffect(() => {
        if (!active) {
            setData(null)
            setError(null)
            setLoading(false)
            return
        }

        let current = true
        setData(null)
        setError(null)
        setLoading(true)
        axios.get(backend + path, {params: {ticker}})
            .then(response => {
                if (current) setData(response.data)
            })
            .catch(error => {
                if (current) setError(formatError(error))
            })
            .finally(() => {
                if (current) setLoading(false)
            })

        return () => {
            current = false
        }
    }, [path, ticker, active])

    return {data, loading, error, requested: active && manual, run: () => setRequestedFor(ticker)}
}

function Section({title, children}) {
    return (
        <Box sx={{width: "100%", maxWidth: 900, margin: "16px auto", padding: "0 8px"}}>
            <Typography color="text.secondary" sx={{marginBottom: "8px"}}>{title}</Typography>
            {children}
        </Box>
    )
}

function Field({label, value}) {
    if (value === null || value === undefined || value === "") return null

    return (
        <Box sx={{display: "flex", gap: "8px", marginTop: "4px"}}>
            <Box sx={{color: "text.secondary", fontSize: 14, minWidth: 90}}>{label}</Box>
            <Box sx={{fontSize: 14}}>{value}</Box>
        </Box>
    )
}

function Warnings({warnings}) {
    if (!warnings || warnings.length === 0) return null

    return (
        <Alert severity="warning" sx={{marginBottom: "8px"}}>
            {warnings.map(warning => <Box key={warning}>{warning}</Box>)}
        </Alert>
    )
}

function Takeaways({items}) {
    if (!items || items.length === 0) return null

    return (
        <Box component="ul" sx={{margin: "6px 0 0 0", paddingLeft: "20px"}}>
            {items.map(item => (
                <Box component="li" key={item} sx={{fontSize: 14, marginTop: "2px"}}>{item}</Box>
            ))}
        </Box>
    )
}

/**
 * One destination a company can be in: Firebase, or this app's database. The status and the
 * action that resolves it sit together on one compact line - a tick or a dash, the
 * destination, then the button right beside it - so the two read as one statement rather
 * than as a label and a far-away control. The two destinations are independent: a company
 * can be in either, both, or neither.
 */
function DestinationRow({testId, destination, present, action}) {
    return (
        <Box
            data-testid={testId}
            sx={{
                display: "flex",
                alignItems: "center",
                gap: "8px",
                minHeight: "30px",
                paddingLeft: "8px",
                borderLeft: "3px solid",
                borderColor: present ? "success.main" : "warning.main",
            }}
        >
            {present
                ? <CheckCircleOutlineIcon sx={{fontSize: 16, color: "success.main"}}/>
                : <RemoveCircleOutlineIcon sx={{fontSize: 16, color: "warning.main"}}/>}
            <Typography sx={{fontSize: 13, color: "text.secondary", minWidth: 58}}>
                {destination}
            </Typography>
            <Typography sx={{fontSize: 13, fontWeight: 600, minWidth: 54}}>
                {present ? "present" : "missing"}
            </Typography>
            {!present && action}
        </Box>
    )
}

/**
 * The action sits on the same line as the status it resolves, so it is styled to read as part
 * of that sentence rather than as a separate control: text button, sentence case, the same
 * type scale as the words beside it.
 */
const ROW_ACTION_SX = {
    fontSize: 13,
    textTransform: "none",
    paddingX: "6px",
    paddingY: 0,
    minWidth: 0,
    minHeight: 0,
}

function SectionState({name, loading, error, loadingText}) {
    if (loading) {
        return (
            <Paper
                data-testid={`onboarding-${name}-loading`}
                sx={{padding: "12px", display: "flex", alignItems: "center", gap: "10px"}}
            >
                <CircularProgress size={20}/>
                <Typography sx={{fontSize: 14, color: "text.secondary"}}>{loadingText}</Typography>
            </Paper>
        )
    }
    if (error) {
        return (
            <Alert severity="error" variant="filled" data-testid={`onboarding-${name}-error`}>
                <AlertTitle>{error.title}</AlertTitle>{error.message}
            </Alert>
        )
    }
    return null
}

function TargetStats({stats}) {
    if (!stats || stats.count === 0) return null

    const summary = `${stats.count}@(${decimals(stats.maximum)}-${decimals(stats.minimum)})`
        + `~${decimals(stats.average)}$`

    return (
        <Typography data-testid="onboarding-target-stats" sx={{fontFamily: "Roboto", fontSize: 17, fontWeight: 500}}>
            {summary}
        </Typography>
    )
}

function AnalystResearch({targets}) {
    return (
        <Paper data-testid="onboarding-targets" sx={{padding: "12px"}}>
            <Warnings warnings={targets.warnings}/>
            <Box sx={{display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: "10px"}}>
                <Typography sx={{color: "text.secondary", fontSize: 12}}>
                    {formatDate(targets.from)} - {formatDate(targets.to)}
                    {" | "}{targets.institutions.length} trusted institutions
                </Typography>
                <TargetStats stats={targets.stats}/>
            </Box>

            {targets.report &&
                <Box data-testid="onboarding-targets-report" sx={{marginTop: "8px"}}>
                    <Typography sx={{fontSize: 14}}>{targets.report.overview}</Typography>
                    <Takeaways items={targets.report.keyTakeaways}/>
                </Box>
            }

            {targets.targets.map(target => (
                <Box
                    key={`${target.institution}-${target.date}-${target.price}`}
                    data-testid="onboarding-target"
                    sx={{marginTop: "10px"}}
                >
                    <Divider sx={{marginBottom: "8px"}}/>
                    <Box sx={{display: "flex", gap: "8px", alignItems: "baseline", flexWrap: "wrap"}}>
                        <Typography sx={{fontWeight: 600, fontSize: 14}}>{target.institution}</Typography>
                        <Typography sx={{fontFamily: "Roboto", fontSize: 14}}>{decimals(target.price)}$</Typography>
                        {target.rating &&
                            <Typography sx={{color: "text.secondary", fontSize: 13}}>{target.rating}</Typography>
                        }
                        <Typography sx={{color: "text.secondary", fontSize: 12}}>{formatDate(target.date)}</Typography>
                        {target.source &&
                            <Link href={target.source} target="_blank" rel="noreferrer" sx={{fontSize: 12}}>source</Link>
                        }
                    </Box>
                    {target.overview &&
                        <Typography sx={{fontSize: 14, marginTop: "4px"}}>{target.overview}</Typography>
                    }
                    <Takeaways items={target.keyTakeaways}/>
                </Box>
            ))}
        </Paper>
    )
}

function Profile({lookup}) {
    return (
        <Paper data-testid="onboarding-profile" sx={{padding: "12px"}}>
            <Typography sx={{fontWeight: 600}}>{lookup.ticker}</Typography>
            <Field label="Name" value={lookup.name}/>
            <Field label="Exchange" value={lookup.exchange}/>
            <Field label="Currency" value={lookup.currency?.toUpperCase()}/>
            <Field label="Locale" value={lookup.locale?.toUpperCase()}/>
            <Field label="Industry" value={lookup.industry}/>
            <Field label="Market cap" value={lookup.marketCap === null || lookup.marketCap === undefined
                ? null
                : formatMillionsRounded(Number(lookup.marketCap) / 1000000)}/>
            <Field label="Shares" value={lookup.sharesOutstanding === null || lookup.sharesOutstanding === undefined
                ? null
                : formatMillionsRounded(Number(lookup.sharesOutstanding) / 1000000)}/>
            <Field label="Employees" value={lookup.employees === null || lookup.employees === undefined
                ? null
                : formatDecimals(Number(lookup.employees), 0, 0)}/>
            <Field label="Listed" value={lookup.listDate ? formatDate(lookup.listDate) : null}/>
            <Field label="Type" value={lookup.active === false ? `${lookup.type ?? ""} (inactive)` : lookup.type}/>
            <Field label="Website" value={lookup.website}/>
            {lookup.description &&
                <Box sx={{marginTop: "8px", fontSize: 14, color: "text.secondary"}}>
                    {lookup.description}
                </Box>
            }
        </Paper>
    )
}

function Financials({financials}) {
    const quarters = [...financials.quarters].reverse()

    return (
        <Paper data-testid="onboarding-financials" sx={{padding: "12px"}}>
            <Warnings warnings={financials.warnings}/>
            {quarters.length === 0
                ? <Typography sx={{fontSize: 14, color: "text.secondary"}}>No reported quarter was found.</Typography>
                : <TableContainer sx={{maxWidth: "100%", overflowX: "auto"}}>
                    <Table size="small">
                        <TableHead>
                            <TableRow>
                                <TableCell sx={{fontSize: 12}}/>
                                {quarters.map(quarter => (
                                    <TableCell
                                        key={quarter.id}
                                        data-testid="onboarding-financials-quarter"
                                        sx={{fontSize: 12, fontWeight: 600, whiteSpace: "nowrap", textAlign: "right"}}
                                    >
                                        {quarter.name || quarter.id}
                                    </TableCell>
                                ))}
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {FINANCIAL_ROWS.map(row => (
                                <TableRow key={row.label} data-testid="onboarding-financials-row">
                                    <TableCell sx={{fontSize: 12, color: "text.secondary", whiteSpace: "nowrap"}}>
                                        {row.label}
                                    </TableCell>
                                    {quarters.map(quarter => (
                                        <TableCell
                                            key={quarter.id}
                                            sx={{fontSize: 13, whiteSpace: "nowrap", textAlign: "right"}}
                                        >
                                            {row.value(quarter)}
                                        </TableCell>
                                    ))}
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </TableContainer>
            }
            {financials.notes?.length > 0 &&
                <Box data-testid="onboarding-financials-notes" sx={{marginTop: "8px"}}>
                    <Typography sx={{color: "text.secondary", fontSize: 12}}>Notes from Gemini</Typography>
                    <Takeaways items={financials.notes}/>
                </Box>
            }
        </Paper>
    )
}

const SENTIMENT_COLORS = {
    positive: "success.dark",
    neutral: "text.secondary",
    negative: "error.dark",
}

function QuarterList({title, quarters, format, testId}) {
    if (!quarters || quarters.length === 0) return null

    return (
        <Box data-testid={testId}>
            <Typography sx={{color: "text.secondary", fontSize: 12, textAlign: "center"}}>{title}</Typography>
            <Box sx={{display: "flex", gap: "14px", justifyContent: "center", marginTop: "2px"}}>
                {quarters.map(quarter => (
                    <Box key={`${quarter.label}-${quarter.date}`} sx={{textAlign: "center", minWidth: "42px"}}>
                        <Box sx={{fontFamily: "Roboto", fontSize: 15, fontWeight: 500}}>{format(quarter)}</Box>
                        <Box sx={{color: "text.secondary", fontSize: 11}}>{quarter.label}</Box>
                    </Box>
                ))}
            </Box>
        </Box>
    )
}

function QuarterValues({reported, estimated, format, reportedTitle, estimatedTitle, testId}) {
    if ((!reported || reported.length === 0) && (!estimated || estimated.length === 0)) return null

    return (
        <Box sx={{
            display: "flex",
            alignItems: "flex-start",
            justifyContent: "center",
            gap: "14px",
            flexWrap: "wrap",
            marginTop: "12px",
        }}>
            <QuarterList
                title={reportedTitle}
                quarters={reported}
                format={format}
                testId={`${testId}-reported`}
            />
            {reported?.length > 0 && estimated?.length > 0 &&
                <ArrowRightAltIcon sx={{color: "text.secondary", marginTop: "20px"}}/>
            }
            <QuarterList
                title={estimatedTitle}
                quarters={estimated}
                format={format}
                testId={`${testId}-estimated`}
            />
        </Box>
    )
}

function Projection({projection, format, testId}) {
    if (!projection) return null

    const windows = [
        {label: "ttm", window: projection.ttm},
        {label: "current", window: projection.current},
        {label: "next 1", window: projection.next1},
        {label: "next 2", window: projection.next2},
        {label: "next 3", window: projection.next3},
    ].filter(entry => entry.window)

    return (
        <Box data-testid={testId} sx={{
            display: "flex",
            gap: "16px",
            flexWrap: "wrap",
            justifyContent: "center",
        }}>
            {windows.map(entry => (
                <Box key={entry.label} sx={{textAlign: "center", minWidth: "52px"}}>
                    <Box sx={{color: "text.secondary", fontSize: 11}}>
                        {formatPercent(entry.window.change, true, 1) || "\u00a0"}
                    </Box>
                    <Box sx={{fontFamily: "Roboto", fontSize: 17, fontWeight: 600}}>
                        {format(entry.window.eps)}
                    </Box>
                    <Box sx={{color: "text.secondary", fontSize: 11}}>{entry.label}</Box>
                </Box>
            ))}
        </Box>
    )
}

function Estimates({estimates}) {
    const eps = quarter => decimals(quarter.eps)
    const revenue = quarter => quarter.revenue === null || quarter.revenue === undefined
        ? "-"
        : formatMillionsRounded(Number(quarter.revenue) / 1000000)

    return (
        <Paper data-testid="onboarding-estimates" sx={{padding: "12px"}}>
            <Warnings warnings={estimates.warnings}/>

            <Typography sx={{color: "text.secondary", fontSize: 12, fontWeight: 600}}>EPS</Typography>
            <Projection
                projection={estimates.projection}
                format={value => decimals(value)}
                testId="onboarding-estimates-projection"
            />
            <QuarterValues
                reported={estimates.reported}
                estimated={estimates.estimated}
                format={eps}
                reportedTitle="Reported EPS"
                estimatedTitle="Estimated EPS"
                testId="onboarding-estimates"
            />

            <Divider sx={{margin: "16px 0 12px 0"}}/>

            <Typography sx={{color: "text.secondary", fontSize: 12, fontWeight: 600}}>Revenue</Typography>
            <Projection
                projection={estimates.revenueProjection}
                format={value => value === null || value === undefined
                    ? "-"
                    : formatMillionsRounded(Number(value) / 1000000)}
                testId="onboarding-revenue-projection"
            />
            <QuarterValues
                reported={estimates.reported}
                estimated={estimates.estimated}
                format={revenue}
                reportedTitle="Reported revenue"
                estimatedTitle="Estimated revenue"
                testId="onboarding-revenue"
            />
        </Paper>
    )
}

function SentimentCounts({sentiment}) {
    if (!sentiment || sentiment.total === 0) return null

    const entries = [
        {label: "positive", value: sentiment.positive},
        {label: "neutral", value: sentiment.neutral},
        {label: "negative", value: sentiment.negative},
        {label: "unrated", value: sentiment.unrated},
    ].filter(entry => entry.value > 0)

    return (
        <Box data-testid="onboarding-news-sentiment" sx={{display: "flex", gap: "14px", alignItems: "baseline"}}>
            <Typography sx={{fontSize: 15, fontWeight: 600}}>{sentiment.total} articles</Typography>
            {entries.map(entry => (
                <Typography
                    key={entry.label}
                    sx={{fontSize: 14, color: SENTIMENT_COLORS[entry.label] || "text.secondary"}}
                >
                    {entry.value} {entry.label}
                </Typography>
            ))}
        </Box>
    )
}

function News({news}) {
    return (
        <Paper data-testid="onboarding-news" sx={{padding: "12px"}}>
            <Warnings warnings={news.warnings}/>
            <SentimentCounts sentiment={news.sentiment}/>
            {news.articles.map(article => (
                <Box key={article.url} data-testid="onboarding-news-article" sx={{marginTop: "10px"}}>
                    <Divider sx={{marginBottom: "8px"}}/>
                    <Box sx={{display: "flex", gap: "8px", alignItems: "baseline", flexWrap: "wrap"}}>
                        {article.sentiment &&
                            <Typography sx={{
                                fontSize: 12,
                                fontWeight: 600,
                                color: SENTIMENT_COLORS[article.sentiment] || "text.secondary",
                            }}>
                                {article.sentiment}
                            </Typography>
                        }
                        <Link href={article.url} target="_blank" rel="noreferrer" sx={{fontSize: 14}}>
                            {article.title}
                        </Link>
                    </Box>
                    <Typography sx={{color: "text.secondary", fontSize: 12}}>
                        {article.publisher}{article.publisher && article.published ? " | " : ""}
                        {article.published ? formatDate(article.published.substring(0, 10)) : ""}
                    </Typography>
                    {article.reasoning &&
                        <Typography sx={{fontSize: 13, marginTop: "2px"}}>{article.reasoning}</Typography>
                    }
                </Box>
            ))}
        </Paper>
    )
}

export const Onboarding = props => {
    const [ticker, setTicker] = useState("")
    const [loading, setLoading] = useState(false)
    const [alert, setAlert] = useState(null)
    const [lookup, setLookup] = useState(null)
    const [tab, setTab] = useState("overview")
    const compactTabs = useMediaQuery(`(max-width:${COMPACT_TABS_BREAKPOINT}px)`)
    const [pushing, setPushing] = useState(false)
    const [pushed, setPushed] = useState(false)
    const [confirming, setConfirming] = useState(false)

    const normalized = ticker.trim().toUpperCase()
    const invalid = normalized !== "" && !TICKER_PATTERN.test(normalized)
    // Firebase and this app are two separate places a company can be missing from, and being
    // in one says nothing about the other. The research tabs only need a company that exists
    // somewhere upstream; the two actions are gated on their own flag.
    const known = lookup && (lookup.inDatabase || lookup.inFirebase)
    const researchTicker = lookup && lookup.found && !lookup.inFirebase ? lookup.ticker : null
    // A company counts as real once Polygon found it or we already track it somewhere. Pushing to
    // Firebase only needs the ticker - the processor fills in the rest - so a company we already
    // hold in the database can be pushed even when Polygon has no profile for it.
    const realCompany = Boolean(lookup && (lookup.found || lookup.inDatabase || lookup.inFirebase))
    const missingFromFirebase = Boolean(realCompany && !lookup.inFirebase)
    const missingFromApp = Boolean(realCompany && !lookup.inDatabase)

    function companyCreated() {
        setLookup(previous => previous ? {...previous, inDatabase: true} : previous)
    }

    function createInApp() {
        if (!lookup || !missingFromApp) return

        props.setOpenEditCompany({
            ticker: lookup.ticker,
            currency: lookup.appCurrency ?? "",
            exchange: lookup.appExchange ?? "",
            name: lookup.name ?? "",
            description: lookup.description ?? "",
            website: lookup.website ?? "",
        })
    }

    const targets = useOnboardingSection("/onboarding/targets", researchTicker, true)
    const financials = useOnboardingSection("/onboarding/financials", researchTicker, true)
    const estimates = useOnboardingSection("/onboarding/estimates", researchTicker)
    const news = useOnboardingSection("/onboarding/news", researchTicker)

    // Analyst research and financials are the two Gemini retrievals. They are slow and cost a
    // request each, so they are confirmed together from the overview rather than starting by
    // themselves when the ticker is checked. Estimates and news are cheap and stay automatic.
    const geminiLoading = targets.loading || financials.loading

    // Either Gemini tab can start the pair, so confirming on one also fills the other.
    function runGemini() {
        if (geminiLoading) return
        targets.run()
        financials.run()
    }

    const tabs = [
        {
            name: "overview",
            title: "Overview",
            section: {data: lookup, loading: false, error: null},
            loadingText: "",
            render: data => <Profile lookup={data}/>,
        },
        {
            name: "targets",
            title: "Analyst research",
            section: targets,
            loadingText: "Asking Gemini for price targets of trusted institutions...",
            render: data => <AnalystResearch targets={data}/>,
            manual: true,
        },
        {
            name: "financials",
            title: "Financials",
            section: financials,
            loadingText: "Asking Gemini for the last four reported quarters...",
            render: data => <Financials financials={data}/>,
            manual: true,
        },
        {
            name: "estimates",
            title: "Estimates",
            section: estimates,
            loadingText: "Loading Finnhub earnings...",
            render: data => <Estimates estimates={data}/>,
        },
        {
            name: "news",
            title: "News",
            section: news,
            loadingText: "Loading Polygon news of the last month...",
            render: data => <News news={data}/>,
        },
    ]

    // The tabs are tracked by name, the swipe hook counts in indexes.
    const tabIndex = Math.max(tabs.findIndex(entry => entry.name === tab), 0)
    const swipe = useSwipeNavigation(
        tabIndex,
        index => setTab(tabs[index].name),
        tabs.length)

    function check() {
        if (loading || normalized === "" || invalid) return

        setLoading(true)
        setAlert(null)
        setLookup(null)
        setTab("overview")
        setPushed(false)
        axios.get(backend + "/onboarding/lookup", {params: {ticker: normalized}})
            .then(response => setLookup(response.data))
            .catch(error => setAlert(formatError(error)))
            .finally(() => setLoading(false))
    }

    function pushToFirebase() {
        if (pushing || pushed || !missingFromFirebase) return

        setConfirming(false)
        setPushing(true)
        setAlert(null)
        axios.post(backend + "/onboarding/firebase", null, {params: {ticker: lookup.ticker}})
            .then(() => setPushed(true))
            .catch(error => setAlert(formatError(error)))
            .finally(() => setPushing(false))
    }

    return (
        <Box sx={{paddingBottom: "24px"}}>
            <Section title="Company onboarding">
                <Paper sx={{padding: "12px"}}>
                    <Box sx={{
                        display: "flex",
                        flexDirection: {xs: "column", sm: "row"},
                        alignItems: {xs: "stretch", sm: "flex-start"},
                        justifyContent: "space-between",
                        gap: "12px",
                    }}>
                    <Box
                        component="form"
                        onSubmit={event => {event.preventDefault(); check()}}
                        sx={{display: "flex", gap: "8px", alignItems: "flex-start", flexShrink: 0}}
                    >
                        <TextField
                            id="trader-onboarding-ticker"
                            label="Ticker"
                            size="small"
                            value={ticker}
                            error={invalid}
                            helperText={invalid ? "Use a ticker like NVDA or BRK.B" : " "}
                            onChange={event => setTicker(event.target.value.toUpperCase())}
                            sx={{maxWidth: 200}}
                        />
                        <Button
                            type="submit"
                            variant="outlined"
                            disabled={loading || normalized === "" || invalid}
                            sx={{marginTop: "2px"}}
                        >
                            {loading ? <CircularProgress size={22}/> : "Check"}
                        </Button>
                    </Box>
                    {realCompany &&
                        <Box
                            data-testid="onboarding-state"
                            sx={{
                                display: "flex",
                                flexDirection: "column",
                                gap: "4px",
                                marginTop: {xs: 0, sm: "4px"},
                                alignItems: "flex-start",
                            }}
                        >
                            <DestinationRow
                                testId="onboarding-state-firebase"
                                destination="Firebase"
                                present={lookup.inFirebase || pushed}
                                action={
                                    <Button
                                        type="button"
                                        size="small"
                                        data-testid="onboarding-push"
                                        disabled={pushing}
                                        onClick={() => setConfirming(true)}
                                        sx={ROW_ACTION_SX}
                                    >
                                        {pushing ? <CircularProgress size={14}/> : "Push to Firebase"}
                                    </Button>
                                }
                            />
                            <DestinationRow
                                testId="onboarding-state-app"
                                destination="This app"
                                present={lookup.inDatabase}
                                action={
                                    <Button
                                        type="button"
                                        size="small"
                                        data-testid="onboarding-create"
                                        onClick={createInApp}
                                        sx={ROW_ACTION_SX}
                                    >
                                        Create in app
                                    </Button>
                                }
                            />
                        </Box>
                    }
                    </Box>
                </Paper>
            </Section>

            {alert &&
                <Section title=" ">
                    <Alert severity="error" variant="filled">
                        <AlertTitle>{alert.title}</AlertTitle>{alert.message}
                    </Alert>
                </Section>
            }

            {lookup && (known || lookup.found || !lookup.found || lookup.warnings?.length > 0) &&
                <Section title=" ">
                    <Warnings warnings={lookup.warnings}/>
                    {!known && !lookup.found &&
                        <Alert severity="error" data-testid="onboarding-not-found">
                            No company found for {lookup.ticker}. Check the ticker and try another one.
                        </Alert>
                    }
                </Section>
            }

            {researchTicker &&
                <Section title=" ">
                    {compactTabs
                        ? <Box sx={{display: "flex", justifyContent: "center", marginBottom: "8px"}}>
                            <MainBarTabs
                                labels={tabs.map(entry => entry.title)}
                                value={tabs.findIndex(entry => entry.name === tab)}
                                setValue={index => setTab(tabs[index].name)}
                            />
                        </Box>
                        : <Tabs
                            value={tab}
                            onChange={(event, value) => setTab(value)}
                            variant="scrollable"
                            scrollButtons="auto"
                            sx={{minHeight: "38px", marginBottom: "8px", "& .MuiTab-root": {minHeight: "38px"}}}
                        >
                            {tabs.map(entry => (
                                <Tab
                                    key={entry.name}
                                    value={entry.name}
                                    label={
                                        <Box sx={{display: "flex", alignItems: "center", gap: "6px"}}>
                                            {entry.title}
                                            {entry.section.loading && <CircularProgress size={12}/>}
                                        </Box>
                                    }
                                />
                            ))}
                        </Tabs>
                    }
                    <Box
                        sx={{...SWIPE_AREA_SX, touchAction: "pan-y"}}
                        {...swipe}
                    >
                    {tabs.filter(entry => entry.name === tab).map(entry => (
                        <Box key={entry.name} data-testid={`onboarding-tab-${entry.name}`}>
                            {entry.manual && !entry.section.requested &&
                                <Paper
                                    data-testid={`onboarding-${entry.name}-confirm`}
                                    sx={{
                                        padding: "12px",
                                        display: "flex",
                                        alignItems: "center",
                                        justifyContent: "space-between",
                                        gap: "12px",
                                        flexWrap: "wrap",
                                    }}
                                >
                                    <Typography sx={{fontSize: 13, color: "text.secondary"}}>
                                        Analyst research and financials are retrieved from Gemini and
                                        can take a few minutes. Confirm to start both.
                                    </Typography>
                                    <Button
                                        variant="outlined"
                                        data-testid="onboarding-run-gemini"
                                        onClick={runGemini}
                                    >
                                        Run Gemini retrievals
                                    </Button>
                                </Paper>
                            }
                            <SectionState
                                name={entry.name}
                                loading={entry.section.loading}
                                error={entry.section.error}
                                loadingText={entry.loadingText}
                            />
                            {entry.section.data && entry.render(entry.section.data)}
                        </Box>
                    ))}
                    </Box>
                </Section>
            }

            <EditCompanyDialog {...props} triggerRefresh={companyCreated}/>

            <Dialog open={confirming} onClose={() => setConfirming(false)}>
                <DialogTitle>Add {lookup?.ticker} to Firebase?</DialogTitle>
                <DialogContent>
                    <Typography sx={{fontSize: 14}}>
                        The processor starts the initialization of {lookup?.ticker} on its next run and
                        stores the retrieved data in Firebase, from where this app can import it.
                        The data is then kept up to date periodically.
                    </Typography>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setConfirming(false)}>Cancel</Button>
                    <Button onClick={pushToFirebase} data-testid="onboarding-push-confirm">Add</Button>
                </DialogActions>
            </Dialog>
        </Box>
    )
}
