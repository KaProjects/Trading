import {Box} from "@mui/material";
import React from "react";

const SENTIMENT_ORDER = ["positive", "neutral", "mixed", "negative", "missing"];
const SENTIMENT_COLORS = {
    positive: "#2e7d32",
    neutral: "#607d8b",
    mixed: "#ed6c02",
    negative: "#d32f2f",
    missing: "#9e9e9e",
};

function displayLabel(value) {
    return value
        .replaceAll("_", " ")
        .replace(/^./, character => character.toUpperCase());
}

function entries(stats) {
    return Object.entries(stats ?? {})
        .filter(([, count]) => Number(count) > 0)
        .sort(([left], [right]) => {
            const leftIndex = SENTIMENT_ORDER.indexOf(left.toLowerCase());
            const rightIndex = SENTIMENT_ORDER.indexOf(right.toLowerCase());
            const normalizedLeft = leftIndex === -1 ? SENTIMENT_ORDER.length : leftIndex;
            const normalizedRight = rightIndex === -1 ? SENTIMENT_ORDER.length : rightIndex;
            return normalizedLeft - normalizedRight || left.localeCompare(right);
        });
}

export const SentimentBreakdown = ({stats, total}) => {
    const values = entries(stats);

    if (values.length === 0) {
        return <Box sx={{color: "text.disabled", fontSize: 11}}>No classified articles</Box>;
    }

    return (
        <Box data-testid="sentiment-breakdown" sx={{minWidth: 0}}>
            <Box
                aria-label={`Sentiment distribution across ${total} articles`}
                sx={{display: "flex", width: "100%", height: "5px", borderRadius: "3px", overflow: "hidden"}}
            >
                {values.map(([label, count]) => (
                    <Box
                        key={label}
                        title={`${displayLabel(label)}: ${count}`}
                        sx={{
                            flexGrow: count,
                            flexBasis: 0,
                            minWidth: "2px",
                            bgcolor: SENTIMENT_COLORS[label.toLowerCase()] ?? "#1976d2",
                        }}
                    />
                ))}
            </Box>
            <Box sx={{display: "flex", flexWrap: "wrap", columnGap: "10px", rowGap: "2px", marginTop: "3px"}}>
                {values.map(([label, count]) => (
                    <Box
                        component="span"
                        key={label}
                        sx={{display: "inline-flex", alignItems: "center", gap: "4px", color: "text.secondary", fontSize: 11}}
                    >
                        <Box
                            component="span"
                            sx={{
                                width: "6px",
                                height: "6px",
                                borderRadius: "50%",
                                bgcolor: SENTIMENT_COLORS[label.toLowerCase()] ?? "#1976d2",
                            }}
                        />
                        {displayLabel(label)} {count}
                    </Box>
                ))}
            </Box>
        </Box>
    );
};
