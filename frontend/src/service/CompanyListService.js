export const COMPANY_LIST_TITLES = {
    owned: "Owned",
    actionable: "Actionable",
    recent: "Recent",
    all: "All",
}

const BUILT_IN_LIST_KEYS = ["owned", "actionable", "recent"]
const CUSTOM_LIST_ORDER = BUILT_IN_LIST_KEYS.length
const ALL_LIST_ORDER = BUILT_IN_LIST_KEYS.length + 1

export function getCompanyListKeys(companyLists = {}) {
    return Object.keys(companyLists).sort((first, second) => {
        const firstIndex = BUILT_IN_LIST_KEYS.indexOf(first)
        const secondIndex = BUILT_IN_LIST_KEYS.indexOf(second)
        const firstOrder = first === "all" ? ALL_LIST_ORDER : firstIndex === -1 ? CUSTOM_LIST_ORDER : firstIndex
        const secondOrder = second === "all" ? ALL_LIST_ORDER : secondIndex === -1 ? CUSTOM_LIST_ORDER : secondIndex

        return firstOrder - secondOrder || first.localeCompare(second)
    })
}

export function getCompanyListTitle(listKey) {
    return COMPANY_LIST_TITLES[listKey] ?? listKey
}
