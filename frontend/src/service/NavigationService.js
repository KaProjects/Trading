import {useEffect, useRef} from "react";
import {useLocation} from "react-router-dom";

/**
 * Runs close() on every navigation, including browser back and forward. Dialog state outlives
 * the view it was opened from, so without this a dialog stays open - or re-opens - on top of
 * whatever the user navigated to.
 */
export const useCloseOnNavigation = (close) => {
    const location = useLocation()
    const previousKey = useRef(location.key)
    const latestClose = useRef(close)
    latestClose.current = close

    useEffect(() => {
        if (previousKey.current === location.key) return

        previousKey.current = location.key
        latestClose.current()
    }, [location.key])
}
