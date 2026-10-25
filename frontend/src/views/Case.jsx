import React, {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router-dom";
import {
    Autocomplete,
    Box,
    Button,
    Chip,
    Paper,
    Stack,
    TextField,
    Tooltip,
    Typography,
    useMediaQuery,
} from "@mui/material";
import DeleteIcon from "@mui/icons-material/DeleteOutline";
import PostAddIcon from "@mui/icons-material/PostAdd";
import axios from "axios";
import {useData} from "../service/BackendService";
import {Loader} from "./component/Loader";
import {SnackbarErrorAlert} from "./component/SnackbarErrorAlert";
import {CaseEntryList, hasEntryForToday, today} from "./component/CaseEntryList";
import {useStagedImages} from "./component/useStagedImages";
import {ContentEditorDialog} from "../dialog/ContentEditorDialog";
import {ContentImages} from "./component/ContentImages";
import {SWIPE_NAV_BREAKPOINT} from "./component/MainBar";
import {backend} from "../properties";
import {formatDate, formatError} from "../service/FormattingService";

/**
 * One case: what it is called, the companies it covers, and the days written into it. A case is
 * kept forwards - the only day that can be opened is today - so the entries carry no date
 * picker; what the day holds is written into it for as long as it is still today.
 */
export const Case = props => {
    const {caseId} = useParams()
    const navigate = useNavigate()
    const compact = useMediaQuery(`(max-width:${SWIPE_NAV_BREAKPOINT}px)`)
    const [refresh, setRefresh] = useState("")
    const {data, loaded, error} = useData(`/case/${caseId}${refresh ? "?refresh=" + refresh : ""}`)
    const [alert, setAlert] = useState(null)
    const [name, setName] = useState(null)
    const [entries, setEntries] = useState([])
    const [editedEntry, setEditedEntry] = useState(null)
    const [openEditor, setOpenEditor] = useState(false)

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

    function removeCase() {
        axios.delete(`${backend}/case/${caseId}`)
            .then(() => navigate("/cases"))
            .catch(reason => setAlert(formatError(reason)))
    }

    /** Today, once: a day the case already holds is written into where it stands. */
    function addEntry() {
        if (compact) {
            setEditedEntry(null)
            setOpenEditor(true)
            return
        }
        setEntries(previous => [{id: null, date: today(), content: null, images: []}, ...previous])
    }

    const written = hasEntryForToday(entries)

    return (
        <>
            {!loaded && <Loader error={error}/>}
            {loaded &&
                <Box sx={{width: {xs: "100%", sm: "700px"}, margin: "10px auto"}}>
                    <Paper elevation={1} sx={{padding: "10px 12px"}}>
                        <Stack direction="row" alignItems="center" spacing={1}>
                            <TextField
                                variant="standard"
                                fullWidth
                                inputProps={{"aria-label": "Case name"}}
                                value={name ?? value.name}
                                onChange={event => setName(event.target.value)}
                                onBlur={() => {
                                    if (name !== null && name.trim() && name.trim() !== value.name) {
                                        save({name: name.trim()})
                                    }
                                    setName(null)
                                }}
                            />
                            {!written &&
                                <Tooltip title="Write today">
                                    <Button size="small" aria-label="Write today" onClick={addEntry}>
                                        <PostAddIcon sx={{color: "success.main"}}/>
                                    </Button>
                                </Tooltip>
                            }
                            <Button
                                size="small"
                                variant="outlined"
                                onClick={() => save({closed: !value.closedAt})}
                            >
                                {value.closedAt ? "Reopen" : "Close"}
                            </Button>
                            <Tooltip title="Delete case">
                                <Button size="small" color="error" aria-label="Delete case" onClick={removeCase}>
                                    <DeleteIcon/>
                                </Button>
                            </Tooltip>
                        </Stack>

                        <Typography sx={{fontSize: 12, color: "text.secondary", marginTop: "2px"}}>
                            {`opened ${formatDate(value.createdAt)}`}
                            {value.closedAt ? `, closed ${formatDate(value.closedAt)}` : ""}
                        </Typography>

                        <Stack direction="row" spacing={1} sx={{marginTop: "10px", flexWrap: "wrap", gap: "4px"}}>
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
                        </Stack>

                        <Autocomplete
                            size="small"
                            sx={{marginTop: "10px", maxWidth: "260px"}}
                            options={companies.filter(company =>
                                !value.companies.some(each => each.id === company.id))}
                            getOptionLabel={company => company.ticker}
                            value={null}
                            onChange={(event, company) => company && save({
                                companyIds: [...value.companies.map(each => each.id), company.id],
                            })}
                            renderInput={params => <TextField {...params} label="Add company"/>}
                        />
                    </Paper>

                    <Paper elevation={1} sx={{padding: "10px 12px", marginTop: "10px"}}>
                        <CaseEntryList
                            entries={entries}
                            compact={compact}
                            stagedFor={stagedFor}
                            onSave={saveEntry}
                            onStageImage={stageImage}
                            onDropStagedImage={dropStagedImage}
                            onMarkImageRemoved={markImageRemoved}
                            onCommitImages={commitStaged}
                            onDiscardImages={discardStaged}
                            onOpenEditor={entry => {setEditedEntry(entry);setOpenEditor(true)}}
                        />
                        {entries.length === 0 &&
                            <Typography sx={{color: "text.secondary", fontSize: 13}}>
                                Nothing written yet.
                            </Typography>
                        }
                    </Paper>
                </Box>
            }

            <ContentEditorDialog
                open={openEditor}
                content={editedEntry?.content}
                title={[value?.name, formatDate(editedEntry?.date ?? today())].filter(Boolean).join(" ")}
                handleClose={() => {setOpenEditor(false);setEditedEntry(null)}}
                save={content => saveEntry(editedEntry?.date ?? today(), content)}
                onSubmit={() => (editedEntry ? commitStaged(editedEntry.id) : null)}
                onCancel={() => editedEntry && discardStaged(editedEntry.id)}
                footer={editedEntry &&
                    <ContentImages
                        images={entries.find(entry => entry.id === editedEntry.id)?.images ?? []}
                        pending={stagedFor(editedEntry.id).adds}
                        removedIds={stagedFor(editedEntry.id).removes}
                        compact
                        editing
                        onAdd={file => stageImage(editedEntry.id, file)}
                        onDropPending={key => dropStagedImage(editedEntry.id, key)}
                        onMarkRemoved={(imageId, removed) => markImageRemoved(editedEntry.id, imageId, removed)}
                    />
                }
            />
            <SnackbarErrorAlert error={alert} open={alert !== null} onClose={() => setAlert(null)}/>
        </>
    )
}
