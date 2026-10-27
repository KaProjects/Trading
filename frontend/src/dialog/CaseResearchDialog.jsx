import {Box, Button, Dialog, DialogActions, DialogContent, DialogTitle} from "@mui/material";
import axios from "axios";
import React, {useEffect, useState} from "react";
import {backend} from "../properties";
import {GoToResearch} from "../views/component/GoToResearch";
import {ContentEditor} from "../views/component/ContentEditor";
import {ContentImages} from "../views/component/ContentImages";
import {formatDate, formatError} from "../service/FormattingService";

/**
 * A day of a company's research, read where the case mentions it. Like a record, it belongs to
 * the company's own page: it opens here to be read, and is written where it lives.
 */
export const CaseResearchDialog = ({open, contentId, ticker, handleClose}) => {
    const [content, setContent] = useState(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!open || !contentId) return;

        setContent(null);
        setError(null);
        axios.get(`${backend}/period/content/entry/${contentId}`)
            .then(response => setContent(response.data))
            .catch(reason => setError(formatError(reason)));
    }, [open, contentId]);

    return (
        <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
            <DialogTitle sx={{fontSize: 16, paddingBottom: "4px"}}>
                {[ticker, content ? formatDate(content.date) : ""].filter(Boolean).join(" ")}
            </DialogTitle>
            <DialogContent data-testid="case-research-content">
                {error && <Box sx={{fontSize: 13, color: "error.main"}}>{error.message}</Box>}
                {!content && !error && <Box sx={{fontSize: 13, color: "text.secondary"}}>Reading it…</Box>}
                {content &&
                    <Box sx={{"& ul, & ol": {margin: 0}}}>
                        <ContentEditor content={content.content} update={() => null} locked/>
                        {content.images?.length > 0 &&
                            <Box sx={{marginTop: "8px"}}>
                                <ContentImages images={content.images} compact/>
                            </Box>
                        }
                    </Box>
                }
            </DialogContent>
            <DialogActions>
                <GoToResearch ticker={ticker}/>
                <Button onClick={handleClose}>Close</Button>
            </DialogActions>
        </Dialog>
    )
}
