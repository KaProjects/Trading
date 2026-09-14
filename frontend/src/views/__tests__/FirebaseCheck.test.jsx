import {fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import axios from "axios";

const mockNavigate = jest.fn();

jest.mock("axios");
jest.mock("../../properties", () => ({
    backend: "/api",
}));
jest.mock("react-router-dom", () => ({
    ...jest.requireActual("react-router-dom"),
    useNavigate: () => mockNavigate,
}));
jest.mock("../component/Loader", () => ({
    Loader: ({error}) => <div data-testid="loader">{error ? JSON.stringify(error) : "loading"}</div>,
}));
jest.mock("../../dialog/EditCompanyDialog", () => ({
    EditCompanyDialog: () => <div data-testid="edit-company-dialog"/>,
}));

import {FirebaseCheck} from "../FirebaseCheck";

function company(overrides = {}) {
    return {
        ticker: "NVDA",
        inDatabase: true,
        enabled: true,
        geminiQuarters: 7,
        geminiTargets: 33,
        finnhubEarnings: 8,
        newsSentiments: 3,
        importablePeriods: 0,
        importableTargets: 0,
        ...overrides,
    };
}

function arrangeStats(companies) {
    axios.get.mockImplementation(url => {
        if (url.endsWith("/firebase/stats")) {
            return Promise.resolve({data: {companies, warnings: []}});
        }
        if (url.endsWith("/firebase/companies")) {
            return Promise.resolve({data: {onlyInFirebase: [], warnings: []}});
        }
        return Promise.resolve({data: {institutions: [], warnings: []}});
    });
}

function renderPage(tabIndex) {
    return render(
        <FirebaseCheck
            firebaseTabsIndex={tabIndex}
            setFirebaseTabsIndex={jest.fn()}
            setOpenEditCompany={jest.fn()}
        />
    );
}

describe("FirebaseCheck cache", () => {
    beforeEach(() => {
        axios.get.mockReset();
        axios.put.mockReset();
        mockNavigate.mockReset();
    });

    test("shows importable counts inside the quarters and targets columns", async () => {
        arrangeStats([
            company({ticker: "NVDA", importablePeriods: 1, importableTargets: 12}),
            company({ticker: "AMZN", inDatabase: false, geminiQuarters: 6, geminiTargets: 17}),
        ]);

        renderPage(1);

        const rows = await screen.findAllByTestId("firebase-stats-row");
        expect(rows).toHaveLength(2);
        expect(rows[0]).toHaveTextContent("NVDA(1)7(12)33");
        expect(within(rows[0]).getByTestId("firebase-import-periods")).toBeInTheDocument();
        expect(within(rows[0]).getByTestId("firebase-import-targets")).toBeInTheDocument();
        expect(rows[1]).toHaveTextContent("AMZN617");
        expect(within(rows[1]).queryByTestId("firebase-import-periods")).not.toBeInTheDocument();
        expect(within(rows[1]).queryByTestId("firebase-import-targets")).not.toBeInTheDocument();
    });

    test("filters to companies with something left to import", async () => {
        arrangeStats([
            company({ticker: "NVDA", importablePeriods: 1}),
            company({ticker: "AMZN", inDatabase: false}),
        ]);

        renderPage(1);

        expect(await screen.findAllByTestId("firebase-stats-row")).toHaveLength(2);

        fireEvent.click(screen.getByRole("button", {name: "not imported"}));

        const rows = screen.getAllByTestId("firebase-stats-row");
        expect(rows).toHaveLength(1);
        expect(rows[0]).toHaveTextContent("NVDA");
        expect(screen.getByText("1 of 2 companies with something left to import")).toBeInTheDocument();
    });

    test("redirects to research from a cell with something to import", async () => {
        arrangeStats([
            company({ticker: "NVDA", importablePeriods: 1, importableTargets: 12}),
            company({ticker: "AMZN", importablePeriods: 3, inDatabase: false}),
            company({ticker: "MSFT"}),
        ]);

        renderPage(1);

        const rows = await screen.findAllByTestId("firebase-stats-row");
        expect(within(rows[1]).queryByTestId("firebase-import-periods")).not.toBeInTheDocument();
        expect(within(rows[2]).queryByTestId("firebase-import-targets")).not.toBeInTheDocument();

        fireEvent.click(within(rows[0]).getByTestId("firebase-import-targets"));

        expect(mockNavigate).toHaveBeenCalledWith({pathname: "/research", search: "?company=NVDA"});
        expect(axios.get).not.toHaveBeenCalledWith("/api/firebase/company/NVDA");
    });

    test("opens the firebase detail from a cell with nothing to import", async () => {
        arrangeStats([company({ticker: "NVDA", importablePeriods: 1})]);

        renderPage(1);

        const row = await screen.findByTestId("firebase-stats-row");
        fireEvent.click(within(row).getByText("8"));

        await waitFor(() => expect(axios.get).toHaveBeenCalledWith("/api/firebase/company/NVDA"));
        expect(mockNavigate).not.toHaveBeenCalled();
    });
});

describe("FirebaseCheck companies", () => {
    beforeEach(() => {
        axios.get.mockReset();
        axios.put.mockReset();
        mockNavigate.mockReset();
    });

    test("toggles the enabled flag of a company", async () => {
        arrangeStats([company({ticker: "NVDA"})]);
        axios.put.mockResolvedValue({});

        renderPage(0);

        const row = await screen.findByTestId("firebase-company-NVDA");
        fireEvent.click(within(row).getByRole("switch"));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith(
            "/api/firebase/company/NVDA/enabled", {enabled: false}));
        expect(within(row).getByRole("switch")).not.toBeChecked();
    });

    test("reverts the flag when the update fails", async () => {
        arrangeStats([company({ticker: "NVDA"})]);
        axios.put.mockRejectedValue(new Error("nope"));

        renderPage(0);

        const row = await screen.findByTestId("firebase-company-NVDA");
        fireEvent.click(within(row).getByRole("switch"));

        await waitFor(() => expect(within(row).getByRole("switch")).toBeChecked());
    });

    test("filters companies by the enabled flag", async () => {
        arrangeStats([
            company({ticker: "NVDA"}),
            company({ticker: "AMZN", enabled: false}),
        ]);

        renderPage(0);

        expect(await screen.findByTestId("firebase-company-NVDA")).toBeInTheDocument();
        expect(screen.getByTestId("firebase-company-AMZN")).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "disabled"}));

        expect(screen.queryByTestId("firebase-company-NVDA")).not.toBeInTheDocument();
        expect(screen.getByTestId("firebase-company-AMZN")).toBeInTheDocument();
        expect(screen.getByText("1 of 2 companies in Firebase")).toBeInTheDocument();
    });
});
