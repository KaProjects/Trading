import {Button} from "@mui/material";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import React from "react";
import {useNavigate} from "react-router-dom";

/** Where a company's own page is, with that company already picked. */
export const researchPath = ticker => `/research?company=${encodeURIComponent(ticker)}`;

/**
 * The way out of a case and into the company itself. What a case reads about a company is the
 * company's own - this goes to where it is kept, and where it can be written.
 */
export const GoToResearch = ({ticker, label = "Go to research"}) => {
    const navigate = useNavigate();

    if (!ticker) return null;

    return (
        <Button
            aria-label={`Go to ${ticker} research`}
            startIcon={<ArrowForwardIcon sx={{fontSize: 16}}/>}
            onClick={() => navigate(researchPath(ticker))}
        >
            {label}
        </Button>
    )
};
