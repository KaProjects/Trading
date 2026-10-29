import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import React from "react";

jest.mock("../../../properties", () => ({backend: "/api"}));

const mockUseMediaQuery = jest.fn();
jest.mock("@mui/material/useMediaQuery", () => (...args) => mockUseMediaQuery(...args));

import {ContentImages} from "../ContentImages";

const images = [
    {id: 4, width: 1600, height: 900},
    {id: 5, width: 1200, height: 800},
];

function swipe(element, fromX, toX, fromY = 200, toY = 200) {
    fireEvent.touchStart(element, {touches: [{clientX: fromX, clientY: fromY}]});
    fireEvent.touchEnd(element, {changedTouches: [{clientX: toX, clientY: toY}]});
}

describe("ContentImages", () => {
    beforeEach(() => {
        mockUseMediaQuery.mockReset();
        mockUseMediaQuery.mockReturnValue(false);
    });

    test("shows thumbnails and opens the full picture on one", () => {
        render(<ContentImages images={images} onAdd={jest.fn()} onMarkRemoved={jest.fn()}/>);

        const thumbnail = screen.getByTestId("content-image-4");
        expect(thumbnail).toHaveAttribute("src", "/api/period/content/image/4?thumbnail=true");

        fireEvent.click(thumbnail);

        // The dialog asks for the picture itself, not the thumbnail.
        const opened = screen.getByRole("dialog").querySelector("img");
        expect(opened).toHaveAttribute("src", "/api/period/content/image/4");
    });

    test("takes a picture from the picker", async () => {
        const onAdd = jest.fn().mockResolvedValue(undefined);
        const file = new File(["bytes"], "chart.png", {type: "image/png"});

        const {container} = render(
            <ContentImages images={[]} editing onAdd={onAdd} onMarkRemoved={jest.fn()}/>
        );

        fireEvent.change(container.querySelector('input[type="file"]'), {target: {files: [file]}});

        await waitFor(() => expect(onAdd).toHaveBeenCalledWith(file));
    });

    test("puts the way to add one at the head of the strip", () => {
        render(<ContentImages images={images} editing onAdd={jest.fn()} onMarkRemoved={jest.fn()}/>);

        const strip = screen.getByTestId("content-images");
        expect(strip.firstElementChild).toBe(screen.getByTestId("content-image-add"));
    });

    test("takes a picture dropped on the tile", async () => {
        const onAdd = jest.fn().mockResolvedValue(undefined);
        const file = new File(["bytes"], "chart.png", {type: "image/png"});

        render(<ContentImages images={[]} editing onAdd={onAdd} onMarkRemoved={jest.fn()}/>);

        fireEvent.drop(screen.getByTestId("content-image-add"), {dataTransfer: {files: [file]}});

        await waitFor(() => expect(onAdd).toHaveBeenCalledWith(file));
    });

    test("does not open a picture while the entry is being edited", () => {
        render(<ContentImages images={images} editing onAdd={jest.fn()} onMarkRemoved={jest.fn()}/>);

        fireEvent.click(screen.getByTestId("content-image-4"));

        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    test("crosses a picture out and offers to put it back, without deleting anything yet", () => {
        const onMarkRemoved = jest.fn();

        const {rerender} = render(
            <ContentImages images={images} editing onAdd={jest.fn()} onMarkRemoved={onMarkRemoved}/>
        );

        // Mouse down, not click: the editor blurs on the way, and the button has to act first.
        fireEvent.mouseDown(screen.getAllByRole("button", {name: "Remove image"})[1]);
        expect(onMarkRemoved).toHaveBeenCalledWith(5, true);

        rerender(
            <ContentImages
                images={images}
                removedIds={[5]}
                editing
                onAdd={jest.fn()}
                onMarkRemoved={onMarkRemoved}
            />
        );

        // Still there, marked, with the way back offered in place of the cross.
        expect(screen.getByTestId("content-image-5")).toBeInTheDocument();
        fireEvent.mouseDown(screen.getByRole("button", {name: "Restore image"}));
        expect(onMarkRemoved).toHaveBeenCalledWith(5, false);
    });

    test("shows a picture that is waiting to be saved, and lets it go again", () => {
        const onDropPending = jest.fn();

        render(
            <ContentImages
                images={[]}
                pending={[{key: "new-1", url: "blob:chart"}]}
                editing
                onAdd={jest.fn()}
                onDropPending={onDropPending}
            />
        );

        expect(screen.getByTestId("content-image-new-1")).toHaveAttribute("src", "blob:chart");

        fireEvent.mouseDown(screen.getByRole("button", {name: "Discard new image"}));
        expect(onDropPending).toHaveBeenCalledWith("new-1");
    });

    test("steps through the pictures with the keyboard as well as the arrows", () => {
        render(<ContentImages images={images} onAdd={jest.fn()}/>);

        fireEvent.click(screen.getByTestId("content-image-4"));
        fireEvent.keyDown(document, {key: "ArrowRight"});

        expect(screen.getByTestId("content-image-opened"))
            .toHaveAttribute("src", "/api/period/content/image/5");

        fireEvent.keyDown(document, {key: "ArrowLeft"});
        expect(screen.getByTestId("content-image-opened"))
            .toHaveAttribute("src", "/api/period/content/image/4");
    });

    test("leaves the picture alone on a narrow screen, where a swipe steps instead", () => {
        mockUseMediaQuery.mockReturnValue(true);
        render(<ContentImages images={images} compact onAdd={jest.fn()}/>);

        fireEvent.click(screen.getByTestId("content-image-4"));
        expect(screen.queryByRole("button", {name: "Next image"})).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Previous image"})).not.toBeInTheDocument();

        const picture = screen.getByTestId("content-image-opened").parentElement;
        swipe(picture, 300, 80);
        expect(screen.getByTestId("content-image-opened"))
            .toHaveAttribute("src", "/api/period/content/image/5");

        swipe(picture, 80, 300);
        expect(screen.getByTestId("content-image-opened"))
            .toHaveAttribute("src", "/api/period/content/image/4");

        // Too short to mean anything, and a flick down the picture is not a step either.
        swipe(picture, 300, 270);
        swipe(picture, 300, 290, 100, 400);
        expect(screen.getByTestId("content-image-opened"))
            .toHaveAttribute("src", "/api/period/content/image/4");
    });

    test("steps through the day's pictures with the arrows on a wide screen", () => {
        render(<ContentImages images={images} onAdd={jest.fn()}/>);

        fireEvent.click(screen.getByTestId("content-image-4"));
        expect(screen.getByTestId("content-image-opened"))
            .toHaveAttribute("src", "/api/period/content/image/4");
        // The first picture has nothing before it.
        expect(screen.queryByRole("button", {name: "Previous image"})).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Next image"}));
        expect(screen.getByTestId("content-image-opened"))
            .toHaveAttribute("src", "/api/period/content/image/5");
        expect(screen.queryByRole("button", {name: "Next image"})).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Previous image"}));
        expect(screen.getByTestId("content-image-opened"))
            .toHaveAttribute("src", "/api/period/content/image/4");
    });

    test("keeps the arrows on a wide screen where the strip lies in a row", () => {
        render(<ContentImages images={images} compact onAdd={jest.fn()}/>);

        fireEvent.click(screen.getByTestId("content-image-4"));

        fireEvent.click(screen.getByRole("button", {name: "Next image"}));
        expect(screen.getByTestId("content-image-opened"))
            .toHaveAttribute("src", "/api/period/content/image/5");
    });

    test("offers nothing at all when there is no picture and no editing", () => {
        const {container} = render(
            <ContentImages images={[]} onAdd={jest.fn()} onMarkRemoved={jest.fn()}/>
        );

        expect(container).toBeEmptyDOMElement();
    });
});
