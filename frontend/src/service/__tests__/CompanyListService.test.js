import {getCompanyListKeys, getCompanyListTitle} from "../CompanyListService";

describe("CompanyListService", () => {
    test("orders the app's own lists first, then tags, then all, then the debug lists", () => {
        const keys = getCompanyListKeys({
            all: [],
            semis: [],
            owned: [],
            "_has-period": [],
            actionable: [],
            banks: [],
            recent: [],
        });

        expect(keys).toEqual([
            "owned", "actionable", "recent", "banks", "semis", "all", "_has-period",
        ]);
    });

    test("keeps the underscore on a debug list's name and falls back to the key for a tag", () => {
        expect(getCompanyListTitle("_has-period")).toBe("_has-period");
        expect(getCompanyListTitle("owned")).toBe("Owned");
        expect(getCompanyListTitle("semis")).toBe("semis");
    });
});
