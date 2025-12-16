import {recordEvent} from "../service/utils";

test("recordEvent", () => {
    const nameUsed = "nameUsed";
    localStorage.setItem(nameUsed, "10")

    recordEvent(nameUsed)

    expect(Number(localStorage.getItem(nameUsed))).toEqual(11)

    const nameNew = "nameNew"

    recordEvent(nameNew)

    expect(Number(localStorage.getItem(nameNew))).toEqual(1)
})