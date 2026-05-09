import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import axios from "axios";

const mockFormatError = jest.fn(() => ({title: "Save failed", message: "Company could not be saved"}));
const dialogTextFieldModule = {
    DialogTextField: ({id, label, value = "", onChange, validate, required = true, ...props}) => {
        const error = validate ? validate() : "";

        return (
            <div>
                <label htmlFor={id}>{label || id}</label>
                <input
                    id={id}
                    aria-label={label || id}
                    data-testid={id}
                    value={value ?? ""}
                    onChange={onChange}
                    required={required}
                    {...props}
                />
                {error && <span>{error}</span>}
            </div>
        );
    },
};

jest.mock("axios");
jest.mock("../../properties", () => ({
    backend: "http://backend",
}));
jest.mock("../../service/FormattingService", () => ({
    formatError: (...args) => mockFormatError(...args),
}));
jest.mock("../component/DialogTextField", () => dialogTextFieldModule);

import EditCompanyDialog from "../EditCompanyDialog";

function selectOption(index, optionText) {
    fireEvent.mouseDown(screen.getAllByRole("combobox")[index]);
    fireEvent.click(screen.getByRole("option", {name: optionText}));
}

function createProps(overrides = {}) {
    return {
        openEditCompany: null,
        setOpenEditCompany: jest.fn(),
        triggerRefresh: jest.fn(),
        currencies: ["$", "€"],
        sectors: [
            {key: "SEMICONDUCTORS", name: "Semiconductors"},
            {key: "ENERGY", name: "Energy"},
        ],
        ...overrides,
    };
}

describe("EditCompanyDialog", () => {
    beforeEach(() => {
        axios.post.mockReset();
        axios.put.mockReset();
        mockFormatError.mockClear();
    });

    test("creates a company in add mode", async () => {
        axios.post.mockResolvedValue({});

        const props = createProps({
            openEditCompany: {},
        });

        render(<EditCompanyDialog {...props}/>);

        fireEvent.change(screen.getByLabelText("Ticker"), {target: {value: "NVDA"}});
        selectOption(0, "€");
        selectOption(1, "Semiconductors");
        fireEvent.click(screen.getByRole("checkbox", {name: "controlled"}));
        fireEvent.click(screen.getByText("Create"));

        await waitFor(() => expect(axios.post).toHaveBeenCalledWith("http://backend/company", {
            ticker: "NVDA",
            currency: "€",
            watching: false,
            sector: "SEMICONDUCTORS",
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.setOpenEditCompany).toHaveBeenCalledWith(null);
    });

    test("updates a company in edit mode", async () => {
        axios.put.mockResolvedValue({});

        const props = createProps({
            openEditCompany: {
                id: "company-1",
                ticker: "NVDA",
                currency: "$",
                watching: true,
                sector: {key: "SEMICONDUCTORS"},
            },
        });

        render(<EditCompanyDialog {...props}/>);

        expect(screen.queryByLabelText("Ticker")).not.toBeInTheDocument();

        fireEvent.click(screen.getByText("Edit"));

        await waitFor(() => expect(axios.put).toHaveBeenCalledWith("http://backend/company", {
            id: "company-1",
            ticker: "NVDA",
            currency: "$",
            watching: true,
            sector: "SEMICONDUCTORS",
        }));
        expect(props.triggerRefresh).toHaveBeenCalled();
        expect(props.setOpenEditCompany).toHaveBeenCalledWith(null);
    });
});
