import {
    Badge,
    Box,
    Button,
    Card,
    CardContent,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Grid,
    IconButton,
    Stack,
} from "@mui/material";
import React, {useEffect, useRef, useState} from "react";
import useMediaQuery from "@mui/material/useMediaQuery";
import {Loader} from "./component/Loader";
import {backend} from "../properties";
import axios from "axios";
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import {ReactComponent as DeleteIcon} from "../assets/icons/delete.svg";
import {formatDecimals, formatError, formatMillions, formatPercent} from "../service/FormattingService";
import CloudDownloadIcon from '@mui/icons-material/CloudDownload';
import {AssetBox} from "./component/AssetBox";
import {DateTime} from "./component/DateTime";
import {Record} from "./component/Record";
import {Period} from "./component/Period";
import {
    BUILT_IN_LIST_TITLES,
    COMPANY_SELECTOR_SIDEBAR_BREAKPOINT,
    CompanySelector,
} from "./component/CompanySelector";
import {PeriodFinancials} from "./component/PeriodFinancials";
import {PeriodEstimatesOverview, formatRevenueEstimateAmount} from "./component/PeriodEstimatesOverview";
import {SnackbarErrorAlert} from "./component/SnackbarErrorAlert";
import {AddPeriodDialog} from "../dialog/AddPeriodDialog";
import {AddPeriodFinancialDialog} from "../dialog/AddPeriodFinancialDialog";
import {FinancialsDialog} from "../dialog/FinancialsDialog";
import {EarningsProjectionsDialog} from "../dialog/EarningsProjectionsDialog";
import {RevenueProjectionsDialog} from "../dialog/RevenueProjectionsDialog";
import {AddRecordDialog} from "../dialog/AddRecordDialog";
import {ImportPeriodDialog} from "../dialog/ImportPeriodDialog";
import {AddEstimateDialog} from "../dialog/AddEstimateDialog";
import {TargetDialog} from "../dialog/TargetDialog";
import {NewsSentimentDialog} from "../dialog/NewsSentimentDialog";
import {RESEARCH_SPLIT_BREAKPOINT, RESEARCH_TAB, SWIPE_NAV_BREAKPOINT} from "./component/MainBar";
import {AddTagDialog} from "../dialog/AddTagDialog";
import {useLocation} from "react-router-dom";
import {useCloseOnNavigation} from "../service/NavigationService";
import {TodoList} from "./component/TodoList";
import {isInsideOverlay} from "./component/useSwipeNavigation";
import {LatestNewsSentiment} from "./component/LatestNewsSentiment";
import EditNoteIcon from "@mui/icons-material/EditNote";
import {EditCompanyDialog} from "../dialog/EditCompanyDialog";
import {TradingViewOverview} from "./component/TradingViewOverview";

const badgeStyle = {"& .MuiBadge-badge": {fontSize: "0.6rem", height: "15px", minWidth: "15px", backgroundColor: "#ff7961", color: "white"}}
const researchCardStyle = {
    bgcolor: 'background.paper',
    boxShadow: 1,
    borderRadius: 2,
    minWidth: {xs: 0, sm: 700},
    width: {xs: "calc(100% + 10px)", sm: 800},
    marginLeft: {xs: "-5px", sm: 0},
    marginRight: {xs: "-5px", sm: 0},
    maxHeight: {
        xs: "calc(100dvh - var(--main-bar-height, 48px) - 8px)",
        sm: "calc(100dvh - var(--main-bar-height, 48px) - 16px)",
    },
    height: {xs: "calc(100dvh - var(--main-bar-height, 48px) - 8px)", sm: "auto"},
    flexDirection: "column",
    overflow: "hidden",
}

const recordActionAnchorStyle = {
    position: "absolute",
    top: 0,
    right: 0,
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
    "&:last-child": {paddingBottom: 2},
}

const researchCardRowsStyle = {
    flex: "1 1 auto",
    minHeight: 0,
    marginTop: "10px",
    paddingTop: "5px",
    overflowY: "auto",
    overscrollBehavior: "contain",
    "& > .mainContainer:first-of-type": {marginTop: 0},
}

export const Research = props => {
    const location = useLocation()
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
    const [tagToDelete, setTagToDelete] = useState(null)
    const [tagSuggestions, setTagSuggestions] = useState([])
    const [unavailableTradingViewSymbol, setUnavailableTradingViewSymbol] = useState(null)
    const [targetCandidateCounts, setTargetCandidateCounts] = useState({})
    const [failedTargetCandidatePeriods, setFailedTargetCandidatePeriods] = useState(new Set())
    const [narrowPeriodIndex, setNarrowPeriodIndex] = useState(0)
    const previousCompanyId = useRef(null)
    const latestRequestId = useRef(0)

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
        setTagToDelete(null)
    })
    const researchTabsIndex = props.researchTabsIndex ?? RESEARCH_TAB.research
    const isNarrowScreen = useMediaQuery(`(max-width:${SWIPE_NAV_BREAKPOINT}px)`)
    const todoTabSelected = researchTabsIndex === RESEARCH_TAB.todo
    const touchStart = useRef(null)

    function handleTouchStart(event) {
        if (isInsideOverlay(event.target)) {
            touchStart.current = null
            return
        }

        const touch = event.touches[0]
        touchStart.current = {x: touch.clientX, y: touch.clientY}
    }

    function handleTouchEnd(event) {
        const start = touchStart.current
        touchStart.current = null
        if (!start || window.innerWidth > SWIPE_NAV_BREAKPOINT) return

        const touch = event.changedTouches[0]
        const deltaX = touch.clientX - start.x
        const deltaY = touch.clientY - start.y
        const SWIPE_THRESHOLD = 60
        if (Math.abs(deltaX) < SWIPE_THRESHOLD || Math.abs(deltaX) < Math.abs(deltaY)) return

        if (deltaX < 0) {
            props.setResearchTabsIndex?.(Math.min(researchTabsIndex + 1, RESEARCH_TAB.todo))
        } else {
            props.setResearchTabsIndex?.(Math.max(researchTabsIndex - 1, RESEARCH_TAB.research))
        }
    }

    const periodTouchStart = useRef(null)

    function handlePeriodTouchStart(event) {
        if (isInsideOverlay(event.target)) {
            periodTouchStart.current = null
            return
        }

        const touch = event.touches[0]
        periodTouchStart.current = {x: touch.clientX, y: touch.clientY}
    }

    function handlePeriodTouchEnd(event, periodCount) {
        const start = periodTouchStart.current
        periodTouchStart.current = null
        if (!start || window.innerWidth > SWIPE_NAV_BREAKPOINT) return

        const touch = event.changedTouches[0]
        const deltaX = touch.clientX - start.x
        const deltaY = touch.clientY - start.y
        const SWIPE_THRESHOLD = 60
        if (Math.abs(deltaY) < SWIPE_THRESHOLD || Math.abs(deltaY) < Math.abs(deltaX)) return

        const scrollBox = event.currentTarget.querySelector("[data-period-scroll]")
        if (scrollBox) {
            const atBottom = scrollBox.scrollTop + scrollBox.clientHeight >= scrollBox.scrollHeight - 1
            const atTop = scrollBox.scrollTop <= 1
            if (deltaY < 0 && !atBottom) return
            if (deltaY > 0 && !atTop) return
        }

        if (deltaY < 0) {
            setNarrowPeriodIndex(index => Math.min(index + 1, periodCount - 1))
        } else {
            setNarrowPeriodIndex(index => Math.max(index - 1, 0))
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
                setNarrowPeriodIndex(0)
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
                }
            })
            .catch(() => {
                if (requestId !== latestRequestId.current) return

                setTargetCandidateCounts({})
                setFailedTargetCandidatePeriods(new Set(periods.map(period => String(period.id))))
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

    function deleteTag() {
        axios.delete(backend + "/company/" + data.company.id + "/tag", {
            params: {value: tagToDelete},
        }).then(() => {
            setTagToDelete(null)
            triggerRefresh()
        }).catch((error) => {
            setTagToDelete(null)
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
    const editNextToPrice = !hasTradingView && Boolean(data?.latest)

    return (
        <>
            {loading && <Loader error={waitingForUrlCompany ? null : error}/>}
            <Box
                data-testid="research-content"
                hidden={loading}
                onTouchStart={handleTouchStart}
                onTouchEnd={handleTouchEnd}
                sx={{display: loading ? "none" : "block", position: "relative", minHeight: "1px"}}
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
                            <Box sx={{position: "relative", flexShrink: 0}}>
                                <Box sx={{
                                    color: 'text.secondary',
                                    marginLeft: hasTradingView ? "2px" : 0,
                                    display: {xs: "none", sm: "block"},
                                }}>
                                    Research
                                </Box>
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
                                        width: !hasTradingView ? {xs: "100%", sm: "520px"} : {xs: "100%", sm: "fit-content"},
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
                                                        width: {xs: "auto", sm: "520px"},
                                                        flex: {xs: "1 1 auto", sm: "0 0 520px"},
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
                                    width: !hasTradingView ? {sm: "520px"} : "auto",
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
                                    sx={{
                                        color: "text.secondary",
                                        display: {xs: "none", sm: "flex"},
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
                                            opacity: {xs: 1, sm: 0},
                                            pointerEvents: {xs: "auto", sm: "none"},
                                            flexShrink: 0,
                                            transition: "opacity 120ms ease-in-out",
                                        },
                                        "&:hover .add-tag-button": {
                                            opacity: 1,
                                            pointerEvents: "auto",
                                        },
                                        "& .company-tag": {
                                            display: "inline-flex",
                                            alignItems: "center",
                                            flexShrink: 0,
                                        },
                                        "& .delete-tag-button": {
                                            opacity: {xs: 1, sm: 0},
                                            pointerEvents: {xs: "auto", sm: "none"},
                                            width: {xs: "24px", sm: 0},
                                            height: "24px",
                                            marginRight: {xs: "-6px", sm: 0},
                                            overflow: "hidden",
                                            padding: {xs: "2px", sm: 0},
                                            minWidth: 0,
                                            lineHeight: 0,
                                            transition: "opacity 120ms ease-in-out, width 120ms ease-in-out, margin 120ms ease-in-out, padding 120ms ease-in-out",
                                        },
                                        "& .company-tag:hover .delete-tag-button": {
                                            opacity: 1,
                                            pointerEvents: "auto",
                                            width: "24px",
                                            marginRight: "-6px",
                                            padding: "2px",
                                        },
                                        "& .delete-tag-button svg": {
                                            width: "17px",
                                            height: "17px",
                                            display: "block",
                                        },
                                    }}
                                >
                                    {(data.company.tags ?? [])
                                        .filter(tag => !BUILT_IN_LIST_TITLES[tag])
                                        .map(tag => (
                                            <Box component="span" className="company-tag" key={tag}>
                                                <Button
                                                    className="delete-tag-button"
                                                    aria-label={`Remove tag ${tag}`}
                                                    onClick={() => setTagToDelete(tag)}
                                                >
                                                    <DeleteIcon/>
                                                </Button>
                                                #{tag}
                                            </Box>
                                        ))}
                                    <IconButton
                                        className="add-tag-button"
                                        aria-label="Add tag"
                                        size="small"
                                        onClick={() => setOpenAddTagDialog(true)}
                                        sx={{padding: 0}}
                                    >
                                        <ControlPointIcon sx={{color: "lightgreen", fontSize: 16}}/>
                                    </IconButton>
                                </Box>
                                <AddTagDialog
                                    open={openAddTagDialog}
                                    handleClose={() => setOpenAddTagDialog(false)}
                                    triggerRefresh={triggerRefresh}
                                    companyId={data.company.id}
                                    suggestions={tagSuggestions}
                                    currentTags={data.company.tags}
                                />
                                <Dialog open={tagToDelete !== null} onClose={() => setTagToDelete(null)}>
                                    <DialogTitle>Remove tag?</DialogTitle>
                                    <DialogContent>
                                        Do you want to remove tag #{tagToDelete} from {data.company.ticker}?
                                    </DialogContent>
                                    <DialogActions>
                                        <Button onClick={() => setTagToDelete(null)}>Cancel</Button>
                                        <Button color="error" onClick={deleteTag} autoFocus>Remove</Button>
                                    </DialogActions>
                                </Dialog>

                                {data.ttm &&
                                    <Box sx={{
                                        marginTop: hasTradingView ? {xs: "-7px", sm: "8px"} : {xs: "13px", sm: "20px"},
                                        bgcolor: "background.paper",
                                        position: "relative",
                                    }}>
                                        <PeriodFinancials
                                            ttm={data.ttm}
                                            financials={data.financials}
                                            onOpen={() => setOpenFinancialsDialog(true)}
                                        />
                                    </Box>
                                }
                                <PeriodEstimatesOverview
                                    sx={{marginTop: {xs: "2px", sm: "8px"}}}
                                    overview={data.estimateOverview}
                                    onOpen={() => setOpenEarningsProjectionsDialog(true)}
                                />
                                <PeriodEstimatesOverview
                                    sx={{marginTop: {xs: "2px", sm: "8px"}}}
                                    overview={data.revenueEstimateOverview}
                                    title="Revenue estimates"
                                    format={formatRevenueEstimateAmount}
                                    onOpen={() => setOpenRevenueProjectionsDialog(true)}
                                />
                                <LatestNewsSentiment
                                    companyId={data.company.id}
                                    sx={{marginTop: {xs: "2px", sm: "8px"}}}
                                />

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

                            <Box
                                data-testid="period-list"
                                onTouchStart={isNarrowScreen ? handlePeriodTouchStart : undefined}
                                onTouchEnd={isNarrowScreen ? (event) => handlePeriodTouchEnd(event, data.periods.length) : undefined}
                                sx={{
                                    ...researchCardRowsStyle,
                                    marginTop: {xs: "-7px", sm: "10px"},
                                    paddingTop: {xs: "14px", sm: "5px"},
                                    ...(isNarrowScreen && {display: "flex", flexDirection: "column"}),
                                }}
                            >
                                {isNarrowScreen
                                    ? data.periods.slice(narrowPeriodIndex, narrowPeriodIndex + 1).map((period) => (
                                        <Box key={period.id} sx={{flex: "1 1 auto", minHeight: 0, display: "flex", flexDirection: "column"}}>
                                            <Period
                                                period={period}
                                                previousPeriod={data.periods[narrowPeriodIndex + 1]}
                                                isLatest={narrowPeriodIndex === 0}
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
                                <Box sx={{color: 'text.secondary', display: {xs: "none", sm: "block"}}}>Records</Box>

                                {data.latest &&
                                <Box sx={{
                                    [`@media (min-width:${RESEARCH_SPLIT_BREAKPOINT + 1}px)`]: {display: "none"},
                                }}>
                                    <Box sx={{color: 'text.primary', fontSize: 34, fontWeight: 'medium'}}>
                                        {data.company.currency}{formatDecimals(data.latest.price,0,2)}
                                    </Box>
                                    <DateTime value={data.latest.datetime} sx={{marginTop: '-2px', color: 'text.secondary', fontSize: 11}} iconMarginTop={"1px"}/>
                                </Box>
                                }

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
                                    targetStats={data.periods[0]?.targetStats}
                                />
                            </Box>

                            {data.indicators &&
                                <Box sx={{paddingLeft: {xs: "5px", sm: 0}}}>
                                    <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>Market Cap: {data.company.currency}{formatMillions(data.indicators.marketCap)}</Box>
                                    <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>Dividend Yield: {formatPercent(data.indicators.ttm.dividendYield)}</Box>

                                    <Stack direction={"row"} spacing={2}>
                                        <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>PS: {formatDecimals(data.indicators.ttm.marketCapToRevenues, 0, 2)}</Box>
                                        <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>PG: {formatDecimals(data.indicators.ttm.marketCapToGrossProfit, 0, 2)}</Box>
                                        <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>PO: {formatDecimals(data.indicators.ttm.marketCapToOperatingIncome, 0, 2)}</Box>
                                        <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>PE: {formatDecimals(data.indicators.ttm.marketCapToNetIncome, 0, 2)}</Box>
                                        <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>PCF: {formatDecimals(data.indicators.ttm.marketCapToFreeCashFlow, 0, 2)}</Box>
                                    </Stack>
                                </Box>
                            }

                            {data.assets.assets.length > 0 &&
                                <Stack
                                    data-testid="record-assets"
                                    direction="row"
                                    spacing={1}
                                    sx={{
                                        marginTop: "10px",
                                        marginRight: "10px",
                                        marginBottom: 0,
                                        marginLeft: {xs: "5px", sm: "10px"},
                                        maxWidth: "100%",
                                        flexShrink: 0,
                                        overflowX: {xs: "auto", sm: "visible"},
                                        overflowY: {xs: "hidden", sm: "visible"},
                                        pb: {xs: 1, sm: 0},
                                        "& > *": {flexShrink: 0},
                                    }}
                                >
                                    {data.assets.assets.map((asset, index) => (
                                        <AssetBox key={`${data.company.id}-${index}`} asset={asset} currency={data.company.currency}/>
                                    ))}
                                </Stack>
                            }

                            <Box
                                data-testid="record-list"
                                sx={{...researchCardRowsStyle, marginTop: {xs: "3px", sm: "10px"}}}
                            >
                                {data.records.map((record) => (
                                    <Record
                                        key={record.id}
                                        data={record}
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
            </Box>
        </>
    )
}
