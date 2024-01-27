
export function validateNumber(value, isNullable, lengthConstraint, decimalConstraint) {
    if (typeof value != "string") return "not a string"
    if (value === "") return isNullable ? "" : "non empty"
    if (isNaN(value) || isNaN(parseFloat(value)) || value.endsWith(".") || value.startsWith(".")) return "not a valid number";
    if (value.replace(".", "").length > lengthConstraint) return "max length " + lengthConstraint;
    const split = value.split(".")
    if (split.length > 1 && split[1].length > decimalConstraint) return "max decimal " + decimalConstraint
    return ""
}

export function handleError(error) {
    console.error(error)
    if (typeof error.response.data === 'string') {
        return error.response.data
    } else {
        return error.message
    }
}