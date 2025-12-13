import {
    formatDate,
    formatDecimals,
    formatMillions,
    formatPercent,
    formatPeriodName, isNotAValue
} from "../service/FormattingService";

describe('FormattingService', () => {
    beforeAll(() => {console.error = () => {};});

    test("formatPeriodName", () => {
        expect(formatPeriodName(undefined)).toBe("");
        expect(formatPeriodName(null)).toBe("");
        expect(formatPeriodName("")).toBe("");
        expect(formatPeriodName({})).toBe("");
        expect(formatPeriodName("abc")).toBe("");
        expect(formatPeriodName(123)).toBe("");
        expect(formatPeriodName({year: 1, type: "FY"})).toBe("");
        expect(formatPeriodName({year: "2025", type: 1})).toBe("");
        expect(formatPeriodName({year: "2025", type: "FYa"})).toBe("");
        expect(formatPeriodName({year: "25", type: "FY"})).toBe("");
        expect(formatPeriodName({year: "2025", type: "FY"})).toBe("25FY");
    })

    test("formatMillions", () => {
        expect(formatMillions(undefined)).toBe("");
        expect(formatMillions(null)).toBe("");
        expect(formatMillions("")).toBe("");
        expect(formatMillions({})).toBe("");
        expect(formatMillions("abc")).toBe("");
        expect(formatMillions({aaa: "aaa"})).toBe("");
        expect(formatMillions("123")).toBe("");

        expect(formatMillions(123)).toBe("123M");
        expect(formatMillions(1234)).toBe("1.23B");
        expect(formatMillions(1235)).toBe("1.24B");
        expect(formatMillions(1111235)).toBe("1,111.24B");
    })

    test("formatDecimals", () => {
        expect(formatDecimals(undefined)).toBe("");
        expect(formatDecimals(null)).toBe("");
        expect(formatDecimals("")).toBe("");
        expect(formatDecimals({})).toBe("");
        expect(formatDecimals("abc")).toBe("");
        expect(formatDecimals({aaa: "aaa"})).toBe("");
        expect(formatDecimals("123")).toBe("");

        expect(formatDecimals(123, null, 1)).toBe("");
        expect(formatDecimals(123, "", 1)).toBe("");
        expect(formatDecimals(123, {}, 1)).toBe("");
        expect(formatDecimals(123, "abc", 1)).toBe("");
        expect(formatDecimals(123, {aaa: "aaa"}, 1)).toBe("");
        expect(formatDecimals(123, "123", 1)).toBe("");

        expect(formatDecimals(123, 1, null)).toBe("");
        expect(formatDecimals(123, 1, "")).toBe("");
        expect(formatDecimals(123, 1, {})).toBe("");
        expect(formatDecimals(123, 1, "abc")).toBe("");
        expect(formatDecimals(123, 1, {aaa: "aaa"})).toBe("");
        expect(formatDecimals(123, 1, "123")).toBe("");

        expect(formatDecimals(10.1234, undefined, undefined)).toBe("10");
        expect(formatDecimals(10.1234, 2, 3)).toBe("10.123");
        expect(formatDecimals(10.1, 2, 3)).toBe("10.10");
        expect(formatDecimals(10.1234, undefined, 3)).toBe("10.123");
        expect(formatDecimals(10, undefined, 3)).toBe("10");

        expect(formatDecimals(10.1234, 3, 2)).toBe("");
        expect(formatDecimals(10.1234, 3, undefined)).toBe("");
    })

    test("formatDate", () => {
        expect(formatDate(undefined)).toBe("");
        expect(formatDate(null)).toBe("");
        expect(formatDate("")).toBe("");
        expect(formatDate({})).toBe("");
        expect(formatDate(123)).toBe("");
        expect(formatDate({aaa: "aaa"})).toBe("");
        expect(formatDate("abc")).toBe("");

        expect(formatDate("01-01-2025")).toBe("");
        expect(formatDate("2025-01-1")).toBe("");
        expect(formatDate("1.1.2025")).toBe("");

        expect(formatDate("2025-01-01")).toBe("01.01.2025");
    })

    test("formatPercent", () => {
        expect(formatPercent(undefined)).toBe("");
        expect(formatPercent(null)).toBe("");
        expect(formatPercent("")).toBe("");
        expect(formatPercent({})).toBe("");
        expect(formatPercent({aaa: "aaa"})).toBe("");
        expect(formatPercent("abc")).toBe("");
        expect(formatPercent("123")).toBe("");

        expect(formatPercent(123)).toBe("123%");
        expect(formatPercent(1234)).toBe("1,234%");
        expect(formatPercent(12.3)).toBe("12.3%");
        expect(formatPercent(1.23456)).toBe("1.23%");
        expect(formatPercent(1.23956)).toBe("1.24%");
    })

    test("isNotAValue", () => {
        expect(isNotAValue(undefined)).toBe(true);
        expect(isNotAValue(null)).toBe(true);
        expect(isNotAValue("")).toBe(true);
        expect(isNotAValue({})).toBe(true);
        expect(isNotAValue([])).toBe(true);

        expect(isNotAValue({aaa: "aaa"})).toBe(false);
        expect(isNotAValue("abc")).toBe(false);
        expect(isNotAValue(123)).toBe(false);
        expect(isNotAValue([123])).toBe(false);
    })
});