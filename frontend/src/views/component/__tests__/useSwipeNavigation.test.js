import {isInsideOverlay} from "../useSwipeNavigation";

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
