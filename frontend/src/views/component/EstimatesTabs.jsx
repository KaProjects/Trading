import {Box} from "@mui/material";
import React from "react";

/**
 * The two estimate widgets hold the same five windows in the same shape, so on a wide screen a
 * company that has both can show them in one widget with a tab each rather than stacking two
 * near-identical rows. These are the tabs: they sit where the widget's title normally goes.
 *
 * They live inside the widget's own button, so a click has to be stopped from also opening the
 * projections dialog behind them.
 */
export const EstimatesTabs = ({tabs, active, onChange}) => (
    <Box sx={{display: "flex", alignItems: "center", gap: "14px"}}>
        {tabs.map(tab => {
            const selected = tab.key === active;

            return (
                <Box
                    key={tab.key}
                    component="span"
                    role="tab"
                    aria-selected={selected}
                    data-testid={`estimates-tab-${tab.key}`}
                    onClick={event => {
                        event.stopPropagation();
                        if (!selected) onChange(tab.key);
                    }}
                    sx={{
                        cursor: selected ? "default" : "pointer",
                        fontWeight: selected ? 600 : 400,
                        color: selected ? "text.primary" : "text.disabled",
                        borderBottom: "2px solid",
                        borderColor: selected ? "warning.main" : "transparent",
                        paddingBottom: "1px",
                        "&:hover": {color: selected ? "text.primary" : "text.secondary"},
                    }}
                >
                    {tab.title}
                </Box>
            );
        })}
    </Box>
);
