import React, {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router-dom";
import {
    Autocomplete,
    Box,
    Button,
    Chip,
    IconButton,
    ListItemText,
    Menu,
    MenuItem,
    Paper,
    Stack,
    TextField,
    Tooltip,
    Typography,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import AddIcon from "@mui/icons-material/Add";
import PostAddIcon from "@mui/icons-material/PostAdd";
import axios from "axios";
import {useData} from "../service/BackendService";
import {Loader} from "./component/Loader";
import {SnackbarErrorAlert} from "./component/SnackbarErrorAlert";
import useMediaQuery from "@mui/material/useMediaQuery";
import {SWIPE_NAV_BREAKPOINT} from "./component/MainBar";
import {COMPANY_CHIP, NARROW} from "./component/companyChip";
import {researchPath} from "./component/GoToResearch";
import {CaseTrades} from "./component/CaseTrades";
import {CaseEntryList, hasEntryForToday, today} from "./component/CaseEntryList";
import {isEmptyTable} from "./component/CaseTable";
import {useStagedImages} from "./component/useStagedImages";
import {defaultContent} from "./component/ContentEditor";
import {backend} from "../properties";
import {CaseNameDialog} from "../dialog/CaseNameDialog";
import {CaseCompaniesDialog} from "../dialog/CaseCompaniesDialog";
import {ConfirmDialog} from "../dialog/ConfirmDialog";
import {CaseRecordDialog} from "../dialog/CaseRecordDialog";
import {CaseResearchDialog} from "../dialog/CaseResearchDialog";
import {CaseReportDialog} from "../dialog/CaseReportDialog";
import {CaseDateDialog} from "../dialog/CaseDateDialog";
import {formatDate, formatError} from "../service/FormattingService";

/** A day that is changed by clicking it. */
const DATE_TEXT = {cursor: "pointer", "&:hover": {textDecoration: "underline"}}

const ADD_COMPANY = {[NARROW]: {padding: 0, "& svg": {fontSize: 13}}}
/** The input that opens beside the plus, kept to the height of the chips it stands among. */
const COMPANY_INPUT = {
    width: "180px",
    [NARROW]: {
        width: "100px",
        // The Autocomplete pads its own root and hangs a button off the end; both are what
        // make the field twice the height of the chips beside it.
        "&& .MuiAutocomplete-inputRoot": {padding: "0 2px 0 4px", fontSize: 10, minHeight: 0},
        "&& .MuiAutocomplete-input": {padding: "1px 0", height: "12px"},
        "& .MuiAutocomplete-endAdornment": {right: "2px"},
        "&& .MuiAutocomplete-popupIndicator, && .MuiAutocomplete-clearIndicator": {
            padding: 0,
            "& svg": {fontSize: 14},
        },
    },
}

/**
 * One case: what it is called, the companies it covers, and the days written into it. A case is
 * kept forwards - the only day that can be opened is today - so the entries carry no date
 * picker; what the day holds is written into it for as long as it is still today.
 */
export const Case = props => {
    const {caseId} = useParams()
    const navigate = useNavigate()
    const [refresh, setRefresh] = useState("")
    const {data, loaded, error} = useData(`/case/${caseId}${refresh ? "?refresh=" + refresh : ""}`)
    const [alert, setAlert] = useState(null)
    const [menuAnchor, setMenuAnchor] = useState(null)
    const [renaming, setRenaming] = useState(false)
    const [managingCompanies, setManagingCompanies] = useState(false)
    const [deleting, setDeleting] = useState(false)
    const [openedMention, setOpenedMention] = useState(null)
    const [openedDate, setOpenedDate] = useState(false)
    const [closedDate, setClosedDate] = useState(false)
    const [movedEntry, setMovedEntry] = useState(null)
    const [addingCompany, setAddingCompany] = useState(false)
    const [editingDate, setEditingDate] = useState(null)
    // What the day looked like when the writing started, so cancelling can put it back.
    const [editSnapshot, setEditSnapshot] = useState(null)
    const [entries, setEntries] = useState([])

    useEffect(() => {if (data) setEntries(data.entries ?? [])}, [data])

    const narrow = useMediaQuery(`(max-width:${SWIPE_NAV_BREAKPOINT}px)`)
    // A closed case is history: it is read from here on, and reopened before it is written to.
    const closed = Boolean(data?.closedAt)
    const companies = props.companyLists?.all ?? []
    const value = data

    const {
        stagedFor, stageImage, dropStagedImage, markImageRemoved, discardStaged, commitStaged,
    } = useStagedImages({
        uploadUrl: entryId => `${backend}/case/entry/${entryId}/image`,
        deleteUrl: imageId => `${backend}/case/entry/image/${imageId}`,
        applied: (entryId, {added, removedIds}) => setEntries(previous =>
            previous.map(entry => entry.id === entryId
                ? {...entry, images: [...added, ...entry.images.filter(image => !removedIds.includes(image.id))]}
                : entry)),
        setAlert,
    })

    function save(changes) {
        return axios.put(`${backend}/case/${caseId}`, changes)
            .then(() => setRefresh(Date.now().toString()))
            .catch(reason => setAlert(formatError(reason)))
    }

    /**
     * A case covers what it covers whether the app holds the company or not. A written name that
     * turns out to be a ticker the app does know is linked instead - typing it is a way of
     * naming the company, not of insisting it is a stranger.
     */
    function addCompany(company) {
        if (!company) return

        if (typeof company !== "string") {
            return save({companies: [...coveredCompanies(), {id: company.id}]})
        }

        const written = company.trim()
        if (written === "") return

        const known = companies.find(each => each.ticker.toUpperCase() === written.toUpperCase())
        if (known) {
            if (value.companies.some(each => each.id === known.id)) return
            return save({companies: [...coveredCompanies(), {id: known.id}]})
        }

        if (value.companies.some(each =>
            !each.id && each.ticker.toUpperCase() === written.toUpperCase())) return

        // No day given: it joins the case today.
        return save({companies: [...coveredCompanies(), {name: written}]})
    }

    /** The companies the case covers as the server takes them, each keeping its own day. */
    function coveredCompanies() {
        return (value?.companies ?? []).map(company => (company.id
            ? {id: company.id, addedAt: company.addedAt}
            : {name: company.ticker, addedAt: company.addedAt}))
    }

    /** A day keeps what was written into it; the editor starts again on what was saved. */
    function saveEntry(date, content) {
        return axios.put(`${backend}/case/entry/${caseId}`, {date: date, content: JSON.stringify(content)})
            .then(response => {
                setEntries(previous => {
                    const written = response.data
                    const before = previous.find(entry => entry.date === written.date)
                    const saved = {...written, version: (before?.version ?? 0) + 1}
                    return [...previous.filter(entry => entry.date !== saved.date), saved]
                        .sort((first, second) => second.date.localeCompare(first.date))
                })
            })
            .catch(reason => {
                const formatted = formatError(reason)
                setAlert(formatted)
                return formatted
            })
    }

    /** Tables are typed in place: every change is the whole grid, written as it is made. */
    function addTable(entryId) {
        if (!entryId) return

        axios.post(`${backend}/case/entry/${entryId}/table`)
            .then(response => setEntries(previous => previous.map(entry => entry.id === entryId
                ? {...entry, tables: [...(entry.tables ?? []), response.data]}
                : entry)))
            .catch(reason => setAlert(formatError(reason)))
    }

    /** Typed into the page; written when the day is done, along with everything else. */
    function changeTable(tableId, changes) {
        setEntries(previous => previous.map(entry => ({
            ...entry,
            tables: (entry.tables ?? []).map(table =>
                table.id === tableId ? {...table, ...changes} : table),
        })))
    }

    /**
     * Everything the day now holds, written in one go: the text as it stands in the editor, the
     * pictures staged beside it, and every table as it was typed.
     */
    async function finishEntry(entry, draft) {
        if (draft) {
            if (await saveEntry(entry.date, draft)) return
        }
        if (entry.id) {
            if (await commitStaged(entry.id)) return

            for (const table of entry.tables ?? []) {
                try {
                    // A table nothing was typed into goes again: it was created so that there
                    // was something to type into, and that is all it ever was.
                    if (isEmptyTable(table)) {
                        await axios.delete(`${backend}/case/entry/table/${table.id}`)
                        setEntries(previous => previous.map(each => each.id === entry.id
                            ? {...each, tables: (each.tables ?? []).filter(kept => kept.id !== table.id)}
                            : each))
                        continue
                    }
                    await axios.put(`${backend}/case/entry/table/${table.id}`, {
                        title: table.title ?? "",
                        columns: table.columns ?? [],
                        rows: table.rows ?? [],
                    })
                } catch (reason) {
                    setAlert(formatError(reason))
                    return
                }
            }
        }
        setEditingDate(null)
    }

    function deleteTable(entryId, tableId) {
        axios.delete(`${backend}/case/entry/table/${tableId}`)
            .then(() => setEntries(previous => previous.map(entry => entry.id === entryId
                ? {...entry, tables: (entry.tables ?? []).filter(table => table.id !== tableId)}
                : entry)))
            .catch(reason => setAlert(formatError(reason)))
    }

    function startEditing(date) {
        const entry = entries.find(each => each.date === date)
        setEditSnapshot(entry ? JSON.parse(JSON.stringify(entry)) : null)
        setEditingDate(date)
    }

    /**
     * Gives up on the writing: the text goes back to what was saved, the staged pictures are
     * dropped, the tables return to the snapshot taken when the writing started, and a table
     * added along the way - which had to be created to be typed into - is removed again.
     */
    async function cancelEntry(entry) {
        const kept = new Set((editSnapshot?.tables ?? []).map(table => table.id))
        for (const table of entry.tables ?? []) {
            if (kept.has(table.id)) continue

            try {
                await axios.delete(`${backend}/case/entry/table/${table.id}`)
            } catch (reason) {
                setAlert(formatError(reason))
            }
        }
        if (entry.id) discardStaged(entry.id)
        if (editSnapshot) {
            setEntries(previous => previous.map(each =>
                each.id === editSnapshot.id ? editSnapshot : each))
        }
        setEditSnapshot(null)
        setEditingDate(null)
    }

    /** A day written down late is moved to the day it belongs to. */
    function moveEntry(entryId, date) {
        axios.put(`${backend}/case/entry/${entryId}/date`, {date: date})
            .then(response => setEntries(previous => previous
                .map(entry => entry.id === entryId ? {...entry, date: response.data.date} : entry)
                .sort((first, second) => second.date.localeCompare(first.date))))
            .catch(reason => setAlert(formatError(reason)))
    }

    function removeCase() {
        axios.delete(`${backend}/case/${caseId}`)
            .then(() => navigate("/cases"))
            .catch(reason => setAlert(formatError(reason)))
    }

    /**
     * Today, once: a day the case already holds is written into where it stands.
     *
     * The day is created before anything is written into it, because a picture or a table has
     * to belong to an entry that exists - with the day only in the page, they had nothing to be
     * attached to. A day left empty is hidden again on the next load, as a period's is.
     */
    async function addEntry() {
        const date = today()
        if (await saveEntry(date, defaultContent())) return

        setEditSnapshot(null)
        setEditingDate(date)
    }

    const written = hasEntryForToday(entries)

    /**
     * The trades as days of their own: the day one was bought, and the day it was sold. They
     * are derived, so they are read in the stream and nowhere written into it.
     */
    /** How many of the case's trades each company accounts for, as the dialog warns about. */
    const tradeCounts = (value?.trades ?? []).reduce((counts, trade) => {
        counts[trade.ticker] = (counts[trade.ticker] ?? 0) + 1
        return counts
    }, {})

    /**
     * What the app holds about the case's companies on the days it covers them. A record
     * written on the day of a trade is that trade's record, so the trade line opens it rather
     * than standing a line of its own next to it.
     */
    const mentions = (value?.mentions ?? []).map(mention => ({
        key: `${mention.kind}-${mention.id}`,
        kind: mention.kind,
        id: mention.id,
        date: mention.date,
        ticker: mention.ticker,
    }))

    /** The day a company joined the case is part of the case's story, so it reads in it. */
    const companyLines = (value?.companies ?? [])
        .filter(company => company.addedAt)
        .map(company => ({
            key: `company-${company.id ?? company.ticker}`,
            date: company.addedAt,
            ticker: company.ticker,
        }))

    const tradeLines = (value?.trades ?? []).flatMap(trade => {
        const lines = [{
            key: `${trade.id}-bought`,
            date: trade.purchaseDate,
            ticker: trade.ticker,
            currency: trade.currency,
            quantity: trade.quantity,
            price: trade.purchasePrice,
            total: trade.purchaseTotal,
            sold: false,
        }]
        if (trade.sellDate) {
            lines.push({
                key: `${trade.id}-sold`,
                date: trade.sellDate,
                ticker: trade.ticker,
                currency: trade.currency,
                quantity: trade.quantity,
                price: trade.sellPrice,
                sold: true,
                profit: trade.profit,
                profitPercentage: trade.profitPercentage,
            })
        }
        return lines
    })

    return (
        <>
            {!loaded && <Loader error={error}/>}
            {loaded &&
                <Box sx={{
                    width: {xs: "100%", sm: "700px"},
                    margin: "5px auto",
                    [NARROW]: {margin: "3px auto"},
                }}>
                    <Paper elevation={1} sx={{padding: "10px 12px"}}>
                        <Stack direction="row" alignItems="center" spacing={1}>
                            <Tooltip title="Back to cases">
                                <IconButton size="small" aria-label="Back to cases" onClick={() => navigate("/cases")}>
                                    <ArrowBackIcon fontSize="small"/>
                                </IconButton>
                            </Tooltip>
                            <Typography sx={{fontWeight: 600, flex: 1, minWidth: 0}}>{value.name}</Typography>
                            {!written && !closed &&
                                <Tooltip title="Write today">
                                    <Button size="small" aria-label="Write today" onClick={addEntry}>
                                        <PostAddIcon sx={{color: "success.main"}}/>
                                    </Button>
                                </Tooltip>
                            }
                            {/* Closing, renaming and deleting are rare and none of them should be
                                a stray click away, so they live behind the menu. */}
                            <Tooltip title="Case actions">
                                <IconButton size="small" aria-label="Case actions"
                                            onClick={event => setMenuAnchor(event.currentTarget)}>
                                    <MoreVertIcon fontSize="small"/>
                                </IconButton>
                            </Tooltip>
                            <Menu
                                open={Boolean(menuAnchor)}
                                anchorEl={menuAnchor}
                                onClose={() => setMenuAnchor(null)}
                                anchorOrigin={{vertical: "bottom", horizontal: "right"}}
                                transformOrigin={{vertical: "top", horizontal: "right"}}
                            >
                                <MenuItem onClick={() => {setMenuAnchor(null);setRenaming(true)}}>
                                    <ListItemText primaryTypographyProps={{fontSize: 14}}>Rename</ListItemText>
                                </MenuItem>
                                <MenuItem onClick={() => {setMenuAnchor(null);setManagingCompanies(true)}}>
                                    <ListItemText primaryTypographyProps={{fontSize: 14}}>Companies</ListItemText>
                                </MenuItem>
                                <MenuItem onClick={() => {setMenuAnchor(null);save({closed: !value.closedAt})}}>
                                    <ListItemText primaryTypographyProps={{fontSize: 14}}>
                                        {value.closedAt ? "Reopen" : "Close"}
                                    </ListItemText>
                                </MenuItem>
                                {/* A case still being written is not deleted; close it first. */}
                                {value.closedAt &&
                                    <MenuItem onClick={() => {setMenuAnchor(null);setDeleting(true)}}>
                                        <ListItemText primaryTypographyProps={{fontSize: 14, color: "error.main"}}>
                                            Delete
                                        </ListItemText>
                                    </MenuItem>
                                }
                            </Menu>
                        </Stack>

                        {/* Either day can be put right, and each is asked about on its own: a
                            case kept from the past did not start on the day it was written
                            down, and it was not closed then either. */}
                        <Typography sx={{
                            fontSize: 12,
                            color: "text.secondary",
                            marginTop: "2px",
                            width: "fit-content",
                        }}>
                            <Box component="span"
                                 data-testid="case-opened"
                                 role="button"
                                 aria-label="Day the case opened"
                                 onClick={() => setOpenedDate(true)}
                                 sx={DATE_TEXT}>
                                {`opened ${formatDate(value.createdAt)}`}
                            </Box>
                            {value.closedAt &&
                                <>
                                    {", "}
                                    <Box component="span"
                                         data-testid="case-closed"
                                         role="button"
                                         aria-label="Day the case closed"
                                         onClick={() => setClosedDate(true)}
                                         sx={DATE_TEXT}>
                                        {`closed ${formatDate(value.closedAt)}`}
                                    </Box>
                                </>
                            }
                        </Typography>

                        {/* The selector is not a permanent fixture: it opens beside the plus
                            when a company is being added, and goes again once one is. */}
                        <Stack direction="row" alignItems="center"
                               data-testid="case-companies"
                               sx={{
                                   marginTop: "5px",
                                   flexWrap: "wrap",
                                   gap: "4px",
                                   [NARROW]: {marginTop: "3px", gap: "3px"},
                               }}>
                            {/* Both kinds read as one row, oldest in the case first. A chip
                                is not taken off here: a company belongs to what the case was
                                about, so it goes from the menu and on purpose. */}
                            {value.companies.map(company => (
                                <Chip
                                    key={company.id ?? `name:${company.ticker}`}
                                    label={company.ticker}
                                    size="small"
                                    variant={company.id ? "filled" : "outlined"}
                                    // A company the app holds has a page of its own; one it
                                    // only has the name of has nowhere to go.
                                    aria-label={company.id ? `Go to ${company.ticker} research` : undefined}
                                    onClick={company.id
                                        ? () => navigate(researchPath(company.ticker))
                                        : undefined}
                                    sx={company.id
                                        ? {...COMPANY_CHIP, "&:hover .MuiChip-label": {textDecoration: "underline"}}
                                        : {...COMPANY_CHIP, fontStyle: "italic"}}
                                />
                            ))}
                            {!closed &&
                                <Tooltip title="Add company">
                                    <IconButton size="small" aria-label="Add company"
                                                sx={ADD_COMPANY}
                                                onClick={() => setAddingCompany(true)}>
                                        <AddIcon sx={{fontSize: 16}}/>
                                    </IconButton>
                                </Tooltip>
                            }
                            {addingCompany && !closed &&
                                <Autocomplete
                                    size="small"
                                    freeSolo
                                    openOnFocus
                                    sx={COMPANY_INPUT}
                                    options={companies.filter(company =>
                                        !value.companies.some(each => each.id === company.id))}
                                    getOptionLabel={company =>
                                        typeof company === "string" ? company : company.ticker}
                                    value={null}
                                    onChange={(event, company) => {
                                        setAddingCompany(false)
                                        addCompany(company)
                                    }}
                                    onBlur={() => setAddingCompany(false)}
                                    renderInput={params => (
                                        // The label is what makes the field tall, so on a phone
                                        // the field says what it is in the box instead.
                                        narrow
                                            ? <TextField {...params} autoFocus placeholder="Company"
                                                         inputProps={{...params.inputProps, "aria-label": "Company"}}/>
                                            : <TextField {...params} autoFocus label="Company"/>
                                    )}
                                />
                            }
                        </Stack>
                    </Paper>

                    {(value.trades ?? []).length > 0 &&
                        <Paper elevation={1} sx={{padding: "10px 12px", marginTop: "5px", [NARROW]: {marginTop: "3px"}}}>
                            <CaseTrades trades={value.trades}/>
                        </Paper>
                    }

                    <Paper elevation={1} sx={{padding: "10px 12px", marginTop: "5px", [NARROW]: {marginTop: "3px"}}}>
                        <CaseEntryList
                            readOnly={closed}
                            entries={entries}
                            trades={tradeLines}
                            additions={companyLines}
                            mentions={mentions}
                            onOpenMention={setOpenedMention}
                            stagedFor={stagedFor}
                            onDone={finishEntry}
                            onStageImage={stageImage}
                            onDropStagedImage={dropStagedImage}
                            onMarkImageRemoved={markImageRemoved}
                            onAddTable={addTable}
                            onChangeTable={changeTable}
                            onDeleteTable={deleteTable}
                            editingDate={editingDate}
                            onEditingDateChange={startEditing}
                            onCancel={cancelEntry}
                            onMoveEntry={entry => setMovedEntry(entry)}
                        />
                        {/* The stream carries the trades and the companies as they joined, so
                            it is only empty when none of those has happened either. */}
                        {entries.length === 0 && tradeLines.length === 0 && companyLines.length === 0
                            && mentions.length === 0 &&
                            <Typography sx={{color: "text.secondary", fontSize: 13}}>
                                Nothing written yet.
                            </Typography>
                        }
                    </Paper>
                </Box>
            }

            <CaseDateDialog
                open={openedDate}
                title="Case opened"
                initialDate={value?.createdAt ?? ""}
                onSubmit={date => save({createdAt: date})}
                handleClose={() => setOpenedDate(false)}
            />
            <CaseDateDialog
                open={closedDate}
                title="Case closed"
                initialDate={value?.closedAt ?? ""}
                onSubmit={date => save({closedAt: date})}
                handleClose={() => setClosedDate(false)}
            />
            <CaseDateDialog
                open={Boolean(movedEntry)}
                title="Day written"
                initialDate={movedEntry?.date ?? ""}
                onSubmit={date => moveEntry(movedEntry.id, date)}
                handleClose={() => setMovedEntry(null)}
            />
            <CaseRecordDialog
                open={openedMention?.kind === "RECORD"}
                recordId={openedMention?.id}
                ticker={openedMention?.ticker}
                handleClose={() => setOpenedMention(null)}
            />
            <CaseResearchDialog
                open={openedMention?.kind === "RESEARCH"}
                contentId={openedMention?.id}
                ticker={openedMention?.ticker}
                handleClose={() => setOpenedMention(null)}
            />
            <CaseReportDialog
                open={openedMention?.kind === "REPORT"}
                periodId={openedMention?.id}
                ticker={openedMention?.ticker}
                handleClose={() => setOpenedMention(null)}
            />
            <CaseCompaniesDialog
                open={managingCompanies}
                companies={value?.companies ?? []}
                tradeCounts={tradeCounts}
                onSubmit={wanted => save({companies: wanted})}
                handleClose={() => setManagingCompanies(false)}
            />
            <ConfirmDialog
                open={deleting}
                title="Delete case"
                message={`'${value?.name ?? ""}' and everything written into it will be deleted.`}
                onConfirm={removeCase}
                handleClose={() => setDeleting(false)}
            />
            <CaseNameDialog
                open={renaming}
                title="Rename case"
                initialName={value?.name ?? ""}
                onSubmit={newName => save({name: newName})}
                handleClose={() => setRenaming(false)}
            />
            <SnackbarErrorAlert error={alert} open={alert !== null} onClose={() => setAlert(null)}/>
        </>
    )
}
