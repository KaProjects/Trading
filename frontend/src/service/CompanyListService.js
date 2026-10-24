/** A list the app builds itself; the underscore marks it as a debug aid, not a tag. */
export const HAS_PERIOD_LIST = "_has-period"

export const COMPANY_LIST_TITLES = {
    owned: "Owned",
    actionable: "Actionable",
    recent: "Recent",
    all: "All",
    // Keeps its underscore on screen: the name is the point, it says this is not a tag.
    [HAS_PERIOD_LIST]: HAS_PERIOD_LIST,
}

const BUILT_IN_LIST_KEYS = ["owned", "actionable", "recent"]
const CUSTOM_LIST_ORDER = BUILT_IN_LIST_KEYS.length
const ALL_LIST_ORDER = BUILT_IN_LIST_KEYS.length + 1
const DEBUG_LIST_ORDER = BUILT_IN_LIST_KEYS.length + 2

function listOrder(key) {
    if (key.startsWith("_")) return DEBUG_LIST_ORDER
    if (key === "all") return ALL_LIST_ORDER
    const index = BUILT_IN_LIST_KEYS.indexOf(key)
    return index === -1 ? CUSTOM_LIST_ORDER : index
}

export function getCompanyListKeys(companyLists = {}) {
    return Object.keys(companyLists)
        .sort((first, second) => listOrder(first) - listOrder(second) || first.localeCompare(second))
}

export function getCompanyListTitle(listKey) {
    return COMPANY_LIST_TITLES[listKey] ?? listKey
}
