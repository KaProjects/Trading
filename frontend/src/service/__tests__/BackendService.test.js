import React from "react";
import {render, screen, waitFor} from "@testing-library/react";
import axios from "axios";
import {useData} from "../BackendService";

jest.mock("axios");
jest.mock("../../properties", () => ({
    backend: "http://backend",
}));

const mockFormatError = jest.fn();

jest.mock("../FormattingService", () => ({
    formatError: (...args) => mockFormatError(...args),
}));

function TestComponent({path}) {
    const {data, loaded, error} = useData(path);

    return (
        <>
            <div data-testid="data">{data ? JSON.stringify(data) : "null"}</div>
            <div data-testid="loaded">{String(loaded)}</div>
            <div data-testid="error">{error ? JSON.stringify(error) : "null"}</div>
        </>
    );
}

describe("BackendService", () => {
    beforeEach(() => {
        axios.get.mockReset();
        mockFormatError.mockReset();
    });

    test("useData returns loaded data", async () => {
        axios.get.mockResolvedValue({data: {name: "NVDA"}});

        render(<TestComponent path={"/company"}/>);

        await waitFor(() => expect(screen.getByTestId("loaded")).toHaveTextContent("true"));

        expect(axios.get).toHaveBeenCalledWith("http://backend/company");
        expect(screen.getByTestId("data")).toHaveTextContent(JSON.stringify({name: "NVDA"}));
        expect(screen.getByTestId("error")).toHaveTextContent("null");
    });

    test("useData returns formatted error when request fails", async () => {
        const error = {name: "AxiosError", message: "failed"};
        const formatted = {title: "Request failed", message: "details"};

        axios.get.mockRejectedValue(error);
        mockFormatError.mockReturnValue(formatted);

        render(<TestComponent path={"/company"}/>);

        await waitFor(() => expect(screen.getByTestId("error")).toHaveTextContent(JSON.stringify(formatted)));

        expect(axios.get).toHaveBeenCalledWith("http://backend/company");
        expect(mockFormatError).toHaveBeenCalledWith(error);
        expect(screen.getByTestId("loaded")).toHaveTextContent("false");
        expect(screen.getByTestId("data")).toHaveTextContent("null");
    });

    test("useData refetches when path changes", async () => {
        axios.get
            .mockResolvedValueOnce({data: {name: "NVDA"}})
            .mockResolvedValueOnce({data: {name: "AAPL"}});

        const {rerender} = render(<TestComponent path={"/company"}/>);

        await waitFor(() => expect(screen.getByTestId("data")).toHaveTextContent(JSON.stringify({name: "NVDA"})));

        rerender(<TestComponent path={"/record"}/>);

        await waitFor(() => expect(screen.getByTestId("data")).toHaveTextContent(JSON.stringify({name: "AAPL"})));

        expect(axios.get).toHaveBeenNthCalledWith(1, "http://backend/company");
        expect(axios.get).toHaveBeenNthCalledWith(2, "http://backend/record");
    });
});
