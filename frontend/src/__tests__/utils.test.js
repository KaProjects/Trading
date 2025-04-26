import {handleError, recordEvent, validateNumber, validateQuarter, validateShares, validateTicker} from "../utils";

test("validateQuarter", () => {
    expect(validateQuarter(333)).toBe("not a string");
    expect(validateQuarter(undefined)).toBe("not a string");
    expect(validateQuarter(null)).toBe("not a string");

    expect(validateQuarter("")).toBe("non empty");

    expect(validateQuarter("2025Q1")).toContain("invalid format");

    expect(validateQuarter("A5Q1")).toContain("invalid format");

    expect(validateQuarter("25Q5")).toContain("invalid format");
    expect(validateQuarter("25H3")).toContain("invalid format");
    expect(validateQuarter("25Y1")).toContain("invalid format");

    expect(validateQuarter("20Q1")).toBe("");
    expect(validateQuarter("21Q4")).toBe("");
    expect(validateQuarter("22H1")).toBe("");
    expect(validateQuarter("23H2")).toBe("");
    expect(validateQuarter("24YY")).toBe("");
})

test("recordEvent", () => {
    const nameUsed = "nameUsed";
    localStorage.setItem(nameUsed, "10")

    recordEvent(nameUsed)

    expect(Number(localStorage.getItem(nameUsed))).toEqual(11)

    const nameNew = "nameNew"

    recordEvent(nameNew)

    expect(Number(localStorage.getItem(nameNew))).toEqual(1)
})

test("handleError", () => {
    const aMessage = "message"
    let error = {message: aMessage}

    expect(handleError(error)).toBe(aMessage)

    error.response = {}
    expect(handleError(error)).toBe(aMessage)

    error.response.data = {}
    expect(handleError(error)).toBe(aMessage)

    error.response.data = 11
    expect(handleError(error)).toBe(aMessage)

    const aData = "data"
    error.response.data = aData
    expect(handleError(error)).toBe(aData)
})

test("validateNumber", () => {
    expect(validateNumber(333)).toBe("not a string");
    expect(validateNumber(undefined)).toBe("not a string");
    expect(validateNumber(null)).toBe("not a string");

    expect(validateNumber("", false)).toBe("non empty");
    expect(validateNumber("", true)).toBe("");

    expect(validateNumber("aa", false)).toBe("not a valid number");
    expect(validateNumber("10a0", false)).toBe("not a valid number");
    expect(validateNumber("10.0.0", false)).toBe("not a valid number");
    expect(validateNumber("10.", false)).toBe("not a valid number");
    expect(validateNumber(".1", false)).toBe("not a valid number");

    expect(validateNumber("12345.1", false, 6, 2)).toBe("max length 4");
    expect(validateNumber("1.123", false, 6, 2)).toBe("max decimal 2");

    expect(validateNumber("1234.12", false, 6, 2)).toBe("");
})

test("validateTicker", () => {
    expect(validateTicker(333)).toBe("not a string");
    expect(validateTicker(undefined)).toBe("not a string");
    expect(validateTicker(null)).toBe("not a string");

    expect(validateTicker("")).toBe("non empty");

    expect(validateTicker("ABCDEF")).toBe("max length 5");

    expect(validateTicker("NvDA")).toBe("only uppercase");

    expect(validateTicker("NVDA")).toBe("");
})

test("validateShares", () => {
    expect(validateShares(333)).toBe("not a string");
    expect(validateShares(undefined)).toBe("not a string");
    expect(validateShares(null)).toBe("not a string");

    expect(validateShares("")).toBe("");

    expect(validateShares("12345678")).toBe("max length 7");

    expect(validateShares("135")).toContain("invalid format");

    expect(validateShares("B")).toBe("non empty");
    expect(validateShares("1234B")).toBe("max length 3");
    expect(validateShares("1.123B")).toBe("max decimal 2");

    expect(validateShares("123.12B")).toBe("");
})
