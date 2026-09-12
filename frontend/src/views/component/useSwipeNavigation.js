import {useRef} from "react";

const SWIPE_THRESHOLD = 60

export const SWIPE_AREA_SX = {
    display: "flex",
    flexDirection: "column",
    flexGrow: 1,
}

export function isInsideOverlay(element) {
    return Boolean(element?.closest?.(".MuiModal-root"))
}

function isHorizontallyScrollable(element) {
    if (element.scrollWidth - element.clientWidth <= 1) return false

    const overflowX = window.getComputedStyle(element).overflowX
    return overflowX === "auto" || overflowX === "scroll"
}

export function horizontalScrollAncestor(element, boundary) {
    let current = element
    while (current && current !== boundary) {
        if (isHorizontallyScrollable(current)) return current
        current = current.parentElement
    }
    return null
}

export function useSwipeNavigation(index, setIndex, count) {
    const start = useRef(null)

    function onTouchStart(event) {
        if (isInsideOverlay(event.target)) {
            start.current = null
            return
        }

        const touch = event.touches[0]
        start.current = {x: touch.clientX, y: touch.clientY, target: event.target}
    }

    function onTouchEnd(event) {
        const from = start.current
        start.current = null
        if (!from || !setIndex || count < 2) return

        const touch = event.changedTouches[0]
        const deltaX = touch.clientX - from.x
        const deltaY = touch.clientY - from.y
        if (Math.abs(deltaX) < SWIPE_THRESHOLD || Math.abs(deltaX) < Math.abs(deltaY)) return

        const scroller = horizontalScrollAncestor(from.target, event.currentTarget)
        if (scroller) {
            const atStart = scroller.scrollLeft <= 1
            const atEnd = scroller.scrollLeft + scroller.clientWidth >= scroller.scrollWidth - 1
            if (deltaX < 0 ? !atEnd : !atStart) return
        }

        setIndex(deltaX < 0 ? Math.min(index + 1, count - 1) : Math.max(index - 1, 0))
    }

    return {onTouchStart, onTouchEnd}
}
