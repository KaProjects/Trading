import React, {useEffect, useState} from "react";
import axios from "axios";
import {
    Alert,
    AlertTitle,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    TextField,
} from "@mui/material";
import {backend} from "../properties";
import {formatError} from "../service/FormattingService";

export const AddTodoDialog = props => {
    const editing = Boolean(props.todo);
    const [content, setContent] = useState("");
    const [alert, setAlert] = useState(null);
    const contentValid = content.trim().length > 0;

    useEffect(() => {
        if (props.open) {
            setContent(props.todo?.content ?? "");
            setAlert(null);
        }
    }, [props.open, props.todo]);

    function saveTodo() {
        if (!contentValid) return;

        const payload = {
            content: content.trim(),
        };
        const request = editing
            ? axios.put(backend + "/todo/" + props.todo.id, payload)
            : axios.post(backend + "/todo", payload);

        request.then(response => {
            if (editing) {
                props.onUpdated(response.data);
            } else {
                props.onCreated(response.data);
            }
            props.handleClose();
        }).catch(error => setAlert(formatError(error)));
    }

    return (
        <Dialog
            open={props.open}
            onClose={props.handleClose}
            fullWidth
            maxWidth="sm"
            slotProps={{
                paper: {
                    component: "form",
                    onSubmit: event => {
                        event.preventDefault();
                        saveTodo();
                    },
                },
            }}
        >
            <DialogTitle>{editing ? "Edit Todo" : "Add Todo"}</DialogTitle>
            <DialogContent>
                <TextField
                    autoFocus
                    required
                    fullWidth
                    multiline
                    minRows={3}
                    margin="dense"
                    variant="standard"
                    label="Task"
                    value={content}
                    onChange={event => {
                        setContent(event.target.value);
                        setAlert(null);
                    }}
                    error={content.length > 0 && !contentValid}
                    helperText={content.length > 0 && !contentValid ? "Task must not be blank" : "Use #TICKER to link a company"}
                />
            </DialogContent>
            {alert &&
                <Alert severity="error" variant="filled">
                    <AlertTitle>{alert.title}</AlertTitle>{alert.message}
                </Alert>
            }
            <DialogActions>
                <Button onClick={props.handleClose}>Cancel</Button>
                <Button type="submit" disabled={!contentValid}>{editing ? "Save" : "Add"}</Button>
            </DialogActions>
        </Dialog>
    );
};
