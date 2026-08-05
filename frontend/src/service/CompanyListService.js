export const COMPANY_LIST_TITLES = {
    owned: "Owned",
    recent: "Recent",
    researched: "Researched",
    all: "All",
}

const BUILT_IN_LIST_KEYS = ["owned", "recent", "researched"]

export function getCompanyListKeys(companyLists = {}) {
    return Object.keys(companyLists).sort((first, second) => {
        const firstIndex = BUILT_IN_LIST_KEYS.indexOf(first)
        const secondIndex = BUILT_IN_LIST_KEYS.indexOf(second)
        const firstOrder = first === "all" ? 4 : firstIndex === -1 ? 3 : firstIndex
        const secondOrder = second === "all" ? 4 : secondIndex === -1 ? 3 : secondIndex

        return firstOrder - secondOrder || first.localeCompare(second)
    })
}

export function getCompanyListTitle(listKey) {
    return COMPANY_LIST_TITLES[listKey] ?? listKey
}
