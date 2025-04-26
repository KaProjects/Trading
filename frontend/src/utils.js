
export function validateNumber(value, isNullable, lengthConstraint, decimalConstraint) {
    if (typeof value != "string") return "not a string"
    if (value === "") return isNullable ? "" : "non empty"
    if (isNaN(value) || isNaN(parseFloat(value)) || value.endsWith(".") || value.startsWith(".")) return "not a valid number"
    const split = value.split(".")
    if (split[0].length > lengthConstraint - decimalConstraint) return "max length " + (lengthConstraint - decimalConstraint)
    if (split.length > 1 && split[1].length > decimalConstraint) return "max decimal " + decimalConstraint
    return ""
}

export function handleError(error) {
    console.error(error)
    if (error.response && error.response.data && typeof error.response.data === 'string') {
        return error.response.data
    } else {
        return error.message
    }
}

export function recordEvent(name) {
    const value = localStorage.getItem(name) ? Number(localStorage.getItem(name)) : 0
    localStorage.setItem(name, value + 1);
}

export function validateQuarter(value) {
    if (typeof value != "string") return "not a string"
    if (!value) return "non empty"
    const invalidFormatMsg = "invalid format, not YYQQ (e.g. 24Q1, 25H2, 26YY, ...)"
    if (value.length !== 4) return invalidFormatMsg
    if (isNaN(value.substring(0,2))) return invalidFormatMsg
    if (!["Q1","Q2","Q3","Q4","H1","H2","YY"].includes(value.substring(2,4))) return invalidFormatMsg
    return ""
}

export function validateTicker(value) {
    if (typeof value != "string") return "not a string"
    if (!value) return "non empty"
    if (value.length > 5) return "max length 5"
    if (value.toUpperCase() !== value) return "only uppercase"
    return ""
}

export function validateShares(value) {
    if (typeof value != "string") return "not a string"
    if (!value) return "" //nullable
    if (value.length > 7) return "max length 7"
    const power = value.substring(value.length - 1)
    if (power !== "B" && power !== "M") return "invalid format (eg 100.1M or 12B)"
    const number = value.substring(0, value.length - 1)
    return validateNumber(number, false, 5, 2)
}