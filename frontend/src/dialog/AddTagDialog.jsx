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

const MAX_TAG_LENGTH = 30;

export const AddTagDialog = props => {
    const [tag, setTag] = useState("");
    const [alert, setAlert] = useState(null);
    const containsWhitespace = /\s/.test(tag);
    const valid = tag.length > 0 && tag.length <= MAX_TAG_LENGTH && !containsWhitespace;

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
                    options={props.suggestions ?? []}
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
                            error={tag.length > MAX_TAG_LENGTH || containsWhitespace}
                            helperText={tag.length > MAX_TAG_LENGTH
                                ? `Maximum ${MAX_TAG_LENGTH} characters`
                                : containsWhitespace ? "Tag must not contain spaces or tabs" : ""}
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
