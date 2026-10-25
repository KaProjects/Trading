import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

jest.mock("axios");
jest.mock("../../properties", () => ({backend: "/api"}));

const mockUseData = jest.fn();
const mockNavigate = jest.fn();

jest.mock("../../service/BackendService", () => ({
    useData: (...args) => mockUseData(...args),
}));
jest.mock("react-router-dom", () => ({
    useNavigate: () => mockNavigate,
    useParams: () => ({caseId: "1"}),
}));
jest.mock("../component/Loader", () => ({
    Loader: ({error}) => <div data-testid="loader">{error?.message ?? "loading"}</div>,
}));

import {Case} from "../Case";

const value = {
    id: 1,
    name: "memory supercycle",
    createdAt: "2026-02-10",
    closedAt: null,
    companies: [{id: 11, ticker: "NVDA"}],
};

const companyLists = {all: [{id: 11, ticker: "NVDA"}, {id: 12, ticker: "AMD"}]};

describe("Case", () => {
    beforeEach(() => {
        mockNavigate.mockReset();
        axios.put.mockReset();
        axios.put.mockResolvedValue({});
        axios.delete.mockReset();
        axios.delete.mockResolvedValue({});
        mockUseData.mockReturnValue({data: value, loaded: true, error: null});
    });

    test("shows the case, the day it opened and the companies it covers", () => {
        render(<Case companyLists={companyLists}/>);

        expect(screen.getByLabelText("Case name")).toHaveValue("memory supercycle");
        expect(screen.getByText("opened 10.02.2026")).toBeInTheDocument();
        expect(screen.getByText("NVDA")).toBeInTheDocument();
    });

    test("names the case again once the writing is done", async () => {
        render(<Case companyLists={companyLists}/>);

        const name = screen.getByLabelText("Case name");
        fireEvent.change(name, {target: {value: "memory cycle"}});
        fireEvent.blur(name);

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {name: "memory cycle"}));
    });

    test("covers another company, and stops covering one", async () => {
        render(<Case companyLists={companyLists}/>);

        fireEvent.click(screen.getByRole("button", {name: "Open"}));
        fireEvent.click(screen.getByRole("option", {name: "AMD"}));
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {companyIds: [11, 12]}));

        axios.put.mockClear();
        fireEvent.click(screen.getByTestId("CancelIcon"));
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {companyIds: []}));
    });

    test("closes the case, and reopens a closed one", async () => {
        render(<Case companyLists={companyLists}/>);
        fireEvent.click(screen.getByRole("button", {name: "Close"}));
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {closed: true}));

        axios.put.mockClear();
        mockUseData.mockReturnValue({data: {...value, closedAt: "2026-03-01"}, loaded: true, error: null});
        render(<Case companyLists={companyLists}/>);
        fireEvent.click(screen.getAllByRole("button", {name: "Reopen"})[0]);
        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("/api/case/1", {closed: false}));
    });

    test("goes back to the list once the case is deleted", async () => {
        render(<Case companyLists={companyLists}/>);

        fireEvent.click(screen.getByRole("button", {name: "Delete case"}));

        await waitFor(() => expect(axios.delete).toHaveBeenCalledWith("/api/case/1"));
        await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/cases"));
    });
});
