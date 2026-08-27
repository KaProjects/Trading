import {render, screen} from "@testing-library/react";
import {SentimentBreakdown} from "../SentimentBreakdown";

describe("SentimentBreakdown", () => {
    test("renders dynamic sentiment counts in the preferred order", () => {
        render(
            <SentimentBreakdown
                stats={{negative: 1, custom: 2, positive: 3, neutral: 1, mixed: 1, missing: 0}}
                total={8}
            />
        );

        expect(screen.getByTestId("sentiment-breakdown")).toHaveTextContent(
            "Positive 3Neutral 1Mixed 1Negative 1Custom 2"
        );
        expect(screen.getByLabelText("Sentiment distribution across 8 articles")).toBeInTheDocument();
        expect(screen.queryByText(/Missing/)).not.toBeInTheDocument();
    });

    test("handles records without classified articles", () => {
        render(<SentimentBreakdown stats={{positive: 0}} total={0}/>);

        expect(screen.getByText("No classified articles")).toBeInTheDocument();
    });
});
