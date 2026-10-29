import {Box, Button, Dialog, DialogActions, DialogContent, DialogTitle} from "@mui/material";
import axios from "axios";
import React, {useEffect, useState} from "react";
import {backend} from "../properties";
import {GoToResearch} from "../views/component/GoToResearch";
import {ContentEditor} from "../views/component/ContentEditor";
import {formatDate, formatError} from "../service/FormattingService";

const SECTIONS = [
    ["Content", "content"],
    ["Review", "review"],
    ["Strategy", "strategy"],
    ["Retro", "retro"],
];

const hasText = value => value !== null && value !== undefined && value !== "";

/**
 * A record of one of the case's companies, read where the case mentions it. It is the
 * company's own writing, not the case's: it opens here to be read and is changed where it
 * lives, on the company's page.
 */
export const CaseRecordDialog = ({open, recordId, ticker, handleClose}) => {
    const [record, setRecord] = useState(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!open || !recordId) return;

        setRecord(null);
        setError(null);
        axios.get(`${backend}/record/${recordId}`)
            .then(response => setRecord(response.data))
            .catch(reason => setError(formatError(reason)));
    }, [open, recordId]);

    return (
        <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
            <DialogTitle sx={{fontSize: 16, paddingBottom: "4px"}}>
                {[ticker, record ? formatDate(record.date) : ""].filter(Boolean).join(" ")}
                {record?.title && <Box component="span" sx={{
                    fontSize: 13,
                    color: "text.secondary",
                    marginLeft: "8px",
                }}>{record.title}</Box>}
            </DialogTitle>
            <DialogContent>
                {error && <Box sx={{fontSize: 13, color: "error.main"}}>{error.message}</Box>}
                {!record && !error && <Box sx={{fontSize: 13, color: "text.secondary"}}>Reading it…</Box>}
                {record &&
                    <>
                        {SECTIONS.filter(([, field]) => hasText(record[field])).map(([label, field]) => (
                            <Box key={field} data-testid={`case-record-${field}`} sx={{marginBottom: "8px"}}>
                                <Box sx={{
                                    color: "text.secondary",
                                    fontSize: 13,
                                    fontWeight: 600,
                                    textDecoration: "underline",
                                }}>
                                    {`${label}:`}
                                </Box>
                                {/* Locked: the record is read here and written where it lives. */}
                                <ContentEditor content={record[field]} update={() => null} locked/>
                            </Box>
                        ))}
                    </>
                }
            </DialogContent>
            <DialogActions>
                <GoToResearch ticker={ticker}/>
                <Button onClick={handleClose}>Close</Button>
            </DialogActions>
        </Dialog>
    )
}
