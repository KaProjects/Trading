import React, {useState} from "react";
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Alert,
    AlertTitle,
    Box,
    Button,
    Chip,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControlLabel,
    Paper,
    Switch,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    ToggleButton,
    ToggleButtonGroup,
    Typography,
} from "@mui/material";
import {alpha} from "@mui/material/styles";
import axios from "axios";
import {useNavigate} from "react-router-dom";
import {useData} from "../service/BackendService";
import {recordEvent} from "../service/utils";
import {backend} from "../properties";
import {formatError} from "../service/FormattingService";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import {Loader} from "./component/Loader";
import {EditCompanyDialog} from "../dialog/EditCompanyDialog";
import {STICKY_COLUMN_BODY_SX, STICKY_COLUMN_HEAD_SX} from "./component/tableStyles";

function Section({title, children}) {
    return (
        <Box sx={{width: "100%", maxWidth: 640, margin: "16px auto", padding: "0 8px"}}>
            <Typography color="text.secondary" sx={{marginBottom: "8px"}}>{title}</Typography>
            {children}
        </Box>
    )
}

function Warnings({warnings}) {
    if (!warnings || warnings.length === 0) return null

    return (
        <Alert severity="warning" sx={{marginBottom: "16px"}}>
            <AlertTitle>Some Firebase data could not be loaded</AlertTitle>
            {warnings.map(warning => <Box key={warning}>{warning}</Box>)}
        </Alert>
    )
}

function MissingCompanies({onAdd}) {
    const {data, loaded, error} = useData("/firebase/companies")
    if (!loaded) return <Loader error={error}/>

    return (
        <Section title={`${data.onlyInFirebase.length} companies in Firebase but not in the database`}>
            <Warnings warnings={data.warnings}/>
            {data.onlyInFirebase.length === 0
                ? <Typography color="text.secondary">Every Firebase company exists locally.</Typography>
                : <Box sx={{display: "flex", flexWrap: "wrap", gap: 1}}>
                    {data.onlyInFirebase.map(ticker => (
                        <Chip
                            key={ticker}
                            label={ticker}
                            color="warning"
                            variant="outlined"
                            clickable
                            onClick={() => onAdd(ticker)}
                        />
                    ))}
                </Box>
            }
        </Section>
    )
}

function NotImported() {
    const {data, loaded, error} = useData("/company/lists/actionable")
    const navigate = useNavigate()

    function openResearch(ticker) {
        recordEvent(window.location.pathname + "#redirect:/research")
        navigate({
            pathname: "/research",
            search: `?${new URLSearchParams({company: ticker})}`,
        })
    }

    if (!loaded) return <Loader error={error}/>

    return (
        <Section title={`${data.length} companies with something left to import`}>
            {data.length === 0
                ? <Typography color="text.secondary">Nothing is waiting to be imported.</Typography>
                : <TableContainer component={Paper} sx={{width: "100%"}}>
                    <Table size="small" stickyHeader>
                        <TableHead>
                            <TableRow>
                                <TableCell sx={STICKY_COLUMN_HEAD_SX}>Ticker</TableCell>
                                <TableCell align="right">Periods</TableCell>
                                <TableCell align="right">Targets</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {data.map(row => (
                                <TableRow
                                    key={row.company.id}
                                    hover
                                    sx={{cursor: "pointer"}}
                                    onClick={() => openResearch(row.company.ticker)}
                                >
                                    <TableCell sx={{...STICKY_COLUMN_BODY_SX, color: "primary.main"}}>
                                        {row.company.ticker}
                                    </TableCell>
                                    <TableCell align="right">{row.importablePeriodsCount || "-"}</TableCell>
                                    <TableCell align="right">{row.importableTargetsCount || "-"}</TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </TableContainer>
            }
        </Section>
    )
}


function isEmpty(value) {
    return value === null || value === undefined || value === ""
        || (Array.isArray(value) && value.length === 0)
        || (typeof value === "object" && !Array.isArray(value) && Object.keys(value).length === 0)
}

function FirebaseValue({value, depth = 0}) {
    if (isEmpty(value)) return <Typography sx={{fontSize: 13, color: "text.disabled"}}>-</Typography>

    if (Array.isArray(value)) {
        return (
            <Box component="ul" sx={{margin: 0, paddingLeft: "18px"}}>
                {value.map((item, index) => (
                    <Typography component="li" key={index} sx={{fontSize: 13}}>
                        {typeof item === "object" ? <FirebaseValue value={item} depth={depth + 1}/> : String(item)}
                    </Typography>
                ))}
            </Box>
        )
    }

    if (typeof value === "object") {
        return (
            <Box sx={{paddingLeft: depth === 0 ? 0 : "12px"}}>
                {Object.entries(value).map(([key, nested]) => (
                    <Box key={key} sx={{display: "flex", gap: "8px", alignItems: "baseline", flexWrap: "wrap"}}>
                        <Typography sx={{fontSize: 12, color: "text.secondary", minWidth: 150}}>{key}</Typography>
                        <Box sx={{flexGrow: 1}}>
                            <FirebaseValue value={nested} depth={depth + 1}/>
                        </Box>
                    </Box>
                ))}
            </Box>
        )
    }

    return <Typography sx={{fontSize: 13, wordBreak: "break-word"}}>{String(value)}</Typography>
}

function RecordGroup({title, records, describe}) {
    const entries = Object.entries(records ?? {})

    return (
        <Box sx={{marginBottom: "20px"}}>
            <Typography sx={{fontSize: 12, fontWeight: 700, letterSpacing: "0.06em",
                textTransform: "uppercase", color: "text.secondary", marginBottom: "6px"}}>
                {title} ({entries.length})
            </Typography>
            {entries.length === 0
                ? <Typography color="text.secondary" sx={{fontSize: 13}}>None.</Typography>
                : entries.map(([key, record]) => (
                    <Accordion key={key} disableGutters>
                        <AccordionSummary expandIcon={<ExpandMoreIcon/>}>
                            <Typography sx={{fontSize: 14}}>{describe ? describe(key, record) : key}</Typography>
                        </AccordionSummary>
                        <AccordionDetails>
                            <FirebaseValue value={record}/>
                        </AccordionDetails>
                    </Accordion>
                ))
            }
        </Box>
    )
}

function CompanyDetail({ticker, onBack}) {
    const {data, loaded, error} = useData(`/firebase/company/${ticker}`)
    if (!loaded) return <Loader error={error}/>

    const gemini = data.gemini ?? {}
    const earnings = Object.entries(data.fhe ?? {})
        .reduce((all, [quarter, records]) => {
            Object.entries(records ?? {}).forEach(([key, record]) => {
                all[`${quarter} / ${key}`] = record
            })
            return all
        }, {})

    return (
        <Section title={`${ticker} in Firebase`}>
            <Button startIcon={<ArrowBackIcon/>} onClick={onBack} sx={{marginBottom: "12px"}}>
                Back to all companies
            </Button>
            {gemini.info &&
                <Box sx={{marginBottom: "20px"}}>
                    <Typography sx={{fontSize: 12, fontWeight: 700, letterSpacing: "0.06em",
                        textTransform: "uppercase", color: "text.secondary", marginBottom: "6px"}}>
                        Info
                    </Typography>
                    <FirebaseValue value={gemini.info}/>
                </Box>
            }
            <RecordGroup title="Gemini quarters" records={gemini.quarters}/>
            <RecordGroup
                title="Gemini targets"
                records={gemini.targets}
                describe={(key, record) =>
                    `${record.date ?? key} | ${record.institution ?? "-"} | ${record.price ?? "-"}`}
            />
            <RecordGroup title="Finnhub earnings" records={earnings}/>
            <RecordGroup title="News sentiments" records={data.pgn}/>
        </Section>
    )
}

function Stats({onSelect}) {
    const {data, loaded, error} = useData("/firebase/stats")
    if (!loaded) return <Loader error={error}/>

    return (
        <Section title={`Firebase content of ${data.companies.length} companies`}>
            <Warnings warnings={data.warnings}/>
            <TableContainer component={Paper} sx={{width: "100%"}}>
                <Table size="small" stickyHeader>
                    <TableHead>
                        <TableRow>
                            <TableCell sx={STICKY_COLUMN_HEAD_SX}>Ticker</TableCell>
                            <TableCell align="right">Quarters</TableCell>
                            <TableCell align="right">Targets</TableCell>
                            <TableCell align="right">Earnings</TableCell>
                            <TableCell align="right">Sentiments</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {data.companies.map(row => (
                            <TableRow
                                key={row.ticker}
                                hover
                                sx={{cursor: "pointer"}}
                                onClick={() => onSelect(row.ticker)}
                            >
                                <TableCell sx={{
                                    ...STICKY_COLUMN_BODY_SX,
                                    color: row.inDatabase ? "primary.main" : "warning.main",
                                    fontWeight: row.inDatabase ? undefined : 600,
                                }}>
                                    {row.ticker}
                                </TableCell>
                                <TableCell align="right">{row.geminiQuarters}</TableCell>
                                <TableCell align="right">{row.geminiTargets}</TableCell>
                                <TableCell align="right">{row.finnhubEarnings}</TableCell>
                                <TableCell align="right">{row.newsSentiments}</TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </TableContainer>
        </Section>
    )
}

function FlagSwitch({onLabel, offLabel, checked, onToggle, disabled}) {
    return (
        <Switch
            checked={checked}
            disabled={disabled}
            onChange={onToggle}
            disableRipple
            inputProps={{"aria-label": checked ? onLabel : offLabel}}
            sx={theme => ({
                width: 94,
                height: 24,
                padding: 0,
                "& .MuiSwitch-switchBase": {
                    padding: "3px",
                    "&.Mui-checked": {
                        transform: "translateX(70px)",
                        color: theme.palette.success.main,
                        "& + .MuiSwitch-track": {
                            opacity: 1,
                            backgroundColor: alpha(theme.palette.success.main, 0.16),
                        },
                    },
                },
                "& .MuiSwitch-switchBase .MuiSwitch-input": {
                    top: 0,
                    height: 24,
                    width: 94,
                    left: checked ? -70 : 0,
                    cursor: disabled ? "default" : "pointer",
                },
                "& .MuiSwitch-thumb": {width: 18, height: 18, boxShadow: "none"},
                "& .MuiSwitch-track": {
                    borderRadius: 12,
                    opacity: 1,
                    backgroundColor: theme.palette.grey[400],
                    "&::before, &::after": {
                        position: "absolute",
                        top: "50%",
                        transform: "translateY(-50%)",
                        fontSize: 10,
                        fontWeight: 600,
                        letterSpacing: "0.02em",
                    },
                    "&::before": {
                        content: `"${onLabel}"`,
                        left: 9,
                        opacity: checked ? 1 : 0,
                        color: theme.palette.success.dark,
                    },
                    "&::after": {
                        content: `"${offLabel}"`,
                        right: 9,
                        opacity: checked ? 0 : 1,
                        color: theme.palette.common.white,
                    },
                },
            })}
        />
    )
}

function FlagFilter({yesLabel, noLabel, value, setValue}) {
    return (
        <ToggleButtonGroup
            size="small"
            exclusive
            value={value}
            onChange={(event, next) => next && setValue(next)}
            sx={{"& .MuiToggleButton-root": {fontSize: 11, padding: "2px 10px"}}}
        >
            <ToggleButton value="both">both</ToggleButton>
            <ToggleButton value="yes">{yesLabel}</ToggleButton>
            <ToggleButton value="no">{noLabel}</ToggleButton>
        </ToggleButtonGroup>
    )
}

function Institutions() {
    const [reloadKey, setReloadKey] = useState(0)
    const {data, loaded, error} = useData(`/firebase/institutions?v=${reloadKey}`)
    const [enabledFilter, setEnabledFilter] = useState("both")
    const [trustedFilter, setTrustedFilter] = useState("both")
    const [dragged, setDragged] = useState(null)
    const [dropTarget, setDropTarget] = useState(null)
    const [merge, setMerge] = useState(null)
    const [overrides, setOverrides] = useState({})
    const [merging, setMerging] = useState(false)
    const [mergeError, setMergeError] = useState(null)
    const [toggleError, setToggleError] = useState(null)

    function toggleFlag(institution, flag, value) {
        const next = {enabled: institution.enabled, trusted: institution.trusted, [flag]: value}
        setOverrides(current => ({...current, [institution.key]: next}))
        setToggleError(null)

        axios.put(`${backend}/firebase/institutions/${institution.key}/flags`, next)
            .catch(requestError => {
                setToggleError(formatError(requestError))
                setOverrides(current => {
                    const reverted = {...current}
                    delete reverted[institution.key]
                    return reverted
                })
            })
    }

    function confirmMerge() {
        setMerging(true)
        setMergeError(null)
        axios.post(`${backend}/firebase/institutions/merge`, {
            sourceKey: merge.source.key,
            targetKey: merge.target.key,
        })
            .then(() => {
                setMerge(null)
                setReloadKey(current => current + 1)
            })
            .catch(requestError => setMergeError(formatError(requestError)))
            .finally(() => setMerging(false))
    }

    if (!loaded) return <Loader error={error}/>

    const flagMatches = (filter, flag) => filter === "both" || (filter === "yes") === flag
    const matches = institution =>
        flagMatches(enabledFilter, institution.enabled)
        && flagMatches(trustedFilter, institution.trusted)

    const withOverrides = data.institutions.map(institution =>
        overrides[institution.key] ? {...institution, ...overrides[institution.key]} : institution)
    const visible = withOverrides.filter(matches)

    return (
        <Section title={`${visible.length} of ${data.institutions.length} institutions in Firebase`}>
            <Warnings warnings={data.warnings}/>

            {toggleError &&
                <Alert severity="error" variant="filled" sx={{marginBottom: "12px"}}>
                    <AlertTitle>{toggleError.title}</AlertTitle>{toggleError.message}
                </Alert>
            }

            <Box sx={{display: "flex", flexDirection: "column", gap: "6px",
                alignItems: "flex-start", marginBottom: "12px"}}>
                <FlagFilter
                    yesLabel="enabled"
                    noLabel="disabled"
                    value={enabledFilter}
                    setValue={setEnabledFilter}
                />
                <FlagFilter
                    yesLabel="trusted"
                    noLabel="untrusted"
                    value={trustedFilter}
                    setValue={setTrustedFilter}
                />
            </Box>

            {visible.length === 0
                ? <Typography color="text.secondary" sx={{fontSize: 13}}>
                    No institution matches the selected filters.
                </Typography>
                : <Paper variant="outlined">
                    {visible.map((institution, index) => (
                        <Box
                            key={institution.key}
                            draggable
                            onDragStart={() => setDragged(institution)}
                            onDragEnd={() => {
                                setDragged(null)
                                setDropTarget(null)
                            }}
                            onDragOver={event => {
                                if (!dragged || dragged.key === institution.key) return
                                event.preventDefault()
                                setDropTarget(institution.key)
                            }}
                            onDragLeave={() => setDropTarget(current =>
                                current === institution.key ? null : current)}
                            onDrop={event => {
                                event.preventDefault()
                                setDropTarget(null)
                                if (!dragged || dragged.key === institution.key) return
                                setMergeError(null)
                                setMerge({source: dragged, target: institution})
                            }}
                            data-testid={`institution-${institution.key}`}
                            sx={{
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "space-between",
                                gap: "12px",
                                padding: "8px 12px",
                                cursor: "grab",
                                borderTop: index === 0 ? "none" : "1px solid",
                                borderTopColor: "divider",
                                backgroundColor: dropTarget === institution.key ? "action.hover" : undefined,
                                outline: dropTarget === institution.key ? "2px solid" : "none",
                                outlineColor: "primary.main",
                                opacity: dragged?.key === institution.key ? 0.4 : 1,
                            }}
                        >
                            <Box sx={{minWidth: 0}}>
                                <Typography sx={{fontSize: 14}}>{institution.name}</Typography>
                                {institution.aliases.length > 1 &&
                                    <Typography sx={{fontSize: 12, color: "text.secondary"}}>
                                        {institution.aliases.join(", ")}
                                    </Typography>
                                }
                            </Box>
                            <Box sx={{display: "flex", flexDirection: "column", gap: "4px",
                                alignItems: "flex-end", flexShrink: 0}}>
                                <FlagSwitch
                                    onLabel="enabled"
                                    offLabel="disabled"
                                    checked={institution.enabled}
                                    disabled={merging}
                                    onToggle={event =>
                                        toggleFlag(institution, "enabled", event.target.checked)}
                                />
                                <FlagSwitch
                                    onLabel="trusted"
                                    offLabel="untrusted"
                                    checked={institution.trusted}
                                    disabled={merging}
                                    onToggle={event =>
                                        toggleFlag(institution, "trusted", event.target.checked)}
                                />
                            </Box>
                        </Box>
                    ))}
                </Paper>
            }

            <Dialog open={Boolean(merge)} onClose={() => setMerge(null)}>
                <DialogTitle>Merge institutions</DialogTitle>
                <DialogContent>
                    {mergeError &&
                        <Alert severity="error" variant="filled" sx={{marginBottom: "12px"}}>
                            <AlertTitle>{mergeError.title}</AlertTitle>{mergeError.message}
                        </Alert>
                    }
                    <Typography sx={{marginBottom: "10px"}}>
                        Merge <strong>{merge?.source.name}</strong> into <strong>{merge?.target.name}</strong>?
                    </Typography>
                    <Typography sx={{fontSize: 13, color: "text.secondary"}}>
                        These aliases move to {merge?.target.name}:
                    </Typography>
                    <Box component="ul" sx={{marginTop: "4px", marginBottom: "10px", paddingLeft: "20px"}}>
                        {(merge?.source.aliases ?? []).map(alias => (
                            <Typography component="li" key={alias} sx={{fontSize: 13}}>{alias}</Typography>
                        ))}
                    </Box>
                    <Typography sx={{fontSize: 13, color: "error.main"}}>
                        {merge?.source.name} is then deleted from Firebase. This cannot be undone.
                    </Typography>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setMerge(null)} disabled={merging}>Cancel</Button>
                    <Button color="error" variant="contained" onClick={confirmMerge} disabled={merging}>
                        Merge
                    </Button>
                </DialogActions>
            </Dialog>
        </Section>
    )
}

export const FirebaseCheck = props => {
    const [refreshKey, setRefreshKey] = useState(0)
    const [selectedTicker, setSelectedTicker] = useState(null)
    const [addedTicker, setAddedTicker] = useState(null)
    const navigate = useNavigate()

    function companySaved() {
        if (!addedTicker) {
            setRefreshKey(current => current + 1)
            return
        }
        recordEvent(window.location.pathname + "#redirect:/research")
        window.location.href = `/research?${new URLSearchParams({company: addedTicker})}`
    }

    return (
        <>
            {props.firebaseTabsIndex === 0 &&
                <MissingCompanies
                    key={refreshKey}
                    onAdd={ticker => {
                        setAddedTicker(ticker)
                        props.setOpenEditCompany({ticker})
                    }}
                />
            }
            {props.firebaseTabsIndex === 1 && <NotImported/>}
            {props.firebaseTabsIndex === 2 && (selectedTicker
                ? <CompanyDetail ticker={selectedTicker} onBack={() => setSelectedTicker(null)}/>
                : <Stats onSelect={setSelectedTicker}/>)}
            {props.firebaseTabsIndex === 3 && <Institutions/>}

            <EditCompanyDialog {...props} triggerRefresh={companySaved}/>
        </>
    )
}
