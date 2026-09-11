import React from "react";
import {fireEvent, render, screen} from "@testing-library/react";

jest.mock("../../properties", () => ({backend: "/api", apiDocsUrl: "http://localhost:9090/api/docs/"}));

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

        const titles = ["Companies", "Stock Split", "Trade Import", "Dividend Import", "API Docs"];
        titles.forEach(title => expect(screen.getByText(title)).toBeInTheDocument());
        expect(screen.getAllByRole("button")).toHaveLength(titles.length);
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

        expect(window.open).toHaveBeenCalledWith("http://localhost:9090/api/docs/", "_blank");
        expect(window.location.href).toBe("");
    });
});
