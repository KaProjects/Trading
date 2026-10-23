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

/**
 * Snapshots where every ancestor up to and including the swipe area is scrolled to, plus the
 * page itself.
 *
 * It deliberately does not try to work out which of them can scroll. Only a difference between
 * this snapshot and the one at the end of the gesture matters, and an element whose scroll
 * position changed did scroll, whatever its computed overflow says.
 */
function scrollSnapshot(element, boundary) {
    const entries = []
    let current = element

    while (current && current !== boundary?.parentElement) {
        if (current.nodeType === 1) {
            entries.push({element: current, left: current.scrollLeft, top: current.scrollTop})
        }
        if (current === boundary) break
        current = current.parentElement
    }

    return {
        entries,
        pageX: window.scrollX ?? 0,
        pageY: window.scrollY ?? 0,
    }
}

/**
 * Whether the gesture scrolled anything at all.
 *
 * A swipe must never double as the tail of a scroll. Checking only where a scroller ended up
 * is not enough: dragging a table to its right-hand edge and carrying on in the same movement
 * leaves it at the edge, which used to read as "nothing left to scroll, so navigate" and threw
 * the user onto the next tab mid-gesture. Comparing against where it started means any gesture
 * that moved content is a scroll and nothing else, and reaching the edge simply ends there. The
 * next touch starts fresh and navigates, which is the deliberate second swipe.
 */
function scrolledDuringGesture(snapshot) {
    if (!snapshot) return false
    if ((window.scrollX ?? 0) !== snapshot.pageX) return true
    if ((window.scrollY ?? 0) !== snapshot.pageY) return true

    return snapshot.entries.some(entry =>
        entry.element.scrollLeft !== entry.left || entry.element.scrollTop !== entry.top)
}

/** The start of a potential swipe, or null when the touch began inside a dialog. */
export function beginSwipe(event, boundary) {
    if (isInsideOverlay(event.target)) return null

    const touch = event.touches[0]
    return {
        x: touch.clientX,
        y: touch.clientY,
        target: event.target,
        scroll: scrollSnapshot(event.target, boundary ?? event.currentTarget),
    }
}

/**
 * The direction a finished gesture swiped in, or null when it was not a swipe: too short, more
 * across than along the axis asked for, or a scroll.
 *
 * @param axis "x" for a horizontal swipe, "y" for a vertical one
 * @returns -1 (left / up), 1 (right / down), or null
 */
export function swipeDirection(start, event, axis = "x") {
    if (!start) return null

    const touch = event.changedTouches[0]
    const deltaX = touch.clientX - start.x
    const deltaY = touch.clientY - start.y
    const along = axis === "y" ? deltaY : deltaX
    const across = axis === "y" ? deltaX : deltaY

    if (Math.abs(along) < SWIPE_THRESHOLD || Math.abs(along) < Math.abs(across)) return null
    if (scrolledDuringGesture(start.scroll)) return null

    return along < 0 ? -1 : 1
}

export function useSwipeNavigation(index, setIndex, count) {
    const start = useRef(null)

    function onTouchStart(event) {
        start.current = beginSwipe(event)
    }

    function onTouchEnd(event) {
        const from = start.current
        start.current = null
        if (!from || !setIndex || count < 2) return

        const direction = swipeDirection(from, event)
        if (direction === null) return

        setIndex(direction < 0 ? Math.min(index + 1, count - 1) : Math.max(index - 1, 0))
    }

    return {onTouchStart, onTouchEnd}
}
