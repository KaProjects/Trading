import {SWIPE_NAV_BREAKPOINT} from "./MainBar";

/** What the app calls a narrow screen everywhere else. */
export const NARROW = `@media (max-width:${SWIPE_NAV_BREAKPOINT}px)`;

/**
 * A company of a case, wherever it is read. A phone gives the companies about half the height
 * a desktop does: a row of chips at the usual size takes a tenth of the screen before a word of
 * the case has been read.
 */
export const COMPANY_CHIP = {
    [NARROW]: {
        height: "14px",
        fontSize: 10,
        "& .MuiChip-label": {paddingLeft: "5px", paddingRight: "4px"},
        "& .MuiChip-deleteIcon": {fontSize: 12, marginRight: "2px", marginLeft: "-3px"},
    },
};
