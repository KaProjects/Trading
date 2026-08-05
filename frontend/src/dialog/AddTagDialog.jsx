import React, {useEffect, useState} from "react";
import axios from "axios";
import {
    Alert,
    AlertTitle,
    Autocomplete,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    TextField,
} from "@mui/material";
import {backend} from "../properties";
import {formatError} from "../service/FormattingService";
import {COMPANY_LIST_TITLES} from "../service/CompanyListService";

const MAX_TAG_LENGTH = 30;

export const AddTagDialog = props => {
    const [tag, setTag] = useState("");
    const [alert, setAlert] = useState(null);
    const normalizedTag = tag.toLocaleLowerCase();
    const currentTags = props.currentTags ?? [];
    const containsWhitespace = /\s/.test(tag);
    const alreadyAssigned = tag.length > 0
        && currentTags.some(currentTag => currentTag.toLocaleLowerCase() === normalizedTag);
    const reserved = tag.length > 0
        && Object.prototype.hasOwnProperty.call(COMPANY_LIST_TITLES, normalizedTag);
    const valid = tag.length > 0
        && tag.length <= MAX_TAG_LENGTH
        && !containsWhitespace
        && !alreadyAssigned
        && !reserved;
    const suggestions = (props.suggestions ?? []).filter(suggestion => {
        const normalizedSuggestion = suggestion.toLocaleLowerCase();
        return !currentTags.some(currentTag => currentTag.toLocaleLowerCase() === normalizedSuggestion)
            && !Object.prototype.hasOwnProperty.call(COMPANY_LIST_TITLES, normalizedSuggestion);
    });

    useEffect(() => {
        if (props.open) {
            setTag("");
            setAlert(null);
        }
    }, [props.open]);

    function addTag() {
        if (!valid) return;

        axios.post(backend + "/company/tag", {
            companyId: props.companyId,
            value: tag,
        }).then(() => {
            props.triggerRefresh();
            props.handleClose();
        }).catch(error => setAlert(formatError(error)));
    }

    return (
        <Dialog
            open={props.open}
            onClose={props.handleClose}
            slotProps={{
                paper: {
                    component: "form",
                    onSubmit: event => {
                        event.preventDefault();
                        addTag();
                    },
                },
            }}
        >
            <DialogTitle>Add Tag</DialogTitle>
            <DialogContent>
                <Autocomplete
                    freeSolo
                    options={suggestions}
                    inputValue={tag}
                    onInputChange={(_, value) => {
                        setTag(value);
                        setAlert(null);
                    }}
                    renderInput={params => (
                        <TextField
                            {...params}
                            autoFocus
                            required
                            fullWidth
                            margin="dense"
                            variant="standard"
                            label="Tag"
                            error={tag.length > MAX_TAG_LENGTH || containsWhitespace || alreadyAssigned || reserved}
                            helperText={tag.length > MAX_TAG_LENGTH
                                ? `Maximum ${MAX_TAG_LENGTH} characters`
                                : containsWhitespace ? "Tag must not contain spaces or tabs"
                                    : alreadyAssigned ? "Tag is already assigned to this company"
                                        : reserved ? "Tag name is reserved" : ""}
                            slotProps={{htmlInput: {...params.inputProps, maxLength: MAX_TAG_LENGTH + 1}}}
                        />
                    )}
                />
            </DialogContent>
            {alert &&
                <Alert severity="error" variant="filled">
                    <AlertTitle>{alert.title}</AlertTitle>{alert.message}
                </Alert>
            }
            <DialogActions>
                <Button onClick={props.handleClose}>Cancel</Button>
                <Button type="submit" disabled={!valid}>Add</Button>
            </DialogActions>
        </Dialog>
    );
};
