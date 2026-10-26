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
import {CaseEntryList, hasEntryForToday, today} from "./component/CaseEntryList";
import {isEmptyTable} from "./component/CaseTable";
import {useStagedImages} from "./component/useStagedImages";
import {defaultContent} from "./component/ContentEditor";
import {backend} from "../properties";
import {CaseNameDialog} from "../dialog/CaseNameDialog";
import {CaseDateDialog} from "../dialog/CaseDateDialog";
import {NUMERIC_FONT} from "./component/widgetLayout";
import {formatDate, formatDateShort, formatError, formatDecimals, formatPercent} from "../service/FormattingService";
import {tightPlus} from "./component/tightPlus";

const TRADE_CELL = {padding: "1px 6px 1px 0", whiteSpace: "nowrap"}

/** What the closed trades of the case have made, one total per currency they were made in. */
function profitByCurrency(trades = []) {
    const totals = new Map()
    trades
        .filter(trade => !trade.active && trade.profit !== null && trade.profit !== undefined)
        .forEach(trade => {
            const currency = trade.currency ?? ""
            totals.set(currency, (totals.get(currency) ?? 0) + Number(trade.profit))
        })
    return [...totals.entries()]
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
    const [openedDate, setOpenedDate] = useState(false)
    const [movedEntry, setMovedEntry] = useState(null)
    const [addingCompany, setAddingCompany] = useState(false)
    const [editingDate, setEditingDate] = useState(null)
    // What the day looked like when the writing started, so cancelling can put it back.
    const [editSnapshot, setEditSnapshot] = useState(null)
    const [entries, setEntries] = useState([])

    useEffect(() => {if (data) setEntries(data.entries ?? [])}, [data])

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
            return save({companyIds: [...value.companies.map(each => each.id), company.id]})
        }

        const written = company.trim()
        if (written === "") return

        const known = companies.find(each => each.ticker.toUpperCase() === written.toUpperCase())
        if (known) {
            if (value.companies.some(each => each.id === known.id)) return
            return save({companyIds: [...value.companies.map(each => each.id), known.id]})
        }

        const names = value.companyNames ?? []
        if (names.some(each => each.toUpperCase() === written.toUpperCase())) return
        return save({companyNames: [...names, written]})
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
                <Box sx={{width: {xs: "100%", sm: "700px"}, margin: "10px auto"}}>
                    <Paper elevation={1} sx={{padding: "10px 12px"}}>
                        <Stack direction="row" alignItems="center" spacing={1}>
                            <Tooltip title="Back to cases">
                                <IconButton size="small" aria-label="Back to cases" onClick={() => navigate("/cases")}>
                                    <ArrowBackIcon fontSize="small"/>
                                </IconButton>
                            </Tooltip>
                            <Typography sx={{fontWeight: 600, flex: 1, minWidth: 0}}>{value.name}</Typography>
                            {!written &&
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
                                <MenuItem onClick={() => {setMenuAnchor(null);save({closed: !value.closedAt})}}>
                                    <ListItemText primaryTypographyProps={{fontSize: 14}}>
                                        {value.closedAt ? "Reopen" : "Close"}
                                    </ListItemText>
                                </MenuItem>
                                {/* A case still being written is not deleted; close it first. */}
                                {value.closedAt &&
                                    <MenuItem onClick={() => {setMenuAnchor(null);removeCase()}}>
                                        <ListItemText primaryTypographyProps={{fontSize: 14, color: "error.main"}}>
                                            Delete
                                        </ListItemText>
                                    </MenuItem>
                                }
                            </Menu>
                        </Stack>

                        {/* The opened day can be put right: a case kept from the past did not
                            start on the day it was written down. */}
                        <Typography
                            data-testid="case-opened"
                            onClick={() => setOpenedDate(true)}
                            sx={{
                                fontSize: 12,
                                color: "text.secondary",
                                marginTop: "2px",
                                width: "fit-content",
                                cursor: "pointer",
                                "&:hover": {textDecoration: "underline"},
                            }}
                        >
                            {`opened ${formatDate(value.createdAt)}`}
                            {value.closedAt ? `, closed ${formatDate(value.closedAt)}` : ""}
                        </Typography>

                        {/* The selector is not a permanent fixture: it opens beside the plus
                            when a company is being added, and goes again once one is. */}
                        <Stack direction="row" alignItems="center"
                               sx={{marginTop: "10px", flexWrap: "wrap", gap: "4px"}}>
                            {value.companies.map(company => (
                                <Chip
                                    key={company.id}
                                    label={company.ticker}
                                    size="small"
                                    onDelete={() => save({
                                        companyIds: value.companies
                                            .filter(each => each.id !== company.id)
                                            .map(each => each.id),
                                    })}
                                />
                            ))}
                            {/* Companies the app does not hold are marked as written rather than
                                linked, so it is plain which of them it can actually tell about. */}
                            {(value.companyNames ?? []).map(name => (
                                <Chip
                                    key={name}
                                    label={name}
                                    size="small"
                                    variant="outlined"
                                    sx={{fontStyle: "italic"}}
                                    onDelete={() => save({
                                        companyNames: value.companyNames.filter(each => each !== name),
                                    })}
                                />
                            ))}
                            <Tooltip title="Add company">
                                <IconButton size="small" aria-label="Add company"
                                            onClick={() => setAddingCompany(true)}>
                                    <AddIcon sx={{fontSize: 16}}/>
                                </IconButton>
                            </Tooltip>
                            {addingCompany &&
                                <Autocomplete
                                    size="small"
                                    freeSolo
                                    openOnFocus
                                    sx={{width: "180px"}}
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
                                    renderInput={params => <TextField {...params} autoFocus label="Company"/>}
                                />
                            }
                        </Stack>
                    </Paper>

                    {(value.trades ?? []).length > 0 &&
                        <Paper elevation={1} sx={{padding: "10px 12px", marginTop: "10px"}}>
                            {/* Narrow screens scroll the table rather than the page: left to
                                itself it is wider than the phone and takes the page with it. */}
                            <Box sx={{overflowX: "auto", maxWidth: "100%"}}>
                            <Box component="table" data-testid="case-trades"
                                 sx={{...NUMERIC_FONT, borderCollapse: "collapse", fontSize: 12, width: "100%"}}>
                                <Box component="thead">
                                    <Box component="tr">
                                        {["", "bought", "quantity", "price", "sold", "price", "profit", ""]
                                            .map((heading, index) => (
                                                <Box component="th" key={index} sx={{
                                                    ...TRADE_CELL,
                                                    textAlign: index === 0 || index === 1 || index === 4
                                                        ? "left" : "right",
                                                    fontWeight: 400,
                                                    color: "text.disabled",
                                                }}>
                                                    {heading}
                                                </Box>
                                            ))}
                                    </Box>
                                </Box>
                                <Box component="tbody">
                                    {/* Oldest first: the table reads as the case was traded. */}
                                    {[...value.trades]
                                        .sort((first, second) =>
                                            String(first.purchaseDate).localeCompare(String(second.purchaseDate)))
                                        .map(trade => (
                                        <Box component="tr" key={trade.id}>
                                            <Box component="td" sx={{...TRADE_CELL, fontWeight: 600}}>
                                                {trade.ticker}
                                            </Box>
                                            <Box component="td" sx={TRADE_CELL}>{formatDateShort(trade.purchaseDate)}</Box>
                                            <Box component="td" sx={{...TRADE_CELL, textAlign: "right"}}>
                                                {formatDecimals(trade.quantity, 0, 4)}
                                            </Box>
                                            <Box component="td" sx={{...TRADE_CELL, textAlign: "right"}}>
                                                {formatDecimals(trade.purchasePrice, 2, 2) + (trade.currency ?? "")}
                                            </Box>
                                            {/* A part-sold position is two trades sharing a purchase
                                                date: one closed here, one still open. */}
                                            <Box component="td" sx={{...TRADE_CELL, color: trade.active ? "text.disabled" : "text.primary"}}>
                                                {trade.active ? "open" : formatDateShort(trade.sellDate)}
                                            </Box>
                                            <Box component="td" sx={{...TRADE_CELL, textAlign: "right"}}>
                                                {trade.active ? "" : formatDecimals(trade.sellPrice, 2, 2) + (trade.currency ?? "")}
                                            </Box>
                                            <Box component="td" sx={{
                                                ...TRADE_CELL,
                                                textAlign: "right",
                                                color: Number(trade.profit) < 0 ? "error.main" : "success.main",
                                            }}>
                                                {trade.active ? "" : formatDecimals(trade.profit, 2, 2) + (trade.currency ?? "")}
                                            </Box>
                                            <Box component="td" sx={{
                                                ...TRADE_CELL,
                                                textAlign: "right",
                                                color: Number(trade.profit) < 0 ? "error.main" : "success.main",
                                            }}>
                                                {trade.active ? "" : tightPlus(formatPercent(trade.profitPercentage, true, 1))}
                                            </Box>
                                        </Box>
                                    ))}
                                </Box>
                                {/* What the case has made so far, kept per currency: a case can
                                    cover companies that do not share one. */}
                                <Box component="tfoot">
                                    {profitByCurrency(value.trades).map(([currency, profit]) => (
                                        <Box component="tr" key={currency} data-testid={`case-profit-${currency}`}>
                                            <Box component="td" colSpan={6} sx={{
                                                ...TRADE_CELL,
                                                borderTop: "1px solid",
                                                borderColor: "divider",
                                            }}/>
                                            {/* Under the profit column, where the column already
                                                says what it is. */}
                                            <Box component="td" sx={{
                                                ...TRADE_CELL,
                                                textAlign: "right",
                                                fontWeight: 600,
                                                borderTop: "1px solid",
                                                borderColor: "divider",
                                                color: profit < 0 ? "error.main" : "success.main",
                                            }}>
                                                {formatDecimals(profit, 2, 2) + currency}
                                            </Box>
                                            <Box component="td" sx={{
                                                ...TRADE_CELL,
                                                borderTop: "1px solid",
                                                borderColor: "divider",
                                            }}/>
                                        </Box>
                                    ))}
                                </Box>
                            </Box>
                            </Box>
                        </Paper>
                    }

                    <Paper elevation={1} sx={{padding: "10px 12px", marginTop: "10px"}}>
                        <CaseEntryList
                            entries={entries}
                            trades={tradeLines}
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
                        {entries.length === 0 &&
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
                open={Boolean(movedEntry)}
                title="Day written"
                initialDate={movedEntry?.date ?? ""}
                onSubmit={date => moveEntry(movedEntry.id, date)}
                handleClose={() => setMovedEntry(null)}
            />
            <CaseNameDialog
                open={renaming}
                title="Rename case"
                initialName={value?.name ?? ""}
                onSubmit={newName => save({name: newName})}
                handleClose={() => setRenaming(false)}
            />
                }
            />
            <SnackbarErrorAlert error={alert} open={alert !== null} onClose={() => setAlert(null)}/>
        </>
    )
}
