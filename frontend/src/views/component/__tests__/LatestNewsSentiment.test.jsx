import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

jest.mock("axios");
jest.mock("../../../properties", () => ({backend: "/api"}));

import {LatestNewsSentiment} from "../LatestNewsSentiment";

describe("LatestNewsSentiment", () => {
    beforeEach(() => axios.get.mockReset());

    test("loads the latest record and opens the sentiment dialog", async () => {
        axios.get.mockResolvedValue({
            data: {
                record: {
                    id: "2026-08-23-latest",
                    date: "2026-08-23",
                    total: 5,
                    stats: {positive: 3, neutral: 1, negative: 1},
                    keyTakeaways: ["Demand remains broad.", "Valuation is the main risk."],
                },
                warnings: [],
            },
        });

        const onOpen = jest.fn();
        render(<LatestNewsSentiment companyId="company-1" onOpen={onOpen}/>);

        expect(screen.getByTestId("latest-news-sentiment-loading")).toBeInTheDocument();
        expect(await screen.findByText("Analysis")).toBeInTheDocument();
        expect(screen.getByText("23.08.2026")).toBeInTheDocument();
        expect(screen.getByText("5 articles")).toBeInTheDocument();
        expect(screen.getByTestId("sentiment-breakdown")).toHaveTextContent(
            "Positive 3Neutral 1Negative 1"
        );
        expect(axios.get).toHaveBeenCalledWith("/api/news-sentiment/company/company-1/latest");

        expect(screen.queryByText("Demand remains broad.")).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Latest news sentiment"}));
        expect(onOpen).toHaveBeenCalled();
    });

    test("renders nothing when Firebase returns a warning", async () => {
        axios.get.mockResolvedValue({
            data: {
                record: null,
                warnings: ["Firebase news sentiment for NVDA could not be loaded: permission denied"],
            },
        });

        const {container} = render(<LatestNewsSentiment companyId="company-1"/>);

        await waitFor(() => expect(screen.queryByTestId("latest-news-sentiment-loading")).not.toBeInTheDocument());
        expect(container).toBeEmptyDOMElement();
    });

    test("renders nothing when the request fails", async () => {
        const consoleError = jest.spyOn(console, "error").mockImplementation(() => {});
        axios.get.mockRejectedValue(new Error("network"));

        const {container} = render(<LatestNewsSentiment companyId="company-1"/>);

        await waitFor(() => expect(screen.queryByTestId("latest-news-sentiment-loading")).not.toBeInTheDocument());
        expect(container).toBeEmptyDOMElement();
        consoleError.mockRestore();
    });

    test("renders nothing when no news exists and no warning occurred", async () => {
        axios.get.mockResolvedValue({data: {record: null, warnings: []}});

        const {container} = render(<LatestNewsSentiment companyId="company-1"/>);

        await waitFor(() => expect(screen.queryByTestId("latest-news-sentiment-loading")).not.toBeInTheDocument());
        expect(container).toBeEmptyDOMElement();
    });
});
