const APP_CONTENT_PADDING_XS = 8
const TABLE_EDGE_GAP_XS = 1
const EDGE_BLEED_XS = APP_CONTENT_PADDING_XS - TABLE_EDGE_GAP_XS

export const TABLE_CONTAINER_SX = {
    width: {xs: `calc(100% + ${EDGE_BLEED_XS * 2}px)`, sm: "max-content"},
    maxWidth: {xs: `calc(100% + ${EDGE_BLEED_XS * 2}px)`, sm: "unset"},
    margin: {xs: `${TABLE_EDGE_GAP_XS}px -${EDGE_BLEED_XS}px`, sm: "10px auto"},
}

export const STICKY_COLUMN_BODY_SX = {
    position: "sticky",
    left: 0,
    zIndex: 1,
    backgroundColor: "background.paper",
    borderRight: "1px solid",
    borderRightColor: "divider",
}

export const STICKY_COLUMN_HEAD_SX = {
    position: "sticky",
    left: 0,
    zIndex: 3,
    backgroundColor: "background.default",
    borderRight: "1px solid",
    borderRightColor: "divider",
}

export const STICKY_FIRST_COLUMN_STYLE = {
    position: "sticky",
    left: 0,
    zIndex: 2,
    backgroundColor: "inherit",
}

export const STICKY_FIRST_COLUMN_HEAD_STYLE = {
    position: "sticky",
    left: 0,
    zIndex: 4,
}
