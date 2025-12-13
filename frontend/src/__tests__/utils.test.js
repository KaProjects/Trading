import {handleError, recordEvent} from "../service/utils";

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

test("recordEvent", () => {
    const nameUsed = "nameUsed";
    localStorage.setItem(nameUsed, "10")

    recordEvent(nameUsed)

    expect(Number(localStorage.getItem(nameUsed))).toEqual(11)

    const nameNew = "nameNew"

    recordEvent(nameNew)

    expect(Number(localStorage.getItem(nameNew))).toEqual(1)
})