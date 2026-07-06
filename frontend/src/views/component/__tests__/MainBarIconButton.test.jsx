import React from "react";
import {fireEvent, render, screen} from "@testing-library/react";
import {MainBarIconButton} from "../MainBarIconButton";

const TestIcon = (props) => <svg data-testid="test-icon" {...props}/>;

describe("MainBarIconButton", () => {
    test("renders clickable icon button", () => {
        const onClick = jest.fn();

        render(
            <MainBarIconButton
                tooltip="Open action"
                ariaLabel="open action"
                onClick={onClick}
                icon={TestIcon}
                color="red"
                buttonSx={{width: 45, height: 30}}
                iconSx={{width: 23, height: 23}}
            />
        );

        fireEvent.click(screen.getByRole("button", {name: "open action"}));

        expect(onClick).toHaveBeenCalledTimes(1);
        expect(screen.getByTestId("test-icon")).toBeInTheDocument();
    });

    test("renders external image link", () => {
        render(
            <MainBarIconButton
                tooltip="External source"
                href="https://example.com/source"
                image="https://example.com/favicon.ico"
                alt="External source"
                buttonSx={{width: 45, height: 30}}
                iconSx={{width: 21, height: 21}}
            />
        );

        const link = screen.getByRole("link", {name: "External source"});

        expect(link).toHaveAttribute("href", "https://example.com/source");
        expect(link).toHaveAttribute("target", "_blank");
        expect(link).toHaveAttribute("rel", "noopener noreferrer");
        expect(screen.getByAltText("External source")).toHaveAttribute("src", "https://example.com/favicon.ico");
    });
});
