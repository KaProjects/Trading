
export function validateNumber(value, isNullable, lengthConstraint, decimalConstraint, canNegative) {
    if (typeof value != "string") return "not a string"
    if (value === "") return isNullable ? "" : "not filled"
    if (isNaN(value) || isNaN(parseFloat(value)) || value.endsWith(".") || value.startsWith(".")) return "not a valid number"
    if (!canNegative && Number(value) < 0) return "negative values not allowed"
    const split = value.split(".")
    if (split[0].length > lengthConstraint - decimalConstraint) return "max length " + (lengthConstraint - decimalConstraint)
    if (split.length > 1 && split[1].length > decimalConstraint) return "max decimal " + decimalConstraint
    return ""
}

export function validateQuarter(value) {
    if (typeof value != "string") return "not a string"
    if (!value) return "not filled"
    const invalidFormatMsg = "invalid format, not YYQQ (e.g. 24Q1, 25H2, 26FY, ...)"
    if (value.length !== 4) return invalidFormatMsg
    if (isNaN(value.substring(0, 2))) return invalidFormatMsg
    if (!["Q1", "Q2", "Q3", "Q4", "H1", "H2", "FY"].includes(value.substring(2, 4))) return invalidFormatMsg
    return ""
}

export function validateTicker(value) {
    if (typeof value != "string") return "not a string"
    if (!value) return "not filled"
    if (value.length > 5) return "max length 5"
    if (value.toUpperCase() !== value) return "only uppercase"
    return ""
}

export function validateDate(date, canInFuture, canInPast) {
    if (!date) return "should not be empty"
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return "doesn't match YYYY-MM-DD"
    let today = new Date();
    today = [today.getFullYear(), String(today.getMonth() + 1).padStart(2, "0"), String(today.getDate()).padStart(2, "0"),].join("-");
    if (!canInFuture && date > today) return "should not be in the future"
    if (!canInPast && date < today) return "should not be in the past"
    return ""
}