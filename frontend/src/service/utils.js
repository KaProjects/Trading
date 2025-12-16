
// TODO refactor to %Service.js

export function recordEvent(name) {
    const value = localStorage.getItem(name) ? Number(localStorage.getItem(name)) : 0
    localStorage.setItem(name, value + 1);
}

