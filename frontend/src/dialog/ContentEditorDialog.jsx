import {Alert, AlertTitle, Button, Dialog, DialogActions, DialogContent} from "@mui/material";
import React, {useEffect, useState} from "react";
import {ContentEditor} from "../views/component/ContentEditor";

export const ContentEditorDialog = ({open, content, handleClose, save}) => {
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
        if (draft === null) {
            handleClose()
            return
        }

        setSaving(true)
        const error = await save(draft)
        setSaving(false)
        if (error) {
            setAlert(error)
        } else {
            handleClose()
        }
    }

    return (
        <Dialog open={open} onClose={handleClose} fullScreen>
            <DialogContent sx={{display: "flex", flexDirection: "column", padding: 1, overflow: "hidden"}}>
                {open &&
                    <ContentEditor
                        content={content}
                        alwaysEditing
                        autoSave={false}
                        onValueChange={setDraft}
                        style={{flex: "1 1 auto", minHeight: 0, overflowY: "auto"}}
                    />
                }
            </DialogContent>
            {alert &&
                <Alert severity="error" variant="filled">
                    <AlertTitle>{alert.title}</AlertTitle>{alert.message}
                </Alert>
            }
            <DialogActions>
                <Button onClick={handleClose} disabled={saving}>Cancel</Button>
                <Button onClick={handleSave} disabled={saving}>Save</Button>
            </DialogActions>
        </Dialog>
    )
}
