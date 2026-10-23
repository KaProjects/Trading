import {beginSwipe, isInsideOverlay, swipeDirection} from "../useSwipeNavigation";

describe("isInsideOverlay", () => {
    afterEach(() => {
        document.body.innerHTML = "";
    });

    test("detects an element rendered inside a modal overlay", () => {
        document.body.innerHTML = `
            <div class="MuiModal-root"><div class="MuiDialog-paper"><table id="inner"></table></div></div>
        `;

        expect(isInsideOverlay(document.getElementById("inner"))).toBe(true);
    });

    test("detects the overlay element itself", () => {
        document.body.innerHTML = `<div id="overlay" class="MuiModal-root"></div>`;

        expect(isInsideOverlay(document.getElementById("overlay"))).toBe(true);
    });

    test("leaves page content alone", () => {
        document.body.innerHTML = `<div class="page"><div id="content"></div></div>`;

        expect(isInsideOverlay(document.getElementById("content"))).toBe(false);
    });

    test("tolerates missing targets", () => {
        expect(isInsideOverlay(null)).toBe(false);
        expect(isInsideOverlay(undefined)).toBe(false);
        expect(isInsideOverlay({})).toBe(false);
    });
});

describe("swipeDirection", () => {
    afterEach(() => {
        document.body.innerHTML = "";
        window.scrollTo(0, 0);
    });

    function area() {
        document.body.innerHTML = `<div id="area"><div id="scroller"><span id="inner"></span></div></div>`;
        return {
            area: document.getElementById("area"),
            scroller: document.getElementById("scroller"),
            inner: document.getElementById("inner"),
        };
    }

    function start(target, currentTarget, x, y) {
        return beginSwipe({target, currentTarget, touches: [{clientX: x, clientY: y}]});
    }

    function end(x, y) {
        return {changedTouches: [{clientX: x, clientY: y}]};
    }

    test("reports the direction of a gesture that scrolled nothing", () => {
        const {area: root, inner} = area();

        expect(swipeDirection(start(inner, root, 300, 100), end(100, 100))).toBe(-1);
        expect(swipeDirection(start(inner, root, 100, 100), end(300, 100))).toBe(1);
    });

    test("refuses a gesture that scrolled a component, even one that ends at the edge", () => {
        const {area: root, scroller, inner} = area();
        const from = start(inner, root, 300, 100);
        scroller.scrollLeft = 400;

        expect(swipeDirection(from, end(100, 100))).toBe(null);
    });

    test("refuses a gesture that scrolled the page", () => {
        const {area: root, inner} = area();
        const from = start(inner, root, 100, 300);
        window.scrollY = 200;

        expect(swipeDirection(from, end(100, 100), "y")).toBe(null);

        window.scrollY = 0;
    });

    test("accepts the next gesture once the scroller can move no further", () => {
        const {area: root, scroller, inner} = area();
        scroller.scrollLeft = 400;

        // A fresh touch: the scroller is pinned at the edge, so nothing moves this time.
        expect(swipeDirection(start(inner, root, 300, 100), end(100, 100))).toBe(-1);
    });

    test("ignores a gesture that is too short or mostly across the axis", () => {
        const {area: root, inner} = area();

        expect(swipeDirection(start(inner, root, 300, 100), end(270, 100))).toBe(null);
        expect(swipeDirection(start(inner, root, 300, 100), end(200, 400))).toBe(null);
    });

    test("reads the vertical axis when asked", () => {
        const {area: root, inner} = area();

        expect(swipeDirection(start(inner, root, 100, 300), end(100, 100), "y")).toBe(-1);
        expect(swipeDirection(start(inner, root, 100, 100), end(100, 300), "y")).toBe(1);
    });

    test("does not start inside a dialog", () => {
        document.body.innerHTML = `<div class="MuiModal-root"><span id="inner"></span></div>`;
        const inner = document.getElementById("inner");

        expect(beginSwipe({target: inner, currentTarget: document.body, touches: [{clientX: 0, clientY: 0}]}))
            .toBe(null);
        expect(swipeDirection(null, end(300, 0))).toBe(null);
    });
});
