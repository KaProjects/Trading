import {Alert, AlertTitle, Box, Button, Dialog, DialogActions, DialogContent} from "@mui/material";
import React, {useEffect, useState} from "react";
import {ContentEditor} from "../views/component/ContentEditor";

/**
 * @param onSubmit anything else the dialog is carrying, saved on Save even when the text itself
 *                 was left alone
 * @param onCancel the same, put back when the dialog is closed without saving
 */
export const ContentEditorDialog = ({open, content, title, handleClose, save, header, footer, onSubmit, onCancel}) => {
    const [draft, setDraft] = useState(null)
    const [alert, setAlert] = useState(null)
    const [saving, setSaving] = useState(false)

    useEffect(() => {
        if (open) {
            setDraft(null)
            setAlert(null)
            setSaving(false)
        }
    }, [open])

    async function handleSave() {
        if (saving) return

        setSaving(true)
        const error = (onSubmit ? await onSubmit() : null)
            ?? (draft === null ? null : await save(draft))
        setSaving(false)
        if (error) {
            setAlert(error)
        } else {
            handleClose()
        }
    }

    function handleCancel() {
        onCancel?.()
        handleClose()
    }

    return (
        <Dialog open={open} onClose={handleClose} fullScreen>
            {title &&
                <Box
                    data-testid="content-editor-title"
                    sx={{color: "text.secondary", fontSize: 12, fontWeight: 600, padding: "6px 10px 0 10px"}}
                >
                    {title}
                </Box>
            }
            <DialogContent sx={{display: "flex", flexDirection: "column", padding: 1, overflow: "hidden"}}>
                {/* Anything the entry needs above its text - the day a new one is written for. */}
                {open && header}
                {open &&
                    <ContentEditor
                        content={content}
                        alwaysEditing
                        autoSave={false}
                        onValueChange={setDraft}
                        style={{flex: "1 1 auto", minHeight: 0, overflowY: "auto"}}
                    />
                }
                {/* Whatever belongs with the text while it is being written - the day's
                    pictures, where the entry has them - sits under the editor. */}
                {open && footer}
            </DialogContent>
            {alert &&
                <Alert severity="error" variant="filled">
                    <AlertTitle>{alert.title}</AlertTitle>{alert.message}
                </Alert>
            }
            <DialogActions>
                <Button onClick={handleCancel} disabled={saving}>Cancel</Button>
                <Button onClick={handleSave} disabled={saving}>Save</Button>
            </DialogActions>
        </Dialog>
    )
}
