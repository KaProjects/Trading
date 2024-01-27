import {useEffect, useState} from "react";
import axios from "axios";
import {domain} from "./properties";
import {handleError} from "./utils";

export const useData = (path) => {

    const [data, setData] = useState(null);
    const [loaded, setLoaded] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        const dataFetch = async () => {
            await axios.get(domain + path)
                .then((response) => {
                    setData(response.data)
                    setError(null)
                    setLoaded(true)
                }).catch((error) => {
                    setError(handleError(error))
                    setLoaded(false)
                })
        };

        dataFetch();
    }, [path]);

    return { data, loaded, error };
};