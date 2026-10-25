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

/**
 * Figures are set in Roboto with tabular figures switched on, which fixes every digit to the
 * same width so a column lines up on the decimal point. A monospaced family would do that too,
 * but it also widens the separators - a full character cell for a decimal point or a comma
 * pushes the digits apart and makes the numbers look gappy. Tabular figures leave the
 * punctuation at its natural, narrower width.
 *
 * Roboto rather than the system face because its tabular digits are the narrower ones: in the
 * system face a "1" sits in the middle of a wide cell, so "11" and "+11%" read as separate
 * marks rather than one number. Only figures take this face; the rest of the app is unchanged.
 * The font itself is loaded in public/index.html.
 */
export const NUMERIC_FONT = {
    fontFamily: '"Roboto", -apple-system, "system-ui", "Segoe UI", sans-serif',
    fontVariantNumeric: "tabular-nums",
    fontFeatureSettings: '"tnum" 1',
};
