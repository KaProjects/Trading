import {useEffect, useState} from "react";
import axios from "axios";
import {domain} from "./properties";
import {handleError} from "./utils";

export const useData = (path) => {

    const [data, setData] = useState(null);
    const [loaded, setLoaded] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        setError(null)
        setLoaded(false)
        axios.get(domain + path)
            .then((response) => {
                setData(response.data)
                setError(null)
                setLoaded(true)
            }).catch((error) => {
                setError(handleError(error))
                setLoaded(false)
            })
        // eslint-disable-next-line
    }, [path]);

    return { data, loaded, error };
};