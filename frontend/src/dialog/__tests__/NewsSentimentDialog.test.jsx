import {fireEvent, render, screen, waitFor, within} from "@testing-library/react";
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

    test("shows the latest analysis and pages back to the older ones", async () => {
        axios.get.mockResolvedValue({
            data: {
                records: [
                    {
                        id: "2026-08-23-latest",
                        date: "2026-08-23",
                        total: 4,
                        stats: {positive: 2, mixed: 1, negative: 1},
                        keyTakeaways: ["Demand broadened.", "Competition increased."],
                        bull: [{point: "Capacity sold out", reasoning: "orders exceed supply"}],
                        bear: [],
                        bullBearAnalysed: true,
                    },
                    {
                        id: "2026-08-16-older",
                        date: "2026-08-16",
                        total: 2,
                        stats: {neutral: 2},
                        keyTakeaways: [],
                        bull: [],
                        bear: [],
                        bullBearAnalysed: false,
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
        expect(screen.getByText("4 articles")).toBeInTheDocument();
        expect(screen.getByText("Demand broadened.")).toBeVisible();
        expect(screen.getByText("Competition increased.")).toBeVisible();
        expect(screen.getByText("16.08.2026")).not.toBeVisible();
        expect(screen.getByTestId("sentiment-position")).toHaveTextContent("1 / 2");
        expect(axios.get).toHaveBeenCalledWith("/api/news-sentiment/period/period-1");

        expect(screen.getByText("Capacity sold out")).toBeVisible();
        expect(screen.getByText("orders exceed supply")).toBeVisible();
        expect(within(screen.getByTestId("sentiment-bear-case"))
            .getByText("The analysis found no bear points.")).toBeVisible();
        expect(screen.getByRole("button", {name: "Newer analysis"})).toBeDisabled();

        fireEvent.click(screen.getByRole("button", {name: "Older analysis"}));

        expect(screen.getByText("16.08.2026")).toBeVisible();
        expect(screen.getByText("2 articles")).toBeVisible();
        expect(screen.getByText("No news was analysed this week.")).toBeVisible();
        expect(screen.getByText("23.08.2026")).not.toBeVisible();
        expect(within(screen.getByTestId("sentiment-bull-case")).getByText("Not part of this weekly analysis."))
            .toBeVisible();
        expect(screen.getByTestId("sentiment-position")).toHaveTextContent("2 / 2");
        expect(screen.getByRole("button", {name: "Older analysis"})).toBeDisabled();

        fireEvent.click(screen.getByRole("button", {name: "Newer analysis"}));
        expect(screen.getByText("23.08.2026")).toBeVisible();
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
        expect(screen.getByText("No analysis is available for this period.")).toBeInTheDocument();
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
