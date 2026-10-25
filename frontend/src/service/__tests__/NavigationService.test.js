import React from "react";
import {fireEvent, render, screen} from "@testing-library/react";
import {MemoryRouter, Route, Routes, useNavigate} from "react-router-dom";
import {useCloseOnNavigation} from "../NavigationService";

function TestComponent({close}) {
    useCloseOnNavigation(close);
    const navigate = useNavigate();

    return (
        <>
            <button onClick={() => navigate("/other")}>other view</button>
            <button onClick={() => navigate("/research?company=2")}>other company</button>
            <button onClick={() => navigate(-1)}>back</button>
        </>
    );
}

function renderHarness(close) {
    return render(
        <MemoryRouter initialEntries={["/research?company=1"]}>
            <Routes>
                <Route path="*" element={<TestComponent close={close}/>}/>
            </Routes>
        </MemoryRouter>
    );
}

describe("useCloseOnNavigation", () => {

    it("does not close on the initial render", () => {
        const close = jest.fn();

        renderHarness(close);

        expect(close).not.toHaveBeenCalled();
    });

    it("closes when navigating to another view", () => {
        const close = jest.fn();
        renderHarness(close);

        fireEvent.click(screen.getByText("other view"));

        expect(close).toHaveBeenCalledTimes(1);
    });

    it("closes when only the query parameter changes", () => {
        const close = jest.fn();
        renderHarness(close);

        fireEvent.click(screen.getByText("other company"));

        expect(close).toHaveBeenCalledTimes(1);
    });

    it("closes when the user goes back", () => {
        const close = jest.fn();
        renderHarness(close);
        fireEvent.click(screen.getByText("other view"));
        close.mockClear();

        fireEvent.click(screen.getByText("back"));

        expect(close).toHaveBeenCalledTimes(1);
    });

    it("uses the latest close callback without needing it memoized", () => {
        const stale = jest.fn();
        const fresh = jest.fn();
        const {rerender} = renderHarness(stale);

        rerender(
            <MemoryRouter initialEntries={["/research?company=1"]}>
                <Routes>
                    <Route path="*" element={<TestComponent close={fresh}/>}/>
                </Routes>
            </MemoryRouter>
        );
        fireEvent.click(screen.getByText("other view"));

        expect(stale).not.toHaveBeenCalled();
        expect(fresh).toHaveBeenCalledTimes(1);
    });
});
