import {Box, Button, Dialog, DialogActions, DialogContent, DialogTitle} from "@mui/material";
import axios from "axios";
import React, {useEffect, useState} from "react";
import {backend} from "../properties";
import {GoToResearch} from "../views/component/GoToResearch";
import {PeriodFinancialSummary} from "../views/component/PeriodFinancialSummary";
import {formatDate, formatError, formatPeriodName} from "../service/FormattingService";

/**
 * What a company reported on a day the case covers it, shown exactly as its own page shows it:
 * the same block of figures, read-only, where the case can see it without leaving.
 */
export const CaseReportDialog = ({open, periodId, ticker, handleClose}) => {
    const [report, setReport] = useState(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!open || !periodId) return;

        setReport(null);
        setError(null);
        axios.get(`${backend}/period/${periodId}`)
            .then(response => setReport(response.data))
            .catch(reason => setError(formatError(reason)));
    }, [open, periodId]);

    const period = report?.period;

    return (
        <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
            <DialogTitle sx={{fontSize: 16, paddingBottom: "4px"}}>
                {[report?.ticker ?? ticker, period ? formatPeriodName(period.name) : ""]
                    .filter(Boolean).join(" ")}
                {period?.reportDate &&
                    <Box component="span" sx={{fontSize: 13, color: "text.secondary", marginLeft: "8px"}}>
                        {`reported ${formatDate(period.reportDate)}`}
                    </Box>
                }
            </DialogTitle>
            <DialogContent data-testid="case-report-financials">
                {error && <Box sx={{fontSize: 13, color: "error.main"}}>{error.message}</Box>}
                {!report && !error && <Box sx={{fontSize: 13, color: "text.secondary"}}>Reading it…</Box>}
                {period &&
                    <PeriodFinancialSummary
                        financial={period.financial}
                        shares={period.shares}
                        priceHigh={period.priceHigh}
                        priceLow={period.priceLow}
                        currency={report.currency}
                    />
                }
            </DialogContent>
            <DialogActions>
                <GoToResearch ticker={report?.ticker ?? ticker}/>
                <Button onClick={handleClose}>Close</Button>
            </DialogActions>
        </Dialog>
    )
}
