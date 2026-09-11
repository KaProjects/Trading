import React from "react";
import {fireEvent, render, screen} from "@testing-library/react";

jest.mock("../../properties", () => ({backend: "/api"}));

import {Admin} from "../Admin";

describe("Admin", () => {
    const originalLocation = window.location;
    const originalOpen = window.open;

    beforeEach(() => {
        delete window.location;
        window.location = {href: ""};
        window.open = jest.fn();
    });

    afterEach(() => {
        window.location = originalLocation;
        window.open = originalOpen;
    });

    test("lists every admin page as a title-only card", () => {
        render(<Admin/>);

        expect(screen.getByText("Companies")).toBeInTheDocument();
        expect(screen.getByText("Trade Import")).toBeInTheDocument();
        expect(screen.getByText("Dividend Import")).toBeInTheDocument();
        expect(screen.getByText("API Docs")).toBeInTheDocument();
        expect(screen.getAllByRole("button")).toHaveLength(4);
    });

    test.each([
        ["Companies", "/admin/companies"],
        ["Trade Import", "/admin/import/trades"],
        ["Dividend Import", "/admin/import/dividends"],
    ])("navigates to %s", (title, path) => {
        render(<Admin/>);

        fireEvent.click(screen.getByText(title));

        expect(window.location.href).toBe(path);
    });

    test("opens the api docs in a new tab", () => {
        render(<Admin/>);

        fireEvent.click(screen.getByText("API Docs"));

        expect(window.open).toHaveBeenCalledWith("/api/api/docs/", "_blank");
        expect(window.location.href).toBe("");
    });
});
