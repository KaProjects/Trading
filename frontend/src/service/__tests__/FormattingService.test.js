import {
    formatDate,
    formatDecimals,
    formatError,
    formatMillions,
    formatMillionsRounded,
    formatTargetStats,
    formatPercent,
    formatPeriodName,
    isNotAValue,
    orBlank
} from "../FormattingService";

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
        expect(formatMillions(1111235)).toBe("1.11T");
        expect(formatMillions(-123)).toBe("-123M");
        expect(formatMillions(-1234)).toBe("-1.23B");
        expect(formatMillions(-1235)).toBe("-1.24B");

        expect(formatMillions(1000000)).toBe("1T");
        expect(formatMillions(5200000)).toBe("5.2T");
        expect(formatMillions(4324567)).toBe("4.32T");
        expect(formatMillions(999999)).toBe("1,000B");
        expect(formatMillions(-5200000)).toBe("-5.2T");
    })

    test("formatMillionsRounded", () => {
        expect(formatMillionsRounded(undefined)).toBe("");
        expect(formatMillionsRounded(null)).toBe("");
        expect(formatMillionsRounded("")).toBe("");
        expect(formatMillionsRounded("123")).toBe("");

        expect(formatMillionsRounded(9.456)).toBe("9.46M");
        expect(formatMillionsRounded(46.78)).toBe("46.8M");
        expect(formatMillionsRounded(875.4)).toBe("875M");
        expect(formatMillionsRounded(9750)).toBe("9.75B");
        expect(formatMillionsRounded(46740)).toBe("46.7B");
        expect(formatMillionsRounded(108450)).toBe("108B");
        expect(formatMillionsRounded(253490)).toBe("253B");
        expect(formatMillionsRounded(-46740)).toBe("-46.7B");
        expect(formatMillionsRounded(-9750)).toBe("-9.75B");

        expect(formatMillionsRounded(5200000)).toBe("5.2T");
        expect(formatMillionsRounded(4324567)).toBe("4.32T");
        expect(formatMillionsRounded(45600000)).toBe("45.6T");
        expect(formatMillionsRounded(-5200000)).toBe("-5.2T");
    })

    test("formatTargetStats", () => {
        expect(formatTargetStats(null)).toBe("");
        expect(formatTargetStats({count: 0})).toBe("");
        expect(formatTargetStats({count: 5, minimum: 120, average: 145.5, maximum: 175}))
            .toBe("5@(175-120)~146");
        expect(formatTargetStats({count: 3, minimum: 9.44, average: 9.75, maximum: 10}))
            .toBe("3@(10-9.4)~9.8");
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

        expect(formatPercent(1.2, true)).toBe("+1.2%");
        expect(formatPercent(-1.2, true)).toBe("-1.2%");
        expect(formatPercent(1.23456, false, 3)).toBe("1.235%");
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

    test("formatError", () => {
        const expected = {title: "Unexpected Error:", message: ""}
        expect(formatError(undefined)).toStrictEqual(expected);
        expect(formatError(null)).toStrictEqual(expected);
        expect(formatError("")).toStrictEqual(expected);
        expect(formatError({})).toStrictEqual(expected);
        expect(formatError([])).toStrictEqual(expected);

        let error = "abcd"
        expected.message = JSON.stringify(error)
        expect(formatError(error)).toStrictEqual(expected);

        error = {dunno: "abcdef"}
        expected.message = JSON.stringify(error)
        expect(formatError(error)).toStrictEqual(expected);

        const expectedTitle = "ab asdaew dsad 4"
        error = {name: "AxiosError", message: expectedTitle}
        expected.title = expectedTitle
        expected.message = ""
        expect(formatError(error)).toStrictEqual(expected);

        let expectedMessage = "nklaDe tails 55"
        error.response = {data: expectedMessage}
        expected.message = expectedMessage
        expect(formatError(error)).toStrictEqual(expected);

        error.response.data = 1234
        expected.message =  JSON.stringify(error.response.data)
        expect(formatError(error)).toStrictEqual(expected);

        error.response.data = {aa: "abcd"}
        expected.message =  JSON.stringify(error.response.data)
        expect(formatError(error)).toStrictEqual(expected);

        error.response.data = {details: expectedMessage}
        expected.message =  expectedMessage
        expect(formatError(error)).toStrictEqual(expected);

        expectedMessage = "asdasd asd, asdasd asd "
        error.response.data = {details: expectedMessage}
        expected.message =  expectedMessage.split(', ')[1]
        expect(formatError(error)).toStrictEqual(expected);

        expectedMessage = "saeadgasrdas"
        error.response.data = {title: expectedMessage, violations: [{message: "sadgkhjkds asd "}]}
        expected.message =  expectedMessage
        expect(formatError(error)).toStrictEqual(expected);
    })

    test("orBlank", () => {
        expect(orBlank(undefined)).toBe("");
        expect(orBlank(null)).toBe("");
        expect(orBlank("")).toBe("");
        expect(orBlank(0)).toBe("");
        expect(orBlank(false)).toBe("");

        expect(orBlank("abc")).toBe("abc");
        expect(orBlank(123)).toBe(123);
        expect(orBlank(true)).toBe(true);
    })
});
