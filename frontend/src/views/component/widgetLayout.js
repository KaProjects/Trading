/**
 * The research page stacks several summary widgets, each one an icon followed by a row of
 * columns. These constants are what keep that stack looking like one component rather than
 * four similar ones.
 */

/** Icon column: the icon itself plus the gap to the values beside it. */
export const WIDGET_ICON_SIZE = 17;
export const WIDGET_ICON_GAP = "7px";

/**
 * Minimum width of a summary row's first column. The columns are centred, so without a shared
 * floor the leading value sits wherever its own label happens to leave it - "revenue" is much
 * wider than "ttm", which pushed the financials value out of line with the estimates below it.
 */
export const WIDGET_FIRST_COLUMN_WIDTH = 48;
