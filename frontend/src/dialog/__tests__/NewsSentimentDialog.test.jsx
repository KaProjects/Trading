import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

jest.mock("axios");
jest.mock("../../properties", () => ({backend: "/api"}));
jest.mock("../../service/FormattingService", () => {
    const actual = jest.requireActual("../../service/FormattingService");
    return {
        ...actual,
        formatError: () => ({title: "Request failed", message: "Network error"}),
    };
});

import {NewsSentimentDialog} from "../NewsSentimentDialog";

const company = {ticker: "NVDA"};
const period = {id: "period-1", name: {year: "2026", type: "Q2"}};

describe("NewsSentimentDialog", () => {
    beforeEach(() => axios.get.mockReset());

    test("loads period records and expands their key takeaways", async () => {
        axios.get.mockResolvedValue({
            data: {
                records: [
                    {
                        id: "2026-08-23-latest",
                        date: "2026-08-23",
                        total: 4,
                        stats: {positive: 2, mixed: 1, negative: 1},
                        keyTakeaways: ["Demand broadened.", "Competition increased."],
                    },
                    {
                        id: "2026-08-16-older",
                        date: "2026-08-16",
                        total: 2,
                        stats: {neutral: 2},
                        keyTakeaways: [],
                    },
                ],
                window: {start: "2026-05-27", end: "2026-08-27"},
                warnings: [],
            },
        });

        render(
            <NewsSentimentDialog
                open
                handleClose={jest.fn()}
                company={company}
                period={period}
            />
        );

        expect(screen.getByRole("progressbar", {name: "Loading news sentiment"})).toBeInTheDocument();
        expect(await screen.findByText("23.08.2026")).toBeInTheDocument();
        expect(screen.getByText("16.08.2026")).toBeInTheDocument();
        expect(screen.getByText("4 articles")).toBeInTheDocument();
        expect(axios.get).toHaveBeenCalledWith("/api/news-sentiment/period/period-1");

        fireEvent.click(screen.getByText("23.08.2026"));
        expect(screen.getByText("Demand broadened.")).toBeInTheDocument();
        expect(screen.getByText("Competition increased.")).toBeInTheDocument();
    });

    test("keeps the dialog usable when Firebase returns a warning", async () => {
        axios.get.mockResolvedValue({
            data: {
                records: [],
                window: null,
                warnings: ["Firebase news sentiment could not be loaded"],
            },
        });

        const handleClose = jest.fn();
        render(
            <NewsSentimentDialog
                open
                handleClose={handleClose}
                company={company}
                period={period}
            />
        );

        expect(await screen.findByText("Some expected news sentiment data could not be loaded")).toBeInTheDocument();
        expect(screen.getByText("Firebase news sentiment could not be loaded")).toBeInTheDocument();
        expect(screen.getByText("No news sentiment records are available for this period.")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Close"}));
        expect(handleClose).toHaveBeenCalled();
    });

    test("formats a transport failure as an error", async () => {
        axios.get.mockRejectedValue(new Error("network"));

        render(
            <NewsSentimentDialog
                open
                handleClose={jest.fn()}
                company={company}
                period={period}
            />
        );

        expect(await screen.findByText("Request failed")).toBeInTheDocument();
        expect(screen.getByText("Network error")).toBeInTheDocument();
    });

    test("does not request data while closed", async () => {
        render(
            <NewsSentimentDialog
                open={false}
                handleClose={jest.fn()}
                company={company}
                period={period}
            />
        );

        await waitFor(() => expect(axios.get).not.toHaveBeenCalled());
    });
});
