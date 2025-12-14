
export function formatPeriodName(period) {
    if (isNotAValue(period)) return "";
    if (typeof period !== 'object'
        || (!('year' in period) || !('type' in period))
        || (typeof period.year !== 'string' || typeof period.type !== 'string')
        || (period.year.length  !== 4 || period.type.length !== 2)
    ) {
        console.error(`'${period}' is not a valid period object`)
        return ""
    }
    return period.year.substring(2, 4) + period.type;
}

export function formatMillions(millions) {
    if (isNotAValue(millions)) return "";
    if (typeof millions !== 'number') {
        console.error(`'${millions}' is not a valid number`)
        return ""
    }

    const num = Number(millions);

    if (num >= 1000) {
        return formatDecimals(num / 1000, 0, 2) + "B";
    } else {
        return formatDecimals(num, 0, 2) + "M";
    }
}

export function formatDecimals(number, min= 0, max = 0) {
    if (isNotAValue(number)) return "";
    if (typeof number !== 'number') {
        console.error(`'${number}' is not a valid number`)
        return ""
    }
    if (isNotAValue(min) || typeof min !== 'number') {
        console.error(`'${min}' is not a valid number`)
        return ""
    }
    if (isNotAValue(max) || typeof max !== 'number') {
        console.error(`'${max}' is not a valid number`)
        return ""
    }
    if (min > max) {
        console.error(`'${min}' > '${max}'`)
        return ""
    }
    return Number(number).toLocaleString("en-US", {
        minimumFractionDigits: min,
        maximumFractionDigits: max,
    });
}

export function formatDate(date) {
    if (isNotAValue(date)) return "";
    if (typeof date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
        console.error(`'${date}' is not a valid date`)
        return ""
    }
    const [year, month, day] = date.split("-");
    return `${day}.${month}.${year}`;
}

export function formatPercent(value) {
    if (isNotAValue(value)) return "";
    value = formatDecimals(value, 0, 2)
    return value ? value + "%" : "";
}

export function isNotAValue(value) {
    return (value === null || value === undefined || value === ""
        || (typeof value === 'object' && Object.keys(value).length === 0)
    )
}

export function formatError(error) {
    console.error(error) // for debug purposes for now
    let message = "Unexpected Error:"
    let details = isNotAValue(error) ? "" : JSON.stringify(error)

    if (error && error.name === "AxiosError") {
        message = error.message

        if (error.response && error.response.data) {
            if (typeof error.response.data === 'string') {
                details = error.response.data;
            }
            else if (typeof error.response.data === 'object' && error.response.data.details) {
                if (error.response.data.details.split(', ').length === 2) {
                    details = error.response.data.details.split(', ')[1];
                } else {
                    details = error.response.data.details;
                }
            }
            else {
                details = JSON.stringify(error.response.data)
            }
        } else {
            details = ""
        }
    }
    return {message: message, details: details}
}