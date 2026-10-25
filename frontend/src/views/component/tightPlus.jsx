import {Box} from "@mui/material";
import React from "react";

/**
 * A leading plus sits a little away from the figure it belongs to. The sign itself is not the
 * problem - its glyph nearly fills its advance - it is the digit after it: in a tabular face
 * every digit is centred in a cell of the same width, so a narrow one like "1" leaves about a
 * pixel of air on its left. Pulling the sign in by that much closes the gap. A minus is drawn
 * much narrower than a digit and already sits close, so it is left alone.
 */
export const tightPlus = text => {
    if (typeof text !== "string" || text.length < 2 || text.charAt(0) !== "+") return text;

    return (
        <>
            <Box component="span" sx={{letterSpacing: "-0.08em"}}>+</Box>
            {text.slice(1)}
        </>
    );
};
