import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

jest.mock("axios");
jest.mock("../../properties", () => ({
    backend: "/api",
}));

import {Onboarding} from "../Onboarding";

function estimatesResponse(overrides = {}) {
    return {
        ticker: "ORCL",
        reported: [
            {label: "26Q3", date: "2025-11-26", eps: 1.42, revenue: 9240000000},
            {label: "26Q4", date: "2026-02-25", eps: 1.54, revenue: 9610000000},
            {label: "27Q1", date: "2026-05-28", eps: 1.62, revenue: 9870000000},
            {label: "27Q2", date: "2026-08-27", eps: 1.81, revenue: 10240000000},
        ],
        estimated: [
            {label: "27Q3", date: "2026-11-27", eps: 1.88, revenue: 10610000000},
            {label: "27Q4", date: "2027-02-26", eps: 1.96, revenue: 10980000000},
            {label: "28Q1", date: "2027-05-28", eps: 2.05, revenue: 11340000000},
            {label: "28Q2", date: "2027-08-27", eps: 2.18, revenue: 11760000000},
        ],
        projection: {
            ttm: {eps: 6.39, change: null},
            current: {eps: 6.85, change: 7.2},
            next1: {eps: 7.27, change: 13.77},
            next2: {eps: 7.7, change: 20.5},
            next3: {eps: 8.07, change: 26.29},
        },
        revenueProjection: {
            ttm: {eps: 38960000000, change: null},
            current: {eps: 40330000000, change: 3.52},
            next1: {eps: 41700000000, change: 7.03},
            next2: {eps: 43060000000, change: 10.52},
            next3: {eps: 44760000000, change: 14.89},
        },
        warnings: [],
        ...overrides,
    };
}

function newsResponse(overrides = {}) {
    return {
        ticker: "ORCL",
        from: "2026-08-14",
        sentiment: {total: 4, positive: 2, neutral: 1, negative: 1, unrated: 0},
        articles: [
            {
                title: "Agentforce expands to service teams", publisher: "Reuters",
                published: "2026-09-12T13:30:00Z", url: "https://example.test/1",
                sentiment: "positive", reasoning: "broader addressable base",
            },
            {
                title: "Deal lost to rival suite", publisher: "Business Wire",
                published: "2026-09-05T13:30:00Z", url: "https://example.test/2",
                sentiment: "negative", reasoning: "competitive loss",
            },
        ],
        warnings: [],
        ...overrides,
    };
}

function financialsResponse(overrides = {}) {
    return {
        ticker: "ORCL",
        quarters: [
            {
                id: "27Q2", name: "Q2 2027", endingMonth: "2026-07", reportDate: "2026-08-27",
                revenue: 10240, grossProfit: 7890, operatingIncome: 2150, netIncome: 1720,
                capex: 210, freeCashFlow: 2980, dividend: 410, shares: 955, adjustedEps: 1.81,
                priceMin: 232.5, priceMax: 289.4,
            },
            {
                id: "27Q1", name: "Q1 2027", endingMonth: "2026-04", reportDate: "2026-05-28",
                revenue: 9870, grossProfit: 7580, operatingIncome: 1980, netIncome: 1560,
                capex: 195, freeCashFlow: 5310, dividend: 400, shares: 962, adjustedEps: 1.62,
                priceMin: 241.1, priceMax: 302.8,
            },
        ],
        notes: ["Dividend for Q1 2027 could not be verified."],
        warnings: ["Gemini returned 2 of 4 requested quarters."],
        ...overrides,
    };
}

function targetsResponse(overrides = {}) {
    return {
        ticker: "ORCL",
        from: "2026-08-14",
        to: "2026-09-14",
        institutions: ["Morgan Stanley", "UBS"],
        targets: [
            {
                institution: "Morgan Stanley", date: "2026-09-09", price: 320, rating: "Overweight",
                source: "https://ms.com", overview: "agentic seats beat plan",
                keyTakeaways: ["data cloud attach"],
            },
            {
                institution: "UBS", date: "2026-08-24", price: 285, rating: "Neutral",
                source: "https://ubs.com", overview: "priced in", keyTakeaways: [],
            },
        ],
        stats: {count: 2, minimum: 285, maximum: 320, average: 302.5},
        report: {overview: "merged view of both houses", keyTakeaways: ["they agree on the product cycle"]},
        warnings: [],
        ...overrides,
    };
}

function lookupResponse(overrides = {}) {
    return {
        ticker: "ORCL",
        found: true,
        inDatabase: false,
        inFirebase: false,
        name: "Oracle Corporation",
        description: "Cloud and database company.",
        website: "https://oracle.com",
        exchange: "XNYS",
        currency: "usd",
        locale: "us",
        type: "CS",
        active: true,
        industry: "SERVICES-PREPACKAGED SOFTWARE",
        listDate: "1986-03-12",
        marketCap: 655000000000,
        sharesOutstanding: 2800000000,
        employees: 159000,
        warnings: [],
        ...overrides,
    };
}

function arrangeRoutes({lookup, targets, financials, estimates, news}) {
    axios.get.mockImplementation(url => {
        const payload = url.endsWith("/targets") ? targets
            : url.endsWith("/financials") ? financials
            : url.endsWith("/estimates") ? (estimates ?? estimatesResponse())
            : url.endsWith("/news") ? (news ?? newsResponse())
            : lookup;
        if (payload === "pending") return new Promise(() => {});
        if (payload instanceof Error) return Promise.reject(payload);
        return Promise.resolve({data: payload});
    });
}

describe("Onboarding", () => {
    beforeEach(() => {
        axios.get.mockReset();
        axios.post.mockReset();
    });

    test("looks up a ticker and shows the company profile", async () => {
        arrangeRoutes({lookup: lookupResponse(), targets: "pending", financials: "pending"});

        render(<Onboarding/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "orcl"}});

        expect(screen.getByLabelText("Ticker")).toHaveValue("ORCL");

        fireEvent.click(screen.getByRole("button", {name: "Check"}));

        await waitFor(() => expect(axios.get).toHaveBeenCalledWith(
            "/api/onboarding/lookup", {params: {ticker: "ORCL"}}));

        const profile = await screen.findByTestId("onboarding-profile");
        expect(profile).toHaveTextContent("Oracle Corporation");
        expect(profile).toHaveTextContent("XNYS");
        expect(profile).toHaveTextContent("USD");
        expect(profile).toHaveTextContent("SERVICES-PREPACKAGED SOFTWARE");
        expect(profile).toHaveTextContent("655B");
        expect(profile).toHaveTextContent("2.8B");
        expect(profile).toHaveTextContent("159,000");
        expect(profile).toHaveTextContent("12.03.1986");
        expect(profile).toHaveTextContent("Cloud and database company.");
        expect(screen.queryByTestId("onboarding-known")).not.toBeInTheDocument();
        expect(screen.queryByTestId("onboarding-not-found")).not.toBeInTheDocument();
    });

    test("asks gemini for analyst research once the company is new", async () => {
        arrangeRoutes({lookup: lookupResponse(), targets: targetsResponse(), financials: financialsResponse()});

        render(<Onboarding/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "ORCL"}});
        fireEvent.click(screen.getByRole("button", {name: "Check"}));

        await waitFor(() => expect(axios.get).toHaveBeenCalledWith(
            "/api/onboarding/targets", {params: {ticker: "ORCL"}}));

        fireEvent.click(await screen.findByRole("tab", {name: /Analyst research/}));

        const research = await screen.findByTestId("onboarding-targets");
        expect(screen.getByTestId("onboarding-target-stats")).toHaveTextContent("2@(320-285)~302.5$");
        expect(screen.getByTestId("onboarding-targets-report")).toHaveTextContent("merged view of both houses");
        expect(research).toHaveTextContent("they agree on the product cycle");
        expect(screen.getAllByTestId("onboarding-target")).toHaveLength(2);
        expect(screen.getAllByTestId("onboarding-target")[0]).toHaveTextContent("Morgan Stanley");
        expect(screen.getAllByTestId("onboarding-target")[0]).toHaveTextContent("320$");
        expect(screen.queryByTestId("onboarding-targets-loading")).not.toBeInTheDocument();
    });

    test("shows an empty research result without targets", async () => {
        arrangeRoutes({
            lookup: lookupResponse(),
            targets: targetsResponse({
                targets: [],
                stats: {count: 0, minimum: null, maximum: null, average: null},
                report: {overview: "nothing published in the window", keyTakeaways: []},
            }),
            financials: financialsResponse(),
        });

        render(<Onboarding/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "ORCL"}});
        fireEvent.click(screen.getByRole("button", {name: "Check"}));

        fireEvent.click(await screen.findByRole("tab", {name: /Analyst research/}));

        expect(await screen.findByTestId("onboarding-targets-report"))
            .toHaveTextContent("nothing published in the window");
        expect(screen.queryByTestId("onboarding-target-stats")).not.toBeInTheDocument();
        expect(screen.queryByTestId("onboarding-target")).not.toBeInTheDocument();
    });

    test("shows the reported quarters from gemini", async () => {
        arrangeRoutes({lookup: lookupResponse(), targets: "pending", financials: financialsResponse()});

        render(<Onboarding/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "ORCL"}});
        fireEvent.click(screen.getByRole("button", {name: "Check"}));

        await waitFor(() => expect(axios.get).toHaveBeenCalledWith(
            "/api/onboarding/financials", {params: {ticker: "ORCL"}}));

        fireEvent.click(await screen.findByRole("tab", {name: /Financials/}));

        const table = await screen.findByTestId("onboarding-financials");
        const quarters = screen.getAllByTestId("onboarding-financials-quarter");
        expect(quarters.map(quarter => quarter.textContent)).toEqual(["Q1 2027", "Q2 2027"]);

        const rows = screen.getAllByTestId("onboarding-financials-row");
        const revenue = rows.find(row => row.textContent.startsWith("Revenue"));
        expect(revenue).toHaveTextContent("Revenue9.87B10.2B");
        expect(rows.find(row => row.textContent.startsWith("Adj. EPS"))).toHaveTextContent("1.81");
        expect(rows.find(row => row.textContent.startsWith("L - H"))).toHaveTextContent("232.5 - 289.4");
        expect(table).toHaveTextContent("Gemini returned 2 of 4 requested quarters.");
        expect(screen.getByTestId("onboarding-financials-notes"))
            .toHaveTextContent("Dividend for Q1 2027 could not be verified.");
    });

    test("reports a failing section without breaking the others", async () => {
        arrangeRoutes({
            lookup: lookupResponse(),
            targets: targetsResponse(),
            financials: Object.assign(new Error("boom"), {response: {status: 500}}),
        });

        render(<Onboarding/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "ORCL"}});
        fireEvent.click(screen.getByRole("button", {name: "Check"}));

        fireEvent.click(await screen.findByRole("tab", {name: /Analyst research/}));
        expect(await screen.findByTestId("onboarding-targets")).toBeInTheDocument();

        fireEvent.click(screen.getByRole("tab", {name: /Financials/}));

        expect(await screen.findByTestId("onboarding-financials-error")).toBeInTheDocument();
        expect(screen.queryByTestId("onboarding-targets")).not.toBeInTheDocument();
    });

    test("shows the eps and revenue projections from finnhub earnings", async () => {
        arrangeRoutes({
            lookup: lookupResponse(), targets: "pending", financials: "pending",
            estimates: estimatesResponse(), news: "pending",
        });

        render(<Onboarding/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "ORCL"}});
        fireEvent.click(screen.getByRole("button", {name: "Check"}));

        await waitFor(() => expect(axios.get).toHaveBeenCalledWith(
            "/api/onboarding/estimates", {params: {ticker: "ORCL"}}));

        fireEvent.click(await screen.findByRole("tab", {name: /Estimates/}));

        const projection = await screen.findByTestId("onboarding-estimates-projection");
        expect(projection).toHaveTextContent("6.39");
        expect(projection).toHaveTextContent("+26.3%");
        expect(screen.getByTestId("onboarding-estimates-reported")).toHaveTextContent("27Q2");
        expect(screen.getByTestId("onboarding-estimates-estimated")).toHaveTextContent("28Q2");

        const revenue = screen.getByTestId("onboarding-revenue-projection");
        expect(revenue).toHaveTextContent("39B");
        expect(revenue).toHaveTextContent("+14.9%");
        expect(screen.getByTestId("onboarding-revenue-reported")).toHaveTextContent("10.2B");
        expect(screen.getByTestId("onboarding-revenue-estimated")).toHaveTextContent("11.8B");
    });

    test("shows the news sentiment counts and articles", async () => {
        arrangeRoutes({
            lookup: lookupResponse(), targets: "pending", financials: "pending",
            estimates: "pending", news: newsResponse(),
        });

        render(<Onboarding/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "ORCL"}});
        fireEvent.click(screen.getByRole("button", {name: "Check"}));

        await waitFor(() => expect(axios.get).toHaveBeenCalledWith(
            "/api/onboarding/news", {params: {ticker: "ORCL"}}));

        fireEvent.click(await screen.findByRole("tab", {name: /News/}));

        const sentiment = await screen.findByTestId("onboarding-news-sentiment");
        expect(sentiment).toHaveTextContent("4 articles");
        expect(sentiment).toHaveTextContent("2 positive");
        expect(sentiment).toHaveTextContent("1 negative");
        expect(screen.getAllByTestId("onboarding-news-article")).toHaveLength(2);
        expect(screen.getAllByTestId("onboarding-news-article")[0])
            .toHaveTextContent("Agentforce expands to service teams");
    });

    test("loads every research tab up front and shows the selected one", async () => {
        arrangeRoutes({
            lookup: lookupResponse(), targets: targetsResponse(), financials: financialsResponse(),
            estimates: estimatesResponse(), news: newsResponse(),
        });

        render(<Onboarding/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "ORCL"}});
        fireEvent.click(screen.getByRole("button", {name: "Check"}));

        expect(await screen.findByTestId("onboarding-tab-overview")).toBeInTheDocument();
        expect(screen.getByTestId("onboarding-profile")).toHaveTextContent("Oracle Corporation");
        await waitFor(() => expect(axios.get).toHaveBeenCalledTimes(5));
        expect(screen.getAllByRole("tab")).toHaveLength(5);
        expect(screen.queryByTestId("onboarding-tab-news")).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("tab", {name: /News/}));

        expect(screen.getByTestId("onboarding-tab-news")).toBeInTheDocument();
        expect(screen.queryByTestId("onboarding-tab-overview")).not.toBeInTheDocument();
        expect(screen.getByTestId("onboarding-news-sentiment")).toHaveTextContent("4 articles");
    });

    test("pushes a new ticker to firebase once and then stays disabled", async () => {
        arrangeRoutes({
            lookup: lookupResponse(), targets: "pending", financials: "pending",
            estimates: "pending", news: "pending",
        });
        axios.post.mockResolvedValue({});

        render(<Onboarding/>);

        expect(screen.getByTestId("onboarding-push")).toBeDisabled();

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "ORCL"}});
        fireEvent.click(screen.getByRole("button", {name: "Check"}));

        await waitFor(() => expect(screen.getByTestId("onboarding-push")).not.toBeDisabled());

        fireEvent.click(screen.getByTestId("onboarding-push"));

        const dialog = await screen.findByRole("dialog");
        expect(dialog).toHaveTextContent("Add ORCL to Firebase?");
        expect(dialog).toHaveTextContent("kept up to date periodically");
        expect(axios.post).not.toHaveBeenCalled();

        fireEvent.click(screen.getByTestId("onboarding-push-confirm"));
        fireEvent.click(screen.getByTestId("onboarding-push-confirm"));

        await waitFor(() => expect(screen.getByTestId("onboarding-pushed")).toBeInTheDocument());
        expect(axios.post).toHaveBeenCalledTimes(1);
        expect(axios.post).toHaveBeenCalledWith(
            "/api/onboarding/firebase", null, {params: {ticker: "ORCL"}});
        expect(screen.getByTestId("onboarding-push")).toBeDisabled();
    });

    test("does not push when the confirmation is cancelled", async () => {
        arrangeRoutes({
            lookup: lookupResponse(), targets: "pending", financials: "pending",
            estimates: "pending", news: "pending",
        });

        render(<Onboarding/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "ORCL"}});
        fireEvent.click(screen.getByRole("button", {name: "Check"}));
        await waitFor(() => expect(screen.getByTestId("onboarding-push")).not.toBeDisabled());

        fireEvent.click(screen.getByTestId("onboarding-push"));
        fireEvent.click(await screen.findByRole("button", {name: "Cancel"}));

        await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
        expect(axios.post).not.toHaveBeenCalled();
        expect(screen.getByTestId("onboarding-push")).not.toBeDisabled();
    });

    test("keeps the push button disabled for a known or unknown ticker", async () => {
        arrangeRoutes({lookup: lookupResponse({
            ticker: "NVDA", found: false, inDatabase: true, inFirebase: false,
            name: null, description: null, website: null, currency: null, locale: null,
        })});

        render(<Onboarding/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "NVDA"}});
        fireEvent.click(screen.getByRole("button", {name: "Check"}));

        await screen.findByTestId("onboarding-known");
        expect(screen.getByTestId("onboarding-push")).toBeDisabled();
        expect(axios.post).not.toHaveBeenCalled();
    });

    test("re-enables the push button after checking another valid ticker", async () => {
        arrangeRoutes({
            lookup: lookupResponse(), targets: "pending", financials: "pending",
            estimates: "pending", news: "pending",
        });
        axios.post.mockResolvedValue({});

        render(<Onboarding/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "ORCL"}});
        fireEvent.click(screen.getByRole("button", {name: "Check"}));
        await waitFor(() => expect(screen.getByTestId("onboarding-push")).not.toBeDisabled());

        fireEvent.click(screen.getByTestId("onboarding-push"));
        fireEvent.click(await screen.findByTestId("onboarding-push-confirm"));
        await waitFor(() => expect(screen.getByTestId("onboarding-push")).toBeDisabled());
        await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "CRM"}});
        fireEvent.click(screen.getByRole("button", {name: "Check"}));

        await waitFor(() => expect(screen.getByTestId("onboarding-push")).not.toBeDisabled());
        expect(screen.queryByTestId("onboarding-pushed")).not.toBeInTheDocument();
    });

    test("does not ask gemini for a company that is already tracked", async () => {
        axios.get.mockResolvedValue({data: lookupResponse({
            ticker: "NVDA", found: false, inDatabase: true, inFirebase: false,
            name: null, description: null, website: null, currency: null, locale: null,
        })});

        render(<Onboarding/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "NVDA"}});
        fireEvent.click(screen.getByRole("button", {name: "Check"}));

        await screen.findByTestId("onboarding-known");
        expect(axios.get).toHaveBeenCalledTimes(1);
        expect(axios.get).toHaveBeenCalledWith("/api/onboarding/lookup", {params: {ticker: "NVDA"}});
    });

    test("stops at the notice when the company is already tracked", async () => {
        axios.get.mockResolvedValue({data: lookupResponse({
            ticker: "NVDA", found: false, inDatabase: true, inFirebase: true,
            name: null, description: null, website: null, currency: null, locale: null,
        })});

        render(<Onboarding/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "NVDA"}});
        fireEvent.click(screen.getByRole("button", {name: "Check"}));

        expect(await screen.findByTestId("onboarding-known"))
            .toHaveTextContent("NVDA is already tracked in the database and in Firebase. Nothing to onboard.");
        expect(screen.queryByTestId("onboarding-profile")).not.toBeInTheDocument();
        expect(screen.queryByTestId("onboarding-not-found")).not.toBeInTheDocument();
    });

    test("reports an unknown ticker and warnings from failing sources", async () => {
        axios.get.mockResolvedValue({data: lookupResponse({
            ticker: "XYZQ", found: false, name: null, description: null,
            website: null, currency: null, locale: null,
            warnings: ["Polygon company profile could not be loaded: down"],
        })});

        render(<Onboarding/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "XYZQ"}});
        fireEvent.click(screen.getByRole("button", {name: "Check"}));

        expect(await screen.findByTestId("onboarding-not-found")).toBeInTheDocument();
        expect(screen.getByText("Polygon company profile could not be loaded: down")).toBeInTheDocument();
        expect(screen.queryByTestId("onboarding-profile")).not.toBeInTheDocument();
    });

    test("rejects an invalid ticker without calling the backend", () => {
        render(<Onboarding/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "1$"}});

        expect(screen.getByText("Use a ticker like NVDA or BRK.B")).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Check"})).toBeDisabled();
        expect(axios.get).not.toHaveBeenCalled();
    });
});
