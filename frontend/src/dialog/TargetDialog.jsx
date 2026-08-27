import {
    Alert,
    AlertTitle,
    Badge,
    Box,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    IconButton,
    Paper,
    Stack,
    TextField,
    Tooltip,
    Typography,
} from "@mui/material";
import CloudDownloadIcon from "@mui/icons-material/CloudDownload";
import DeleteIcon from "@mui/icons-material/Delete";
import axios from "axios";
import React, {useEffect, useState} from "react";
import {backend} from "../properties";
import {formatDate, formatDecimals, formatError, formatPeriodName} from "../service/FormattingService";
import {validateDate, validateNumber} from "../service/ValidationService";

const EMPTY_TARGET = {
    date: "",
    institution: "",
    price: "",
    rating: "",
    overview: "",
    takeaway1: "",
    takeaway2: "",
    takeaway3: "",
    takeaway4: "",
};

const MAX_LENGTHS = {
    institution: 50,
    rating: 30,
    overview: 1000,
    takeaway1: 500,
    takeaway2: 500,
    takeaway3: 500,
    takeaway4: 500,
};

const optional = value => value.trim() === "" ? null : value.trim();
const uniqueWarnings = warnings => [...new Set(warnings.filter(Boolean))];

function shiftMonths(value, offset) {
    if (!value) return null;

    const [year, month, day] = value.split("-").map(Number);
    const totalMonths = year * 12 + month - 1 + offset;
    const targetYear = Math.floor(totalMonths / 12);
    const targetMonthIndex = totalMonths - targetYear * 12;
    const lastDay = new Date(Date.UTC(targetYear, targetMonthIndex + 1, 0)).getUTCDate();
    return [
        targetYear,
        String(targetMonthIndex + 1).padStart(2, "0"),
        String(Math.min(day, lastDay)).padStart(2, "0"),
    ].join("-");
}

function previousDay(value) {
    const date = new Date(`${value}T00:00:00Z`);
    date.setUTCDate(date.getUTCDate() - 1);
    return date.toISOString().substring(0, 10);
}

function dateWindow(period) {
    const type = typeof period?.name === "string"
        ? period.name.slice(-2)
        : period?.name?.type;
    const months = type === "FY" ? 12 : type === "H1" || type === "H2" ? 6 : 3;
    const start = period?.previousReportDate || shiftMonths(period?.reportDate, -months);
    const end = period?.reportDate || shiftMonths(period?.previousReportDate, months);
    return start && end && start < end ? {start, end} : null;
}

export const TargetDialog = ({open, handleClose, triggerRefresh, company, period}) => {
    const [mode, setMode] = useState("list");
    const [targets, setTargets] = useState([]);
    const [target, setTarget] = useState(EMPTY_TARGET);
    const [candidateCount, setCandidateCount] = useState(0);
    const [warnings, setWarnings] = useState([]);
    const [loadingTargets, setLoadingTargets] = useState(false);
    const [loadingCount, setLoadingCount] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [alert, setAlert] = useState(null);
    const [submitted, setSubmitted] = useState(false);
    const [highlightedTargetIds, setHighlightedTargetIds] = useState(new Set());
    const targetDateWindow = dateWindow(period);

    useEffect(() => {
        if (!open || !period) return;

        setTargets([]);
        setTarget(EMPTY_TARGET);
        setCandidateCount(0);
        setWarnings([]);
        setAlert(null);
        setSubmitted(false);
        setHighlightedTargetIds(new Set());
        setMode("list");
        loadTargets();
        loadCandidateCount();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open, period?.id]);

    function loadTargets() {
        setLoadingTargets(true);
        return axios.get(`${backend}/target/${period.id}`)
            .then(response => {
                const loadedTargets = response.data ?? [];
                setTargets(loadedTargets);
                return loadedTargets;
            })
            .catch(error => setAlert(formatError(error)))
            .finally(() => setLoadingTargets(false));
    }

    function highlightTargets(targetIds) {
        setHighlightedTargetIds(previous => new Set([
            ...previous,
            ...targetIds.filter(targetId => targetId !== null && targetId !== undefined),
        ]));
    }

    function loadCandidateCount(existingWarnings = []) {
        setLoadingCount(true);
        return axios.get(`${backend}/target/${period.id}/sync/count`)
            .then(response => {
                setCandidateCount(response.data?.count ?? 0);
                setWarnings(uniqueWarnings([
                    ...existingWarnings,
                    ...(response.data?.warnings ?? []),
                ]));
            })
            .catch(error => setAlert(formatError(error)))
            .finally(() => setLoadingCount(false));
    }

    function update(field, value) {
        setTarget(previous => ({...previous, [field]: value}));
        setAlert(null);
    }

    function startAdding() {
        setTarget(EMPTY_TARGET);
        setSubmitted(true);
        setAlert(null);
        setMode("add");
    }

    function stopAdding() {
        setTarget(EMPTY_TARGET);
        setSubmitted(false);
        setAlert(null);
        setMode("list");
    }

    function priceError() {
        const validation = validateNumber(target.price, false, 10, 4, false);
        if (validation) return validation;
        return Number(target.price) > 0 ? "" : "must be greater than zero";
    }

    function fieldError(field) {
        if (field === "date") {
            if (!target.date) return "not filled";
            const validation = validateDate(target.date, true, true);
            if (validation) return validation;
            if (!targetDateWindow) return "period date range unavailable";
            if (target.date < targetDateWindow.start || target.date >= targetDateWindow.end) {
                return `must be from ${targetDateWindow.start} to ${previousDay(targetDateWindow.end)}`;
            }
            return "";
        }
        if (field === "institution") {
            if (!target.institution.trim()) return "not filled";
            return target.institution.length > MAX_LENGTHS.institution
                ? `max length ${MAX_LENGTHS.institution}`
                : "";
        }
        if (field === "price") return priceError();
        const maxLength = MAX_LENGTHS[field];
        return target[field].length > maxLength ? `max length ${maxLength}` : "";
    }

    function fieldHelperText(field) {
        const error = fieldError(field);
        if (submitted && error) return error;

        return target[field] ? `${target[field].length}/${MAX_LENGTHS[field]}` : " ";
    }

    function createTarget() {
        setSubmitted(true);
        const fields = ["date", "institution", "price", "rating", "overview", "takeaway1", "takeaway2", "takeaway3", "takeaway4"];
        if (fields.some(field => fieldError(field))) return;

        setSubmitting(true);
        axios.post(`${backend}/target/${period.id}`, {
            date: target.date,
            institution: target.institution.trim(),
            price: target.price,
            rating: optional(target.rating),
            overview: optional(target.overview),
            takeaway1: optional(target.takeaway1),
            takeaway2: optional(target.takeaway2),
            takeaway3: optional(target.takeaway3),
            takeaway4: optional(target.takeaway4),
        })
            .then(response => {
                highlightTargets([response.data?.id]);
                setTarget(EMPTY_TARGET);
                setSubmitted(false);
                setMode("list");
                triggerRefresh();
                return Promise.all([loadTargets(), loadCandidateCount()]);
            })
            .catch(error => setAlert(formatError(error)))
            .finally(() => setSubmitting(false));
    }

    function syncTargets() {
        const existingTargetIds = new Set(targets.map(item => item.id));
        setSubmitting(true);
        setAlert(null);
        axios.post(`${backend}/target/${period.id}/sync`)
            .then(response => {
                const syncWarnings = response.data?.warnings ?? [];
                if ((response.data?.count ?? 0) > 0) triggerRefresh();
                return Promise.all([loadTargets(), loadCandidateCount(syncWarnings)])
                    .then(([loadedTargets]) => highlightTargets(
                        (loadedTargets ?? [])
                            .filter(item => !existingTargetIds.has(item.id))
                            .map(item => item.id)
                    ));
            })
            .catch(error => setAlert(formatError(error)))
            .finally(() => setSubmitting(false));
    }

    function deleteTarget(targetId) {
        setSubmitting(true);
        setAlert(null);
        axios.delete(`${backend}/target/${targetId}`)
            .then(() => {
                setHighlightedTargetIds(previous => {
                    const remaining = new Set(previous);
                    remaining.delete(targetId);
                    return remaining;
                });
                triggerRefresh();
                return Promise.all([loadTargets(), loadCandidateCount()]);
            })
            .catch(error => setAlert(formatError(error)))
            .finally(() => setSubmitting(false));
    }

    const takeaways = item => [item.takeaway1, item.takeaway2, item.takeaway3, item.takeaway4].filter(Boolean);

    return (
        <Dialog open={open} onClose={handleClose} fullWidth maxWidth="md">
            <DialogTitle>
                {mode === "add" ? "Add target for" : "Targets for"} {company?.ticker} {period ? formatPeriodName(period.name) : ""}
            </DialogTitle>
            <DialogContent sx={{display: "flex", flexDirection: "column", gap: 2}}>
                {mode === "list" && warnings.length > 0 &&
                    <Alert severity="warning">
                        <AlertTitle>Some expected target data could not be loaded</AlertTitle>
                        {warnings.map(warning => <Box key={warning}>{warning}</Box>)}
                    </Alert>
                }
                {alert &&
                    <Alert severity="error" variant="filled">
                        <AlertTitle>{alert.title}</AlertTitle>{alert.message}
                    </Alert>
                }

                {mode === "add" &&
                    <Box
                        component="form"
                        id="target-form"
                        noValidate
                        onSubmit={event => {
                            event.preventDefault();
                            createTarget();
                        }}
                        sx={{
                            display: "grid",
                            gridTemplateColumns: {xs: "1fr", sm: "145px minmax(180px, 1fr) 130px 150px"},
                            paddingTop: "6px",
                            columnGap: 1.5,
                            rowGap: "5px",
                            alignItems: "start",
                        }}
                    >
                    <TextField
                        type="date"
                        label="Target date"
                        required
                        value={target.date}
                        onChange={event => update("date", event.target.value)}
                        error={submitted && Boolean(fieldError("date"))}
                        helperText={submitted ? fieldError("date") : " "}
                        slotProps={{
                            inputLabel: {shrink: true},
                            htmlInput: targetDateWindow
                                ? {min: targetDateWindow.start, max: previousDay(targetDateWindow.end)}
                                : {},
                        }}
                    />
                    <TextField
                        label="Institution"
                        required
                        value={target.institution}
                        onChange={event => update("institution", event.target.value)}
                        error={submitted && Boolean(fieldError("institution"))}
                        helperText={fieldHelperText("institution")}
                        slotProps={{htmlInput: {maxLength: MAX_LENGTHS.institution + 1}}}
                    />
                    <TextField
                        label="Price"
                        required
                        value={target.price}
                        onChange={event => update("price", event.target.value)}
                        error={submitted && Boolean(fieldError("price"))}
                        helperText={submitted ? fieldError("price") : " "}
                    />
                    <TextField
                        label="Rating"
                        value={target.rating}
                        onChange={event => update("rating", event.target.value)}
                        error={submitted && Boolean(fieldError("rating"))}
                        helperText={fieldHelperText("rating")}
                        slotProps={{htmlInput: {maxLength: MAX_LENGTHS.rating + 1}}}
                    />
                    <TextField
                        label="Overview"
                        value={target.overview}
                        onChange={event => update("overview", event.target.value)}
                        error={submitted && Boolean(fieldError("overview"))}
                        helperText={fieldHelperText("overview")}
                        multiline
                        minRows={2}
                        sx={{gridColumn: {xs: "auto", sm: "1 / -1"}}}
                        slotProps={{htmlInput: {maxLength: MAX_LENGTHS.overview + 1}}}
                    />
                    {[1, 2, 3, 4].map(number => {
                        const field = `takeaway${number}`;
                        return (
                            <TextField
                                key={field}
                                label={`Takeaway ${number}`}
                                value={target[field]}
                                onChange={event => update(field, event.target.value)}
                                error={submitted && Boolean(fieldError(field))}
                                helperText={fieldHelperText(field)}
                                multiline
                                sx={{gridColumn: "1 / -1"}}
                                slotProps={{htmlInput: {maxLength: MAX_LENGTHS[field] + 1}}}
                            />
                        );
                    })}
                    </Box>
                }

                {mode === "list" &&
                    <>
                <Box sx={{display: "flex", alignItems: "center", justifyContent: "space-between"}}>
                    <Typography color="text.secondary">Saved targets</Typography>
                    <Tooltip title="Import new targets from Firebase">
                        <span>
                            <IconButton
                                aria-label="Sync targets"
                                onClick={syncTargets}
                                disabled={submitting || loadingCount || candidateCount < 1}
                            >
                                {loadingCount
                                    ? <CircularProgress size={22}/>
                                    : <Badge badgeContent={candidateCount} color="primary">
                                        <CloudDownloadIcon/>
                                    </Badge>
                                }
                            </IconButton>
                        </span>
                    </Tooltip>
                </Box>

                {loadingTargets &&
                    <Box sx={{display: "flex", justifyContent: "center", minHeight: 100, alignItems: "center"}}>
                        <CircularProgress/>
                    </Box>
                }
                {!loadingTargets && targets.length === 0 &&
                    <Typography color="text.secondary">No targets saved for this period.</Typography>
                }
                {!loadingTargets &&
                    <Stack spacing={1}>
                        {targets.map(item => (
                            <Paper
                                key={item.id}
                                data-testid={`target-${item.id}`}
                                data-highlighted={highlightedTargetIds.has(item.id)}
                                variant="outlined"
                                sx={{
                                    padding: 1.5,
                                    borderColor: highlightedTargetIds.has(item.id) ? "success.main" : undefined,
                                    backgroundColor: highlightedTargetIds.has(item.id)
                                        ? "rgba(46, 125, 50, 0.10)"
                                        : undefined,
                                    transition: "background-color 180ms ease-in-out, border-color 180ms ease-in-out",
                                    "& .deleteTarget": {
                                        opacity: 1,
                                        transition: "opacity 120ms ease-in-out",
                                    },
                                    "@media (hover: hover)": {
                                        "& .deleteTarget": {
                                            opacity: 0,
                                            pointerEvents: "none",
                                        },
                                        "&:hover .deleteTarget, &:focus-within .deleteTarget": {
                                            opacity: 1,
                                            pointerEvents: "auto",
                                        },
                                    },
                                }}
                            >
                                <Box sx={{display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 1}}>
                                    <Box>
                                        <Typography sx={{fontWeight: 600}}>
                                            {item.institution} | {company?.currency}{formatDecimals(item.price, 2, 2)}
                                        </Typography>
                                        <Typography sx={{fontSize: 12, color: "text.secondary"}}>
                                            {formatDate(item.date)}{item.rating ? ` | ${item.rating}` : ""}
                                        </Typography>
                                    </Box>
                                    <Tooltip title="Delete target">
                                        <IconButton
                                            className="deleteTarget"
                                            aria-label={`Delete target ${item.institution}`}
                                            color="error"
                                            size="small"
                                            disabled={submitting}
                                            onClick={() => deleteTarget(item.id)}
                                        >
                                            <DeleteIcon fontSize="small"/>
                                        </IconButton>
                                    </Tooltip>
                                </Box>
                                {item.overview && <Typography sx={{marginTop: 1, fontSize: 14}}>{item.overview}</Typography>}
                                {takeaways(item).length > 0 &&
                                    <Box component="ul" sx={{marginY: 0.5, paddingLeft: 3}}>
                                        {takeaways(item).map((takeaway, index) => (
                                            <Typography component="li" key={`${item.id}-${index}`} sx={{fontSize: 13}}>
                                                {takeaway}
                                            </Typography>
                                        ))}
                                    </Box>
                                }
                            </Paper>
                        ))}
                    </Stack>
                }
                    </>
                }
            </DialogContent>
            <DialogActions>
                {mode === "add"
                    ? <>
                        <Button onClick={stopAdding} disabled={submitting}>Back</Button>
                        <Button
                            type="submit"
                            form="target-form"
                            variant="contained"
                            disabled={submitting}
                        >
                            Add
                        </Button>
                    </>
                    : <>
                        <Button onClick={startAdding}>Add Target</Button>
                        <Button onClick={handleClose}>Close</Button>
                    </>
                }
            </DialogActions>
        </Dialog>
    );
};
