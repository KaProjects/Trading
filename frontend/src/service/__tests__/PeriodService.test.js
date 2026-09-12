import {endingMonthWarning, nextPeriod, periodNameError, periodNameWarning, reportDateError} from "../PeriodService";

describe("nextPeriod", () => {
    test("advances a quarter within the same fiscal year", () => {
        expect(nextPeriod({name: {year: "2025", type: "Q3"}, endingMonth: "2025-07"}))
            .toEqual({name: "25Q4", endingMonth: "2025-10"});
    });

    test("wraps the fiscal year after the fourth quarter", () => {
        expect(nextPeriod({name: {year: "2026", type: "Q4"}, endingMonth: "2026-01"}))
            .toEqual({name: "27Q1", endingMonth: "2026-04"});
    });

    test("crosses the calendar year in the ending month", () => {
        expect(nextPeriod({name: {year: "2025", type: "Q4"}, endingMonth: "2025-10"}))
            .toEqual({name: "26Q1", endingMonth: "2026-01"});
    });

    test("advances half years by six months", () => {
        expect(nextPeriod({name: {year: "2025", type: "H1"}, endingMonth: "2025-06"}))
            .toEqual({name: "25H2", endingMonth: "2025-12"});
        expect(nextPeriod({name: {year: "2025", type: "H2"}, endingMonth: "2025-12"}))
            .toEqual({name: "26H1", endingMonth: "2026-06"});
    });

    test("advances full years by twelve months", () => {
        expect(nextPeriod({name: {year: "2025", type: "FY"}, endingMonth: "2025-12"}))
            .toEqual({name: "26FY", endingMonth: "2026-12"});
    });

    test("accepts the short period name form", () => {
        expect(nextPeriod({name: "25Q1", endingMonth: "2025-03"}))
            .toEqual({name: "25Q2", endingMonth: "2025-06"});
    });

    test("returns nothing without a usable latest period", () => {
        expect(nextPeriod(undefined)).toBeNull();
        expect(nextPeriod({})).toBeNull();
        expect(nextPeriod({name: {year: "2025", type: "XX"}, endingMonth: "2025-12"})).toBeNull();
    });

    test("keeps the name when the ending month cannot be shifted", () => {
        expect(nextPeriod({name: {year: "2025", type: "Q1"}}))
            .toEqual({name: "25Q2", endingMonth: ""});
    });
});

describe("periodNameError", () => {
    const periods = [{name: {year: "2025", type: "Q3"}}, {name: {year: "2025", type: "Q2"}}];

    test("accepts every supported period type", () => {
        ["25Q1", "25Q4", "25H1", "25H2", "25FY"].forEach(name =>
            expect(periodNameError(name, [])).toBe(""));
    });

    test("rejects quarters and half years outside their range", () => {
        ["25Q0", "25Q5", "25Q9", "25H0", "25H3", "25FX", "25XX"].forEach(name =>
            expect(periodNameError(name, [])).toBe("expected Q1-Q4, H1, H2 or FY, e.g. 25FY, 25Q1, ..."));
    });

    test("rejects names of the wrong length or shape", () => {
        ["", "5Q1", "2025Q1", "Q125", "a5Q1"].forEach(name =>
            expect(periodNameError(name, [])).not.toBe(""));
    });

    test("rejects a name that already exists for the company", () => {
        expect(periodNameError("25Q2", periods)).toBe("period already exists for this company");
        expect(periodNameError("25Q4", periods)).toBe("");
    });
});

describe("periodNameWarning", () => {
    const latest = {name: {year: "2025", type: "Q3"}, endingMonth: "2025-07"};

    test("stays silent for the consecutive period", () => {
        expect(periodNameWarning("25Q4", latest)).toBe("");
    });

    test("warns for a period that skips ahead or goes back", () => {
        expect(periodNameWarning("26Q2", latest)).toBe("does not follow 25Q3, expected 25Q4");
        expect(periodNameWarning("24Q1", latest)).toBe("does not follow 25Q3, expected 25Q4");
    });

    test("stays silent without a latest period", () => {
        expect(periodNameWarning("25Q4", undefined)).toBe("");
    });
});

describe("endingMonthWarning", () => {
    const latest = {name: {year: "2026", type: "Q4"}, endingMonth: "2026-01"};

    test("stays silent when the consecutive period keeps the cadence", () => {
        expect(endingMonthWarning("27Q1", "2026-04", latest)).toBe("");
    });

    test("warns when the consecutive period breaks the cadence", () => {
        expect(endingMonthWarning("27Q1", "2027-04", latest)).toBe("expected 2026-04 for 27Q1");
    });

    test("stays silent for a period that is not consecutive", () => {
        expect(endingMonthWarning("28Q1", "2030-01", latest)).toBe("");
    });
});

describe("reportDateError", () => {
    test("rejects a report date inside or before the ending month", () => {
        expect(reportDateError("2026-04-30", "2026-04")).toBe("must be after the ending month");
        expect(reportDateError("2026-03-15", "2026-04")).toBe("must be after the ending month");
    });

    test("accepts a report date after the ending month", () => {
        expect(reportDateError("2026-05-01", "2026-04")).toBe("");
    });

    test("stays silent when either value is missing", () => {
        expect(reportDateError("", "2026-04")).toBe("");
        expect(reportDateError("2026-05-01", "")).toBe("");
    });
});
