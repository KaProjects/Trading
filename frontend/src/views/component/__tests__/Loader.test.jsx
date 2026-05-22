import {render, screen} from "@testing-library/react";
import {Loader} from "../Loader";

describe("Loader", () => {
    test("renders progress indicator when error is null", () => {
        render(<Loader error={null}/>);

        expect(screen.getByRole("progressbar")).toBeInTheDocument();
    });

    test("renders error alert title and message", () => {
        render(<Loader error={{title: "Failed", message: "network"}}/>);

        expect(screen.getByText("Failed")).toBeInTheDocument();
        expect(screen.getByText("network")).toBeInTheDocument();
    });
});
