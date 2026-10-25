import {formatPeriodName} from "./FormattingService";

const PERIOD_NAME_PATTERN = /^\d\d(Q[1-4]|H[12]|FY)$/;

const PERIOD_STEPS = {
    Q1: {next: "Q2", months: 3, wraps: false},
    Q2: {next: "Q3", months: 3, wraps: false},
    Q3: {next: "Q4", months: 3, wraps: false},
    Q4: {next: "Q1", months: 3, wraps: true},
    H1: {next: "H2", months: 6, wraps: false},
    H2: {next: "H1", months: 6, wraps: true},
    FY: {next: "FY", months: 12, wraps: true},
};

function periodParts(name) {
    if (typeof name === "string" && name.length === 4) {
        return {year: name.substring(0, 2), type: name.substring(2, 4)};
    }
    if (name && typeof name.year === "string" && typeof name.type === "string") {
        return {year: name.year.substring(name.year.length - 2), type: name.type};
    }
    return null;
}

function shiftEndingMonth(endingMonth, months) {
    if (typeof endingMonth !== "string" || endingMonth.length < 7) return "";

    const year = Number(endingMonth.substring(0, 4));
    const month = Number(endingMonth.substring(5, 7));
    if (!Number.isInteger(year) || !Number.isInteger(month) || month < 1 || month > 12) return "";

    const shifted = year * 12 + (month - 1) + months;
    return `${String(Math.floor(shifted / 12)).padStart(4, "0")}-${String(shifted % 12 + 1).padStart(2, "0")}`;
}

export function nextPeriod(latestPeriod) {
    const parts = periodParts(latestPeriod?.name);
    const step = parts ? PERIOD_STEPS[parts.type] : null;
    if (!step) return null;

    const year = Number(parts.year);
    if (!Number.isInteger(year)) return null;

    const nextYear = step.wraps ? (year + 1) % 100 : year;
    return {
        name: String(nextYear).padStart(2, "0") + step.next,
        endingMonth: shiftEndingMonth(latestPeriod?.endingMonth, step.months),
    };
}

export function existingPeriodNames(periods = []) {
    return periods
        .map(period => formatPeriodName(period?.name)
            || (typeof period?.name === "string" ? period.name : ""))
        .filter(Boolean);
}

export function periodNameError(name, periods = []) {
    if (!PERIOD_NAME_PATTERN.test(name ?? "")) return "expected Q1-Q4, H1, H2 or FY, e.g. 25FY, 25Q1, ...";
    if (existingPeriodNames(periods).includes(name)) return "period already exists for this company";
    return "";
}

export function periodNameWarning(name, latestPeriod) {
    const following = nextPeriod(latestPeriod);
    if (!following || name === following.name) return "";

    const latestName = formatPeriodName(latestPeriod?.name)
        || (typeof latestPeriod?.name === "string" ? latestPeriod.name : "");
    return `does not follow ${latestName}, expected ${following.name}`;
}

export function endingMonthWarning(name, endingMonth, latestPeriod) {
    const following = nextPeriod(latestPeriod);
    if (!following || name !== following.name) return "";
    if (!following.endingMonth || endingMonth === following.endingMonth) return "";
    return `expected ${following.endingMonth} for ${following.name}`;
}

export function reportDateError(reportDate, endingMonth) {
    if (!reportDate || !endingMonth) return "";
    return reportDate.substring(0, 7) <= endingMonth ? "must be after the ending month" : "";
}
