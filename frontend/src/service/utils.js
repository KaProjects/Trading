
// TODO refactor to %Service.js

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

