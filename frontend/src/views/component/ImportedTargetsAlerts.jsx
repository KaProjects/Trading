import {Alert, AlertTitle, Box} from "@mui/material";
import React, {useEffect} from "react";
import {formatDecimals} from "../../service/FormattingService";

/** Long enough to read a quarter's worth of institutions, short enough not to sit there. */
const DISMISS_AFTER_MS = 20000;

const TargetsAlert = ({group, currency, onDismiss}) => {
    useEffect(() => {
        const timer = setTimeout(() => onDismiss(group.periodId), DISMISS_AFTER_MS);
        return () => clearTimeout(timer);
    }, [group.periodId, onDismiss]);

    return (
        <Alert
            data-testid={`imported-targets-${group.periodId}`}
            severity="success"
            variant="filled"
            onClose={() => onDismiss(group.periodId)}
            sx={{
                width: "100%",
                boxSizing: "border-box",
                alignItems: "flex-start",
                "& .MuiAlert-message": {minWidth: 0, width: "100%"},
            }}
        >
            <AlertTitle sx={{marginBottom: "2px"}}>
                {`New targets for ${group.periodName}`}
            </AlertTitle>
            {/* A period can bring in two dozen of these, so the list scrolls rather than
                filling the screen. */}
            <Box sx={{maxHeight: "30vh", overflowY: "auto", fontSize: 13}}>
                {group.targets.map((target, index) => (
                    <Box
                        key={`${target.date}-${target.institution}-${index}`}
                        sx={{display: "flex", gap: "8px", justifyContent: "space-between"}}
                    >
                        <Box sx={{overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap"}}>
                            {target.institution}
                        </Box>
                        <Box sx={{flexShrink: 0}}>
                            {formatDecimals(Number(target.price), 2, 2) + (currency ?? "")}
                        </Box>
                    </Box>
                ))}
            </Box>
        </Alert>
    );
};

/**
 * What an automatic target import brought in, one card per period. It sits where a notification
 * would: under the todo column on a wide screen, across the foot of a narrow one.
 */
export const ImportedTargetsAlerts = ({groups = [], currency, onDismiss}) => {
    if (groups.length === 0) return null;

    return (
        <Box
            data-testid="imported-targets-alerts"
            sx={{
                position: "fixed",
                zIndex: theme => theme.zIndex.snackbar,
                bottom: {xs: 0, sm: "16px"},
                right: {xs: 0, sm: "16px"},
                left: {xs: 0, sm: "auto"},
                width: {xs: "100%", sm: "360px"},
                maxWidth: "100%",
                padding: {xs: "8px", sm: 0},
                boxSizing: "border-box",
                display: "flex",
                flexDirection: "column",
                gap: "8px",
            }}
        >
            {groups.map(group => (
                <TargetsAlert
                    key={group.periodId}
                    group={group}
                    currency={currency}
                    onDismiss={onDismiss}
                />
            ))}
        </Box>
    );
};
