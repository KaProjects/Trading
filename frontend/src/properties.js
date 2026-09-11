export const backend = "/api"

const backendPort = process.env.REACT_APP_BACKEND_PORT || "9090"

export const apiDocsUrl = `${window.location.protocol}//${window.location.hostname}:${backendPort}/api/docs/`
