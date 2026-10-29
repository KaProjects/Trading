import {
    Badge,
    Box,
    Button,
    Card,
    CardContent,
    Grid,
    IconButton,
} from "@mui/material";
import React, {useCallback, useEffect, useRef, useState} from "react";
import useMediaQuery from "@mui/material/useMediaQuery";
import {Loader} from "./component/Loader";
import {backend} from "../properties";
import axios from "axios";
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import KeyboardDoubleArrowDownIcon from "@mui/icons-material/KeyboardDoubleArrowDown";
import {formatDecimals, formatError} from "../service/FormattingService";
import CloudDownloadIcon from '@mui/icons-material/CloudDownload';
import {AssetsSummary} from "./component/AssetsSummary";
import {DateTime} from "./component/DateTime";
import {Record} from "./component/Record";
import {Period} from "./component/Period";
import {
    BUILT_IN_LIST_TITLES,
    COMPANY_SELECTOR_SIDEBAR_BREAKPOINT,
    CompanySelector,
} from "./component/CompanySelector";
import {EstimatesTabs} from "./component/EstimatesTabs";
import {MarketOverview} from "./component/MarketOverview";
import {PeriodFinancials} from "./component/PeriodFinancials";
import {PeriodEstimatesOverview, formatEstimateAmount, formatRevenueEstimateAmount} from "./component/PeriodEstimatesOverview";
import {SnackbarErrorAlert} from "./component/SnackbarErrorAlert";
import {AddPeriodDialog} from "../dialog/AddPeriodDialog";
import {AddPeriodFinancialDialog} from "../dialog/AddPeriodFinancialDialog";
import {FinancialsDialog} from "../dialog/FinancialsDialog";
import {EarningsProjectionsDialog} from "../dialog/EarningsProjectionsDialog";
import {RevenueProjectionsDialog} from "../dialog/RevenueProjectionsDialog";
import {AddRecordDialog} from "../dialog/AddRecordDialog";
import {ImportedTargetsAlerts} from "./component/ImportedTargetsAlerts";
import {ImportPeriodDialog} from "../dialog/ImportPeriodDialog";
import {AddEstimateDialog} from "../dialog/AddEstimateDialog";
import {TargetDialog} from "../dialog/TargetDialog";
import {NewsSentimentDialog} from "../dialog/NewsSentimentDialog";
import {RESEARCH_SPLIT_BREAKPOINT, RESEARCH_TAB, SWIPE_NAV_BREAKPOINT} from "./component/MainBar";
import {AddTagDialog} from "../dialog/AddTagDialog";
import {useLocation, useNavigate} from "react-router-dom";
import {useCloseOnNavigation} from "../service/NavigationService";
import {TodoList} from "./component/TodoList";
import {beginSwipe, swipeDirection} from "./component/useSwipeNavigation";
import {LatestNewsSentiment} from "./component/LatestNewsSentiment";
import EditNoteIcon from "@mui/icons-material/EditNote";
import {EditCompanyDialog} from "../dialog/EditCompanyDialog";
import {TradingViewOverview} from "./component/TradingViewOverview";

const ESTIMATE_TABS = [
    {key: "eps", title: "Earnings estimates"},
    {key: "revenue", title: "Revenue estimates"},
];

const badgeStyle = {"& .MuiBadge-badge": {fontSize: "0.6rem", height: "15px", minWidth: "15px", backgroundColor: "#ff7961", color: "white"}}
const researchCardStyle = {
    bgcolor: 'background.paper',
    boxShadow: 1,
    borderRadius: 2,
    minWidth: {xs: 0, sm: 700},
    width: {xs: "calc(100% + 16px)", sm: 800},
    marginLeft: {xs: "-8px", sm: 0},
    marginRight: {xs: "-8px", sm: 0},
    maxHeight: {
        xs: "calc(100dvh - var(--main-bar-height, 48px))",
        sm: "calc(100dvh - var(--main-bar-height, 48px) - 16px)",
    },
    height: {xs: "calc(100dvh - var(--main-bar-height, 48px))", sm: "auto"},
    marginBottom: {xs: "-8px", sm: 0},
    flexDirection: "column",
    overflow: "hidden",
}

const recordActionAnchorStyle = {
    position: "absolute",
    top: {xs: "6px", sm: 0},
    left: {xs: "50%", sm: "auto"},
    right: {xs: "auto", sm: 0},
    transform: {xs: "translateX(calc(-50% - 10px))", sm: "none"},
    zIndex: 2,
}

const periodActionAnchorStyle = {
    position: "absolute",
    top: {xs: "6px", sm: 0},
    left: {xs: "50%", sm: "auto"},
    right: {xs: "auto", sm: 0},
    transform: {xs: "translateX(calc(-50% + 25px))", sm: "none"},
    zIndex: 2,
}

const researchCardContentStyle = {
    display: "flex",
    flexDirection: "column",
    flex: "1 1 auto",
    minHeight: 0,
    paddingTop: {xs: "1px", sm: 2},
    paddingLeft: {xs: 0, sm: 2},
    paddingRight: {xs: 0, sm: 2},
    "&:last-child": {paddingBottom: {xs: 0, sm: 2}},
}

const researchCardRowsStyle = {
    flex: "1 1 auto",
    minHeight: 0,
    marginTop: "10px",
    paddingTop: "5px",
    overflowY: "auto",
    overscrollBehavior: "contain",
    "& > .mainContainer:first-of-type, & > *:first-of-type > .mainContainer:first-of-type": {marginTop: 0},
}

export const Research = props => {
    const location = useLocation()
    const navigate = useNavigate()
    const [refresh, setRefresh] = useState("")

    const [data, setData] = useState(null)
    const [loaded, setLoaded] = useState(false)
    const [error, setError] = useState(null)
    const [alert, setAlert] = useState(null)
    const [openAddRecordDialog, setOpenAddRecordDialog] = useState(false)
    const [openAddPeriodDialog, setOpenAddPeriodDialog] = useState(false)
    const [openImportPeriodDialog, setOpenImportPeriodDialog] = useState(false)
    const [openFinancialsDialog, setOpenFinancialsDialog] = useState(false)
    const [openEarningsProjectionsDialog, setOpenEarningsProjectionsDialog] = useState(false)
    const [openRevenueProjectionsDialog, setOpenRevenueProjectionsDialog] = useState(false)
    const [openAddFinancialDialog, setOpenAddFinancialDialog] = useState(null)
    const [openEditFinancialDialog, setOpenEditFinancialDialog] = useState(null)
    const [openAddEstimateDialog, setOpenAddEstimateDialog] = useState(null)
    const [openAddRevenueEstimateDialog, setOpenAddRevenueEstimateDialog] = useState(null)
    const [openTargetDialog, setOpenTargetDialog] = useState(null)
    const [openNewsSentimentDialog, setOpenNewsSentimentDialog] = useState(null)
    const [openAddTagDialog, setOpenAddTagDialog] = useState(false)
    const [tagSuggestions, setTagSuggestions] = useState([])
    const [unavailableTradingViewSymbol, setUnavailableTradingViewSymbol] = useState(null)
    const [targetCandidateCounts, setTargetCandidateCounts] = useState({})
    const [importedTargetGroups, setImportedTargetGroups] = useState([])
    const [failedTargetCandidatePeriods, setFailedTargetCandidatePeriods] = useState(new Set())
    // On a narrow screen the page is paged vertically: 0 is the company overview - the stack
    // of market, financials, estimate and analysis widgets - and 1..n are the periods. Each
    // gets the whole screen rather than splitting it, which is what the swipe is for.
    const [narrowPageIndex, setNarrowPageIndex] = useState(0)
    const [newsSentimentShown, setNewsSentimentShown] = useState(false)
    const [widgetsFolded, setWidgetsFolded] = useState(false)
    const [estimatesTab, setEstimatesTab] = useState("eps")
    const previousCompanyId = useRef(null)
    const latestRequestId = useRef(0)
    // One attempt per company: the import is repeated only when the page is opened again.
    const autoImportedCompanyId = useRef(null)

    useCloseOnNavigation(() => {
        setOpenAddRecordDialog(false)
        setOpenAddPeriodDialog(false)
        setOpenImportPeriodDialog(false)
        setOpenFinancialsDialog(false)
        setOpenEarningsProjectionsDialog(false)
        setOpenRevenueProjectionsDialog(false)
        setOpenAddFinancialDialog(null)
        setOpenEditFinancialDialog(null)
        setOpenAddEstimateDialog(null)
        setOpenAddRevenueEstimateDialog(null)
        setOpenTargetDialog(null)
        setOpenNewsSentimentDialog(null)
        setOpenAddTagDialog(false)
    })
    const researchTabsIndex = props.researchTabsIndex ?? RESEARCH_TAB.research
    const isNarrowScreen = useMediaQuery(`(max-width:${SWIPE_NAV_BREAKPOINT}px)`)
    const todoTabSelected = researchTabsIndex === RESEARCH_TAB.todo
    const touchStart = useRef(null)

    function handleTouchStart(event) {
        touchStart.current = beginSwipe(event)
    }

    function handleTouchEnd(event) {
        const start = touchStart.current
        touchStart.current = null
        if (!start || window.innerWidth > SWIPE_NAV_BREAKPOINT) return

        const direction = swipeDirection(start, event, "x")
        if (direction === null) return

        if (direction < 0) {
            props.setResearchTabsIndex?.(Math.min(researchTabsIndex + 1, RESEARCH_TAB.todo))
        } else {
            props.setResearchTabsIndex?.(Math.max(researchTabsIndex - 1, RESEARCH_TAB.research))
        }
    }

    const periodTouchStart = useRef(null)

    function handlePeriodTouchStart(event) {
        periodTouchStart.current = beginSwipe(event)
    }

    function handlePeriodTouchEnd(event, lastPage) {
        const start = periodTouchStart.current
        periodTouchStart.current = null
        if (!start || window.innerWidth > SWIPE_NAV_BREAKPOINT) return

        const direction = swipeDirection(start, event, "y")
        if (direction === null) return

        if (direction < 0) {
            setNarrowPageIndex(index => Math.min(index + 1, lastPage))
        } else {
            setNarrowPageIndex(index => Math.max(index - 1, 0))
        }
    }

    function fetchData(companyChanged) {
        const requestId = ++latestRequestId.current
        if (props.companySelectorValue) {
            if (companyChanged) {
                setLoaded(false)
                setError(null)
                setTargetCandidateCounts({})
                setFailedTargetCandidatePeriods(new Set())
                setNarrowPageIndex(0)
                setNewsSentimentShown(false)
                setImportedTargetGroups([])
                autoImportedCompanyId.current = null
            }

            axios.get(backend + "/research/" + props.companySelectorValue.id + (refresh ? "?refresh" + refresh : ""))
                .then((response) => {
                    if (requestId !== latestRequestId.current) return

                    setData(response.data)
                    setError(null)

                    setLoaded(true)
                    fetchTargetCandidateCounts(
                        props.companySelectorValue.id,
                        response.data.periods ?? [],
                        requestId,
                        response.data.importablePeriods?.length ?? 0)
                })
                .catch((error) => {
                    if (requestId !== latestRequestId.current) return

                    setError(formatError(error))
                    setLoaded(false)
                })
        } else {
            setLoaded(false)
            setTargetCandidateCounts({})
            setFailedTargetCandidatePeriods(new Set())
        }
    }

    function fetchTargetCandidateCounts(companyId, periods, requestId, importablePeriodsCount) {
        axios.get(`${backend}/target/company/${companyId}/sync/counts`)
            .then(response => {
                if (requestId !== latestRequestId.current) return
                if (response.data?.counts === undefined
                    && response.data?.failedPeriodIds === undefined
                    && response.data?.warnings === undefined) return

                const counts = response.data?.counts ?? {}
                setTargetCandidateCounts(counts)
                const failedPeriodIds = response.data?.failedPeriodIds
                    ?? ((response.data?.warnings?.length ?? 0) > 0
                        ? periods.map(period => period.id)
                        : [])
                setFailedTargetCandidatePeriods(new Set(failedPeriodIds.map(String)))

                if (failedPeriodIds.length === 0) {
                    const importableTargetsCount = Object.values(counts)
                        .reduce((sum, value) => sum + (Number(value) || 0), 0)
                    props.updateActionableCompany?.(
                        companyId,
                        importablePeriodsCount,
                        importableTargetsCount)

                    // On its own chain: an import that fails is not a counts failure, and must
                    // not be handled as one.
                    if (importableTargetsCount > 0) {
                        Promise.resolve().then(() => autoImportTargets(companyId, requestId))
                    }
                }
            })
            .catch(() => {
                if (requestId !== latestRequestId.current) return

                setTargetCandidateCounts({})
                setFailedTargetCandidatePeriods(new Set(periods.map(period => String(period.id))))
            })
    }

    const dismissImportedTargets = useCallback(periodId => {
        setImportedTargetGroups(groups => groups.filter(group => group.periodId !== periodId))
    }, [])

    /**
     * Targets that fall inside a period's window are the period's, whoever imports them, so the
     * page takes them in on its own rather than leaving the same clicks to be made in the targets
     * dialog for every period. Periods the targets cannot be mapped to are left alone, and the
     * backend imports nothing at all when anything is unreadable.
     */
    function autoImportTargets(companyId, requestId) {
        if (autoImportedCompanyId.current === companyId) return
        autoImportedCompanyId.current = companyId

        axios.post(`${backend}/target/company/${companyId}/sync/auto`)
            .then(response => {
                if (requestId !== latestRequestId.current) return

                const groups = response.data?.periods ?? []
                if (groups.length === 0) return

                setImportedTargetGroups(groups)
                triggerRefresh()
            })
            .catch(() => {
                // Nothing was taken in, and the targets dialog still offers them by hand.
            })
    }

    useEffect(() => {
        const companyId = props.companySelectorValue?.id ?? null
        const companyChanged = previousCompanyId.current !== companyId
        previousCompanyId.current = companyId
        fetchData(companyChanged)
        // eslint-disable-next-line
    }, [props.companySelectorValue?.id, refresh])

    function triggerRefresh() {
        setRefresh(new Date().getTime().toString())
        props.refreshCompanyLists?.()
    }

    function deleteRecord(recordId) {
        axios.delete(backend + "/record/" + recordId)
            .then(() => {
                setData(prev => ({...prev, records: prev.records.filter(record => record.id !== recordId)}))
            })
            .catch((error) => {
                setAlert(formatError(error))
            })
    }

    const selectedCompanyLoaded = props.companySelectorValue
        && loaded
        && data?.company?.id === props.companySelectorValue.id
    const companyFromUrl = new URLSearchParams(location.search ?? "").get("company")
    const selectedCompanyMatchesUrl = props.companySelectorValue?.ticker?.toLowerCase()
        === companyFromUrl?.toLowerCase()
    const waitingForUrlCompany = Boolean(companyFromUrl) && !selectedCompanyMatchesUrl
    const waitingForSelectedCompany = Boolean(props.companySelectorValue) && !selectedCompanyLoaded
    const loading = waitingForUrlCompany || waitingForSelectedCompany
    const tradingViewSymbol = data?.company?.ticker && data?.company?.exchange?.tradingViewCode
        ? `${data.company.exchange.tradingViewCode}:${data.company.ticker}`
        : null
    const hasTradingView = Boolean(
        tradingViewSymbol && tradingViewSymbol !== unavailableTradingViewSymbol
    )

    // Each estimate window gets the same bottom line the financials carry: what the market pays
    // for it. Earnings are per share so they divide the price; revenue is an absolute amount, so
    // it divides the market cap. A non-positive denominator has no meaningful multiple.
    const estimateMultiple = (numerator, value) => {
        const amount = Number(value);
        if (!numerator || !Number.isFinite(amount) || amount <= 0) return null;
        return numerator / amount;
    };
    // A company with both sets of estimates gets them in one widget with a tab each, rather than
    // two rows of the same five windows. Only on a wide screen: the narrow overview has the room,
    // and a tab is a poor target next to a swipe.
    const mergeEstimates = !isNarrowScreen
        && Boolean(data?.estimateOverview)
        && Boolean(data?.revenueEstimateOverview);
    const revenueEstimatesSelected = mergeEstimates && estimatesTab === "revenue";

    const earningsMultiple = value => estimateMultiple(Number(data?.latest?.price), value);
    const revenueMultiple = value => estimateMultiple(Number(data?.indicators?.marketCap), value);
    // The overview is a page of widgets; a company with none of them - no figures, no
    // estimates, no analysis - should not be swiped through an empty page to reach its periods.
    const hasMarketFigures = [data?.indicators?.marketCap, data?.indicators?.beta]
        .some(value => value !== null && value !== undefined && value !== ""
            && Number.isFinite(Number(value)))
    const overviewShown = hasMarketFigures
        || Boolean(data?.ttm)
        || Boolean(data?.estimateOverview)
        || Boolean(data?.revenueEstimateOverview)
        || newsSentimentShown
    // Folding the widgets away is offered by the first of them, so it is only possible while
    // that one is on the page - and a narrow screen, where they are a page of their own, keeps
    // them all.
    const foldable = !isNarrowScreen && hasMarketFigures
    const widgetsHidden = foldable && widgetsFolded

    const firstPeriodPage = overviewShown ? 1 : 0
    const lastNarrowPage = Math.max(0, (data?.periods?.length ?? 0) - 1 + firstPeriodPage)
    const narrowPeriodIndex = narrowPageIndex - firstPeriodPage

    const editNextToPrice = !hasTradingView && Boolean(data?.latest)

    return (
        <>
            {loading && <Loader error={waitingForUrlCompany ? null : error}/>}
            <Box
                data-testid="research-content"
                hidden={loading}
                onTouchStart={handleTouchStart}
                onTouchEnd={handleTouchEnd}
                sx={{
                    display: loading ? "none" : "block",
                    position: "relative",
                    minHeight: "1px",
                    flexGrow: 1,
                }}
            >
                <TodoList
                    {...props}
                    active={todoTabSelected}
                    onCompanySelected={() => props.setResearchTabsIndex?.(RESEARCH_TAB.research)}
                />
                <Box sx={{
                    [`@media (max-width:${COMPANY_SELECTOR_SIDEBAR_BREAKPOINT}px)`]: {
                        display: todoTabSelected ? "none" : "block",
                    },
                    [`@media (min-width:${COMPANY_SELECTOR_SIDEBAR_BREAKPOINT + 1}px)`]: {
                        paddingRight: "216px",
                    },
                }}>
                    <CompanySelector onCustomTagsChange={setTagSuggestions} {...props}/>
                </Box>
                {selectedCompanyLoaded && data.company.ticker !== undefined &&
                    <Grid container direction="row" sx={{width: "100%", justifyContent: "center", alignItems: "flex-start"}}>
                    <Card sx={{
                        ...researchCardStyle,
                        display: "flex",
                        [`@media (max-width:${COMPANY_SELECTOR_SIDEBAR_BREAKPOINT}px)`]: {
                            display: researchTabsIndex === RESEARCH_TAB.research ? "flex" : "none",
                        },
                        [`@media (min-width:${COMPANY_SELECTOR_SIDEBAR_BREAKPOINT + 1}px) and (max-width:${RESEARCH_SPLIT_BREAKPOINT}px)`]: {
                            display: researchTabsIndex === RESEARCH_TAB.records ? "none" : "flex",
                        },
                    }}>
                        <CardContent sx={researchCardContentStyle}>
                            <Box sx={{
                                position: "relative",
                                flexShrink: 0,
                                // On the overview page this column has to fill the card, otherwise
                                // the swipe hint has nothing to push itself to the bottom of. On a
                                // company without an overview the first page is a period, which
                                // needs the room itself.
                                ...(isNarrowScreen && overviewShown && narrowPageIndex === 0 && {
                                    flexShrink: 1,
                                    flexGrow: 1,
                                    minHeight: 0,
                                    display: "flex",
                                    flexDirection: "column",
                                }),
                            }}>
                                <Box sx={{
                                    display: "flex",
                                    alignItems: "flex-end",
                                    "& .edit-company-button": {
                                        opacity: {xs: 1, sm: 0},
                                        pointerEvents: {xs: "auto", sm: "none"},
                                        transition: "opacity 120ms ease-in-out",
                                    },
                                    "&:hover .edit-company-button, &:focus-within .edit-company-button": {
                                        opacity: 1,
                                        pointerEvents: "auto",
                                    },
                                }}>
                                    <Box sx={{
                                        display: "flex",
                                        alignItems: "flex-end",
                                        justifyContent: !hasTradingView ? "space-between" : "flex-start",
                                        boxSizing: "border-box",
                                        width: !hasTradingView ? {xs: "100%", sm: "550px"} : {xs: "100%", sm: "fit-content"},
                                        paddingRight: !hasTradingView ? {xs: "30px", sm: 0} : 0,
                                    }}>
                                        <Box sx={{
                                            color: 'text.primary',
                                            minHeight: "40px",
                                            display: "flex",
                                            alignItems: "center",
                                            width: hasTradingView ? {xs: "100%", sm: "fit-content"} : "fit-content",
                                            maxWidth: "100%",
                                        }}>
                                            {hasTradingView
                                                ? <TradingViewOverview
                                                    company={data.company}
                                                    onUnavailable={() => setUnavailableTradingViewSymbol(tradingViewSymbol)}
                                                    sx={{
                                                        width: {xs: "auto", sm: "550px"},
                                                        flex: {xs: "1 1 auto", sm: "0 0 550px"},
                                                        minWidth: 0,
                                                        marginTop: "1px",
                                                    }}
                                                />
                                                : <Box sx={{position: "relative", left: {xs: "5px", sm: 0}, fontSize: 34, fontWeight: 'medium'}}>
                                                    {data.company.ticker}
                                                </Box>
                                            }
                                            {!editNextToPrice &&
                                                <IconButton
                                                    className="edit-company-button"
                                                    aria-label={`Edit ${data.company.ticker}`}
                                                    title="Edit company"
                                                    size="small"
                                                    onClick={() => props.setOpenEditCompany(data.company)}
                                                    sx={{
                                                        position: {xs: "absolute", sm: "static"},
                                                        top: {xs: 0, sm: "auto"},
                                                        right: {xs: 0, sm: "auto"},
                                                        zIndex: {xs: 2, sm: "auto"},
                                                        marginLeft: {xs: 0, sm: "3px"},
                                                        width: "26px",
                                                        height: "26px",
                                                        backgroundColor: {xs: "background.paper", sm: "transparent"},
                                                    }}
                                                >
                                                    <EditNoteIcon sx={{width: 17}}/>
                                                </IconButton>
                                            }
                                        </Box>
                                        {!hasTradingView && data.latest &&
                                            <Box sx={{flexShrink: 0, textAlign: "right", marginBottom: "4px"}}>
                                                <Box sx={{position: "relative", left: {xs: "5px", sm: 0}, fontSize: {xs: 23, sm: 34}, fontWeight: 'medium'}}>
                                                    {data.company.currency}{formatDecimals(data.latest.price, 0, 2)}
                                                </Box>
                                            </Box>
                                        }
                                    </Box>
                                    {editNextToPrice &&
                                        <IconButton
                                            className="edit-company-button"
                                            aria-label={`Edit ${data.company.ticker}`}
                                            title="Edit company"
                                            size="small"
                                            onClick={() => props.setOpenEditCompany(data.company)}
                                            sx={{
                                                position: {xs: "absolute", sm: "static"},
                                                top: {xs: 0, sm: "auto"},
                                                right: {xs: 0, sm: "auto"},
                                                zIndex: {xs: 2, sm: "auto"},
                                                marginLeft: {xs: 0, sm: "3px"},
                                                width: "26px",
                                                height: "26px",
                                                backgroundColor: {xs: "background.paper", sm: "transparent"},
                                            }}
                                        >
                                            <EditNoteIcon sx={{width: 17}}/>
                                        </IconButton>
                                    }
                                </Box>
                                <Box sx={{
                                    display: "flex",
                                    alignItems: "flex-end",
                                    justifyContent: !hasTradingView ? "space-between" : "flex-start",
                                    width: !hasTradingView ? {sm: "550px"} : "auto",
                                    paddingRight: !hasTradingView ? {xs: "30px", sm: 0} : 0,
                                }}>
                                    {!hasTradingView && data.company.sector &&
                                        <Box sx={{position: "relative", left: {xs: "5px", sm: 0}, color: 'text.secondary', fontSize: 14, marginTop: "-4px"}}>
                                            {data.company.sector.name}
                                        </Box>
                                    }
                                    {!hasTradingView && data.latest &&
                                        <DateTime
                                            value={data.latest.datetime}
                                            sx={{display: "flex", position: "relative", left: {xs: "5px", sm: 0}, marginTop: '-2px', color: 'text.secondary', fontSize: {xs: 10, sm: 11}}}
                                            iconMarginTop={"1px"}
                                        />
                                    }
                                </Box>
                                <Box
                                    data-testid="company-tags"
                                    sx={{
                                        color: "text.secondary",
                                        // Read on a phone too, now that the research itself
                                        // has pages of its own: it sits under the chart and
                                        // over the figures, where it does on a desktop.
                                        display: "flex",
                                        alignItems: "center",
                                        marginTop: hasTradingView ? "-10px" : 0,
                                        marginLeft: hasTradingView ? "4px" : 0,
                                        flexWrap: {xs: "nowrap", sm: "wrap"},
                                        columnGap: "6px",
                                        fontSize: 14,
                                        minHeight: "20px",
                                        minWidth: "20px",
                                        width: {xs: "100%", sm: "fit-content"},
                                        maxWidth: "100%",
                                        overflowX: {xs: "auto", sm: "visible"},
                                        overflowY: {xs: "hidden", sm: "visible"},
                                        overscrollBehaviorX: "contain",
                                        "& .add-tag-button": {
                                            flexShrink: 0,
                                            // It leads the row rather than standing among the
                                            // tags, so it sits closer to the first of them.
                                            marginRight: "-3px",
                                        },
                                        "& .company-tag": {
                                            display: "inline-flex",
                                            alignItems: "center",
                                            flexShrink: 0,
                                        },
                                    }}
                                >
                                    <IconButton
                                        className="add-tag-button"
                                        aria-label="Add tag"
                                        size="small"
                                        onClick={() => setOpenAddTagDialog(true)}
                                        sx={{padding: 0}}
                                    >
                                        <ControlPointIcon sx={{color: "lightgreen", fontSize: 16}}/>
                                    </IconButton>
                                    {/* A tag is the list it puts the company in, so pressing it
                                        picks that list in the selector rather than going
                                        anywhere. */}
                                    {(data.company.tags ?? [])
                                        .filter(tag => !BUILT_IN_LIST_TITLES[tag])
                                        .map(tag => (
                                            <Box component="span" className="company-tag" key={tag}>
                                                <Box
                                                    component="span"
                                                    role="button"
                                                    aria-label={`Show the ${tag} list`}
                                                    onClick={() => props.setCompanyListSelectorValue?.(tag)}
                                                    sx={{
                                                        cursor: "pointer",
                                                        "&:hover": {textDecoration: "underline"},
                                                    }}
                                                >
                                                    #{tag}
                                                </Box>
                                            </Box>
                                        ))}
                                    {/* The cases this company is part of, after the tags and
                                        the way into them. A case is named rather than tagged -
                                        the name has spaces in it - so it reads as a badge,
                                        which also says it is not a tag to be taken off. */}
                                    {(data.cases ?? []).map(investmentCase => (
                                        <Box
                                            component="span"
                                            key={investmentCase.id}
                                            data-testid={`company-case-${investmentCase.id}`}
                                            role="button"
                                            aria-label={`Go to case ${investmentCase.name}`}
                                            onClick={() => navigate(`/cases/${investmentCase.id}`)}
                                            sx={{
                                                flexShrink: 0,
                                                cursor: "pointer",
                                                "&:hover": {textDecoration: "underline"},
                                            }}
                                        >
                                            {`@${investmentCase.name}`}
                                        </Box>
                                    ))}
                                </Box>
                                <AddTagDialog
                                    open={openAddTagDialog}
                                    handleClose={() => setOpenAddTagDialog(false)}
                                    triggerRefresh={triggerRefresh}
                                    companyId={data.company.id}
                                    suggestions={tagSuggestions}
                                    currentTags={data.company.tags}
                                />
                                {(!isNarrowScreen || (overviewShown && narrowPageIndex === 0)) &&
                                <Box
                                    data-testid="research-overview"
                                    onTouchStart={isNarrowScreen ? handlePeriodTouchStart : undefined}
                                    onTouchEnd={isNarrowScreen
                                        ? (event) => handlePeriodTouchEnd(event, lastNarrowPage)
                                        : undefined}
                                    sx={isNarrowScreen
                                        ? {flex: "1 1 auto", minHeight: 0, display: "flex", flexDirection: "column"}
                                        : undefined}
                                >
                                <Box sx={{
                                    // The tags sit above this on a phone now, so the figures
                                    // start under them rather than pulled up under the header.
                                    marginTop: hasTradingView ? {xs: "2px", sm: "4px"} : {xs: "6px", sm: "10px"},
                                    bgcolor: "background.paper",
                                    position: "relative",
                                }}>
                                    <MarketOverview
                                        marketCap={data.indicators?.marketCap}
                                        beta={data.indicators?.beta}
                                        collapsible={foldable}
                                        collapsed={widgetsFolded}
                                        onToggleCollapse={() => setWidgetsFolded(folded => !folded)}
                                    />
                                </Box>
                                {data.ttm && !widgetsHidden &&
                                    <Box sx={{
                                        marginTop: {xs: "2px", sm: "4px"},
                                        bgcolor: "background.paper",
                                        position: "relative",
                                    }}>
                                        <PeriodFinancials
                                            ttm={data.ttm}
                                            financials={data.financials}
                                            indicators={data.indicators?.ttm}
                                            expanded={isNarrowScreen}
                                            coreColumnsOnly={isNarrowScreen}
                                            onOpen={() => setOpenFinancialsDialog(true)}
                                        />
                                    </Box>
                                }
                                {!widgetsHidden && (mergeEstimates
                                    ? <PeriodEstimatesOverview
                                        sx={{marginTop: {xs: "2px", sm: "4px"}}}
                                        overview={revenueEstimatesSelected
                                            ? data.revenueEstimateOverview
                                            : data.estimateOverview}
                                        title={
                                            <EstimatesTabs
                                                tabs={ESTIMATE_TABS}
                                                active={estimatesTab}
                                                onChange={setEstimatesTab}
                                            />
                                        }
                                        format={revenueEstimatesSelected
                                            ? formatRevenueEstimateAmount
                                            : formatEstimateAmount}
                                        multiple={revenueEstimatesSelected
                                            ? revenueMultiple
                                            : earningsMultiple}
                                        expanded={isNarrowScreen}
                                        onOpen={() => revenueEstimatesSelected
                                            ? setOpenRevenueProjectionsDialog(true)
                                            : setOpenEarningsProjectionsDialog(true)}
                                    />
                                    : <>
                                        <PeriodEstimatesOverview
                                            sx={{marginTop: {xs: "2px", sm: "4px"}}}
                                            overview={data.estimateOverview}
                                            multiple={earningsMultiple}
                                            expanded={isNarrowScreen}
                                            onOpen={() => setOpenEarningsProjectionsDialog(true)}
                                        />
                                        <PeriodEstimatesOverview
                                            sx={{marginTop: {xs: "2px", sm: "4px"}}}
                                            overview={data.revenueEstimateOverview}
                                            title="Revenue estimates"
                                            format={formatRevenueEstimateAmount}
                                            multiple={revenueMultiple}
                                            expanded={isNarrowScreen}
                                            onOpen={() => setOpenRevenueProjectionsDialog(true)}
                                        />
                                    </>
                                )}
                                {!widgetsHidden &&
                                <LatestNewsSentiment
                                    companyId={data.company.id}
                                    onOpen={() => setOpenNewsSentimentDialog(data.periods?.[0])}
                                    expanded={isNarrowScreen}
                                    onShown={setNewsSentimentShown}
                                    sx={{marginTop: {xs: "2px", sm: "4px"}}}
                                />
                                }

                                {isNarrowScreen && data.periods?.length > 0 &&
                                    <Box
                                        data-testid="overview-swipe-hint"
                                        sx={{
                                            marginTop: "auto",
                                            paddingTop: "12px",
                                            display: "flex",
                                            flexDirection: "column",
                                            alignItems: "center",
                                            color: "text.disabled",
                                        }}
                                    >
                                        <Box sx={{fontSize: 11}}>
                                            {data.periods.length} {data.periods.length === 1 ? "period" : "periods"}
                                        </Box>
                                        <KeyboardDoubleArrowDownIcon sx={{fontSize: 20}}/>
                                    </Box>
                                }

                                </Box>
                                }

                                <Box sx={{
                                    ...periodActionAnchorStyle,
                                    display: "flex",
                                    alignItems: "center",
                                    gap: {xs: "8px", sm: 0},
                                }}>
                                    {data.importablePeriods?.length > 0 &&
                                        <>
                                            <Button sx={{minWidth: {xs: 0, sm: 64}, padding: {xs: "6px", sm: "6px 16px"}}} onClick={() => setOpenImportPeriodDialog(true)}>
                                                <Badge badgeContent={data.importablePeriods.length} sx={badgeStyle}>
                                                    <CloudDownloadIcon sx={{color: 'lightgreen'}}/>
                                                </Badge>
                                            </Button>
                                            <ImportPeriodDialog
                                                open={openImportPeriodDialog}
                                                handleClose={() => setOpenImportPeriodDialog(false)}
                                                company={props.companySelectorValue}
                                                periods={data.importablePeriods}
                                                existingPeriods={data.periods}
                                                triggerRefresh={triggerRefresh}
                                            />
                                        </>
                                    }
                                    <Button sx={{minWidth: {xs: 0, sm: 64}, padding: {xs: "6px", sm: "6px 16px"}}} onClick={() => setOpenAddPeriodDialog(true)}>
                                        <ControlPointIcon sx={{color: 'lightgreen',}}/>
                                    </Button>
                                    <AddPeriodDialog
                                        open={openAddPeriodDialog}
                                        handleClose={() => setOpenAddPeriodDialog(false)}
                                        triggerRefresh={triggerRefresh}
                                        companyId={props.companySelectorValue.id}
                                        periods={data.periods}
                                    />
                                </Box>
                            </Box>

                            <AddPeriodFinancialDialog
                                open={openAddFinancialDialog !== null}
                                period={openAddFinancialDialog}
                                triggerRefresh={triggerRefresh}
                                handleClose={() => setOpenAddFinancialDialog(null)}
                                company={props.companySelectorValue}
                            />
                            <AddPeriodFinancialDialog
                                open={openEditFinancialDialog !== null}
                                period={openEditFinancialDialog}
                                triggerRefresh={triggerRefresh}
                                handleClose={() => setOpenEditFinancialDialog(null)}
                                company={props.companySelectorValue}
                                edit
                            />
                            <FinancialsDialog
                                open={openFinancialsDialog}
                                handleClose={() => setOpenFinancialsDialog(false)}
                                ticker={data.company.ticker}
                                financials={data.financials}
                            />
                            <EarningsProjectionsDialog
                                open={openEarningsProjectionsDialog}
                                handleClose={() => setOpenEarningsProjectionsDialog(false)}
                                triggerRefresh={triggerRefresh}
                                ticker={data.company.ticker}
                                currentPrice={data.latest?.price}
                                latestPeriod={data.periods[0]}
                                previousPeriod={data.periods[1]}
                            />
                            <RevenueProjectionsDialog
                                open={openRevenueProjectionsDialog}
                                handleClose={() => setOpenRevenueProjectionsDialog(false)}
                                triggerRefresh={triggerRefresh}
                                ticker={data.company.ticker}
                                currentPrice={data.latest?.price}
                                ttm={data.ttm}
                                latestPeriod={data.periods[0]}
                                previousPeriod={data.periods[1]}
                            />
                            <AddEstimateDialog
                                open={openAddEstimateDialog !== null}
                                handleClose={() => setOpenAddEstimateDialog(null)}
                                triggerRefresh={triggerRefresh}
                                company={props.companySelectorValue}
                                period={openAddEstimateDialog}
                            />
                            <AddEstimateDialog
                                kind="revenue"
                                open={openAddRevenueEstimateDialog !== null}
                                handleClose={() => setOpenAddRevenueEstimateDialog(null)}
                                triggerRefresh={triggerRefresh}
                                company={props.companySelectorValue}
                                period={openAddRevenueEstimateDialog}
                            />
                            <TargetDialog
                                open={openTargetDialog !== null}
                                handleClose={() => setOpenTargetDialog(null)}
                                triggerRefresh={triggerRefresh}
                                company={props.companySelectorValue}
                                period={openTargetDialog}
                            />
                            <NewsSentimentDialog
                                open={openNewsSentimentDialog !== null}
                                handleClose={() => setOpenNewsSentimentDialog(null)}
                                company={props.companySelectorValue}
                                period={openNewsSentimentDialog}
                            />
                            <EditCompanyDialog triggerRefresh={triggerRefresh} {...props}/>

                            {(!isNarrowScreen || narrowPeriodIndex >= 0) &&
                            <Box
                                data-testid="period-list"
                                onTouchStart={isNarrowScreen ? handlePeriodTouchStart : undefined}
                                onTouchEnd={isNarrowScreen ? (event) => handlePeriodTouchEnd(event, lastNarrowPage) : undefined}
                                sx={{
                                    ...researchCardRowsStyle,
                                    // The negative margin tucks the periods under the overview's
                                    // last widget; with no overview there is nothing to tuck
                                    // under and the card would ride up over the company header.
                                    marginTop: isNarrowScreen && !overviewShown
                                        ? "6px"
                                        : {xs: "-7px", sm: "10px"},
                                    paddingTop: {xs: "14px", sm: "5px"},
                                    ...(isNarrowScreen && {display: "flex", flexDirection: "column"}),
                                }}
                            >
                                {isNarrowScreen
                                    ? data.periods.slice(narrowPeriodIndex, narrowPeriodIndex + 1).map((period) => (
                                        <Box key={period.id} sx={{flex: "1 1 auto", minHeight: 0, display: "flex", flexDirection: "column"}}>
                                            <Period
                                                period={period}
                                                ticker={data.company.ticker}
                                                previousPeriod={data.periods[narrowPeriodIndex + 1]}
                                                isLatest={narrowPeriodIndex === 0}
                                                periodPosition={narrowPeriodIndex + 1}
                                                periodCount={data.periods.length}
                                                currency={data.company.currency}
                                                setAlert={setAlert}
                                                openDialog={() => setOpenAddFinancialDialog(period)}
                                                openEditDialog={() => setOpenEditFinancialDialog(period)}
                                                openEstimateDialog={() => setOpenAddEstimateDialog(period)}
                                                openRevenueEstimateDialog={() => setOpenAddRevenueEstimateDialog(period)}
                                                openTargetDialog={() => setOpenTargetDialog(period)}
                                                openNewsSentimentDialog={() => setOpenNewsSentimentDialog(period)}
                                                targetCandidateCount={targetCandidateCounts[period.id] ?? 0}
                                                targetCandidateFailed={failedTargetCandidatePeriods.has(String(period.id))}
                                                stretch
                                            />
                                        </Box>
                                    ))
                                    : data.periods.map((period, index) => (
                                        <Period
                                            key={period.id}
                                            period={period}
                                            ticker={data.company.ticker}
                                            previousPeriod={data.periods[index + 1]}
                                            isLatest={index === 0}
                                            currency={data.company.currency}
                                            setAlert={setAlert}
                                            openDialog={() => setOpenAddFinancialDialog(period)}
                                            openEditDialog={() => setOpenEditFinancialDialog(period)}
                                            openEstimateDialog={() => setOpenAddEstimateDialog(period)}
                                            openRevenueEstimateDialog={() => setOpenAddRevenueEstimateDialog(period)}
                                            openTargetDialog={() => setOpenTargetDialog(period)}
                                            openNewsSentimentDialog={() => setOpenNewsSentimentDialog(period)}
                                            targetCandidateCount={targetCandidateCounts[period.id] ?? 0}
                                            targetCandidateFailed={failedTargetCandidatePeriods.has(String(period.id))}
                                        />
                                    ))}
                            </Box>
                            }
                        </CardContent>
                    </Card>
                    <Card sx={{
                        ...researchCardStyle,
                        display: "flex",
                        [`@media (max-width:${COMPANY_SELECTOR_SIDEBAR_BREAKPOINT}px)`]: {
                            display: researchTabsIndex === RESEARCH_TAB.records ? "flex" : "none",
                        },
                        [`@media (min-width:${COMPANY_SELECTOR_SIDEBAR_BREAKPOINT + 1}px) and (max-width:${RESEARCH_SPLIT_BREAKPOINT}px)`]: {
                            display: researchTabsIndex === RESEARCH_TAB.records ? "flex" : "none",
                        },
                    }}>
                        <CardContent sx={researchCardContentStyle}>
                            <Box sx={{position: "relative", flexShrink: 0, paddingLeft: {xs: "5px", sm: 0}}}>
                                <Button aria-label="Add record" sx={recordActionAnchorStyle} onClick={() => setOpenAddRecordDialog(true)}>
                                    <ControlPointIcon sx={{color: 'lightgreen',}}/>
                                </Button>
                                <AddRecordDialog
                                    open={openAddRecordDialog}
                                    handleClose={() => setOpenAddRecordDialog(false)}
                                    triggerRefresh={triggerRefresh}
                                    companyId={props.companySelectorValue.id}
                                    indicators={data.indicators}
                                    assets={data.assets}
                                    latestPeriod={data.periods[0]}
                                    targetStats={data.periods[0]?.targetStats}
                                />
                            </Box>

                            <AssetsSummary
                                latest={data.latest}
                                assets={data.assets}
                                currency={data.company.currency}
                                sx={{
                                    marginTop: "6px",
                                    marginRight: {xs: 0, sm: "10px"},
                                    marginLeft: {xs: 0, sm: "5px"},
                                    width: "100%",
                                    maxWidth: {xs: "100%", sm: "520px"},
                                }}
                            />

                            <Box
                                data-testid="record-list"
                                sx={{...researchCardRowsStyle, marginTop: {xs: "3px", sm: "10px"}}}
                            >
                                {data.records.map((record) => (
                                    <Record
                                        key={record.id}
                                        data={record}
                                        ticker={data.company.ticker}
                                        currency={data.company.currency}
                                        setAlert={setAlert}
                                        deleteRecord={deleteRecord}
                                    />
                                ))}
                            </Box>
                        </CardContent>
                    </Card>
                    </Grid>
                }
                <SnackbarErrorAlert error={alert} open={alert !== null} onClose={() => setAlert(null)}/>
                <ImportedTargetsAlerts
                    groups={importedTargetGroups}
                    currency={data?.company?.currency}
                    onDismiss={dismissImportedTargets}
                />
            </Box>
        </>
    )
}
