import {useEffect, useState} from "react";
import axios from "axios";
import {backend} from "../properties";
import {formatError} from "./FormattingService";

export const useData = (path) => {

    const [data, setData] = useState(null)
    const [loaded, setLoaded] = useState(false)
    const [error, setError] = useState(null)

    useEffect(() => {
        let currentRequest = true

        setError(null)
        setLoaded(false)
        axios.get(backend + path)
            .then((response) => {
                if (!currentRequest) return
                setData(response.data)
                setError(null)
                setLoaded(true)
            }).catch((error) => {
                if (!currentRequest) return
                setError(formatError(error))
                setLoaded(false)
            })

        return () => {
            currentRequest = false
        }
    }, [path])

    return { data, loaded, error }
}
