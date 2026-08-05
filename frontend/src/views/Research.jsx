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
import React, {useEffect, useState} from "react";
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
import {BUILT_IN_LIST_TITLES, CompanySelector} from "./component/CompanySelector";
import {PeriodFinancials} from "./component/PeriodFinancials";
import {PeriodEstimatesOverview} from "./component/PeriodEstimatesOverview";
import {SnackbarErrorAlert} from "./component/SnackbarErrorAlert";
import {AddPeriodDialog} from "../dialog/AddPeriodDialog";
import {AddPeriodFinancialDialog} from "../dialog/AddPeriodFinancialDialog";
import {FinancialsDialog} from "../dialog/FinancialsDialog";
import {EarningsProjectionsDialog} from "../dialog/EarningsProjectionsDialog";
import {AddRecordDialog} from "../dialog/AddRecordDialog";
import {ImportPeriodDialog} from "../dialog/ImportPeriodDialog";
import {AddEstimateDialog} from "../dialog/AddEstimateDialog";
import {RESEARCH_TAB} from "./component/MainBar";
import {AddTagDialog} from "../dialog/AddTagDialog";

const badgeStyle = {"& .MuiBadge-badge": {fontSize: "0.6rem", height: "15px", minWidth: "15px", backgroundColor: "#ff7961", color: "white"}}
const researchCardStyle = {
    bgcolor: 'background.paper',
    boxShadow: 1,
    borderRadius: 2,
    minWidth: {xs: 0, sm: 700},
    width: {xs: "100%", sm: 800},
    maxHeight: "calc(100vh - var(--main-bar-height, 48px) - 32px)",
    overflowY: "auto",
}

export const Research = props => {
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
    const [openAddFinancialDialog, setOpenAddFinancialDialog] = useState(null)
    const [openEditFinancialDialog, setOpenEditFinancialDialog] = useState(null)
    const [openAddEstimateDialog, setOpenAddEstimateDialog] = useState(null)
    const [openAddTagDialog, setOpenAddTagDialog] = useState(false)
    const [tagToDelete, setTagToDelete] = useState(null)
    const [tagSuggestions, setTagSuggestions] = useState([])
    const researchTabsIndex = props.researchTabsIndex ?? RESEARCH_TAB.research

    function fetchData(companyChanged) {
        if (props.companySelectorValue) {
            axios.get(backend + "/research/" + props.companySelectorValue.id + (refresh ? "?refresh" + refresh : ""))
                .then((response) => {
                    setData(response.data)
                    setError(null)

                    if (companyChanged) setOpenFinancialsDialog(false)
                    if (companyChanged) setOpenEarningsProjectionsDialog(false)
                    if (companyChanged) setOpenAddEstimateDialog(null)
                    setLoaded(true)
                })
                .catch((error) => {
                    setError(formatError(error))
                    setLoaded(false)
                })
        } else {
            setLoaded(false)
        }
    }

    useEffect(() => {
        fetchData(true)
        // eslint-disable-next-line
    }, [props.companySelectorValue])

    useEffect(() => {
        fetchData(false)
        // eslint-disable-next-line
    }, [refresh])

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

    return (
        <>
            <CompanySelector onCustomTagsChange={setTagSuggestions} {...props}/>
            {props.companySelectorValue && !loaded && <Loader error={error}/>}
            {props.companySelectorValue && loaded && data.company.ticker !== undefined &&
                <Grid container direction="row" sx={{width: "100%", justifyContent: "center", alignItems: "flex-start"}}>
                    <Card sx={{
                        ...researchCardStyle,
                        display: {xs: researchTabsIndex === RESEARCH_TAB.research ? "block" : "none", sm: "block"},
                    }}>
                        <CardContent>
                            <Box sx={{position: "relative"}}>
                                <Box sx={{color: 'text.secondary'}}>Research</Box>
                                <Box sx={{color: 'text.primary', fontSize: 34, fontWeight: 'medium'}}>
                                    {data.company.ticker}
                                </Box>
                                {data.company.sector && <Box sx={{color: 'text.secondary', fontSize: 14, marginTop: "-4px"}}>{data.company.sector.name}</Box>}
                                <Box
                                    sx={{
                                        color: "text.secondary",
                                        display: "flex",
                                        alignItems: "center",
                                        flexWrap: "wrap",
                                        columnGap: "6px",
                                        fontSize: 14,
                                        minHeight: "20px",
                                        minWidth: "20px",
                                        width: "fit-content",
                                        "& .add-tag-button": {
                                            opacity: {xs: 1, sm: 0},
                                            pointerEvents: {xs: "auto", sm: "none"},
                                            transition: "opacity 120ms ease-in-out",
                                        },
                                        "&:hover .add-tag-button": {
                                            opacity: 1,
                                            pointerEvents: "auto",
                                        },
                                        "& .company-tag": {
                                            display: "inline-flex",
                                            alignItems: "center",
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

                                <PeriodFinancials
                                    sx={{marginTop: "20px"}}
                                    ttm={data.ttm}
                                    onOpen={() => setOpenFinancialsDialog(true)}
                                />
                                <PeriodEstimatesOverview
                                    sx={{marginTop: "8px", marginBottom: "20px"}}
                                    overview={data.estimateOverview}
                                    onOpen={() => setOpenEarningsProjectionsDialog(true)}
                                />

                                <Box sx={{position: "absolute", top: "0", right: "0", display: "flex", alignItems: "center"}}>
                                    {data.importablePeriods?.length > 0 &&
                                        <>
                                            <Button onClick={() => setOpenImportPeriodDialog(true)}>
                                                <Badge badgeContent={data.importablePeriods.length} sx={badgeStyle}>
                                                    <CloudDownloadIcon sx={{color: 'lightgreen'}}/>
                                                </Badge>
                                            </Button>
                                            <ImportPeriodDialog
                                                open={openImportPeriodDialog}
                                                handleClose={() => setOpenImportPeriodDialog(false)}
                                                company={props.companySelectorValue}
                                                periods={data.importablePeriods}
                                                triggerRefresh={triggerRefresh}
                                            />
                                        </>
                                    }
                                    <Button onClick={() => setOpenAddPeriodDialog(true)}>
                                        <ControlPointIcon sx={{color: 'lightgreen',}}/>
                                    </Button>
                                    <AddPeriodDialog
                                        open={openAddPeriodDialog}
                                        handleClose={() => setOpenAddPeriodDialog(false)}
                                        triggerRefresh={triggerRefresh}
                                        companyId={props.companySelectorValue.id}
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
                            <AddEstimateDialog
                                open={openAddEstimateDialog !== null}
                                handleClose={() => setOpenAddEstimateDialog(null)}
                                triggerRefresh={triggerRefresh}
                                company={props.companySelectorValue}
                                period={openAddEstimateDialog}
                            />

                            {data.periods.map((period) => (
                                <Period
                                    key={period.id}
                                    period={period}
                                    currency={data.company.currency}
                                    setAlert={setAlert}
                                    openDialog={() => setOpenAddFinancialDialog(period)}
                                    openEditDialog={() => setOpenEditFinancialDialog(period)}
                                    openEstimateDialog={() => setOpenAddEstimateDialog(period)}
                                />
                            ))}
                        </CardContent>
                    </Card>
                    <Card sx={{
                        ...researchCardStyle,
                        display: {xs: researchTabsIndex === RESEARCH_TAB.records ? "block" : "none", sm: "block"},
                    }}>
                        <CardContent>
                            <Box sx={{position: "relative"}}>
                                <Box sx={{color: 'text.secondary'}}>Records</Box>

                                {data.latest &&
                                <>
                                    <Box sx={{color: 'text.primary', fontSize: 34, fontWeight: 'medium'}}>
                                        {data.company.currency}{formatDecimals(data.latest.price,0,2)}
                                    </Box>
                                    <DateTime value={data.latest.datetime} sx={{marginTop: '-2px', color: 'text.secondary', fontSize: 11}} iconMarginTop={"1px"}/>
                                </>
                                }

                                <Button sx={{position: "absolute", top: "0", right: "0"}} onClick={() => setOpenAddRecordDialog(true)}>
                                    <ControlPointIcon sx={{color: 'lightgreen',}}/>
                                </Button>
                                <AddRecordDialog
                                    open={openAddRecordDialog}
                                    handleClose={() => setOpenAddRecordDialog(false)}
                                    triggerRefresh={triggerRefresh}
                                    companyId={props.companySelectorValue.id}
                                    indicators={data.indicators}
                                    assets={data.assets}
                                />
                            </Box>

                            {data.indicators &&
                                <Box>
                                    <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>Market Cap: {data.company.currency}{formatMillions(data.indicators.marketCap)}</Box>
                                    <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>Dividend Yield: {formatPercent(data.indicators.ttm.dividendYield)}</Box>

                                    <Stack direction={"row"} spacing={2}>
                                        <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>PS: {formatDecimals(data.indicators.ttm.marketCapToRevenues, 0, 2)}</Box>
                                        <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>PG: {formatDecimals(data.indicators.ttm.marketCapToGrossProfit, 0, 2)}</Box>
                                        <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>PO: {formatDecimals(data.indicators.ttm.marketCapToOperatingIncome, 0, 2)}</Box>
                                        <Box sx={{color: 'text.secondary', fontSize: 11, marginTop: "0px"}}>PE: {formatDecimals(data.indicators.ttm.marketCapToNetIncome, 0, 2)}</Box>
                                    </Stack>
                                </Box>
                            }

                            {data.assets.assets.length > 0 &&
                                <Stack
                                    direction="row"
                                    spacing={1}
                                    sx={{
                                        margin: "10px 10px 20px 10px",
                                        maxWidth: "100%",
                                        overflowX: "auto",
                                        overflowY: "hidden",
                                        pb: {xs: 1, sm: 0},
                                        "& > *": {flexShrink: 0},
                                    }}
                                >
                                    {data.assets.assets.map((asset, index) => (
                                        <AssetBox key={`${data.company.id}-${index}`} asset={asset} currency={data.company.currency}/>
                                    ))}
                                </Stack>
                            }

                            {data.records.map((record) => (
                                <Record
                                    key={record.id}
                                    data={record}
                                    currency={data.company.currency}
                                    setAlert={setAlert}
                                    deleteRecord={deleteRecord}
                                />
                            ))}
                        </CardContent>
                    </Card>
                </Grid>
            }
            <SnackbarErrorAlert error={alert} open={alert !== null} onClose={() => setAlert(null)}/>
        </>
    )
}
