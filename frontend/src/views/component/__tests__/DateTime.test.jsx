import {render, screen} from "@testing-library/react";
import {DateTime} from "../DateTime";

describe("DateTime", () => {
    test("renders nothing when value is null", () => {
        const {container} = render(<DateTime value={null} sx={{fontSize: 11}}/>);

        expect(container.firstChild).toBeNull();
    });

    test("renders formatted date and time", () => {
        render(<DateTime value={"2026-05-09T14:33:22"} sx={{fontSize: 11}}/>);

        expect(screen.getByText("09.05.2026")).toBeInTheDocument();
        expect(screen.getByText("14:33:22")).toBeInTheDocument();
    });
});
