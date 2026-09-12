import {Box, Button, Tooltip, Typography} from "@mui/material";
import React from "react";
import {validateNumber} from "../../service/ValidationService";
import {DialogTextField} from "./DialogTextField";

export const EMPTY_PERIOD_FINANCIAL_VALUES = {
    shares: "",
    revenue: "",
    grossProfit: "",
    operatingIncome: "",
    netIncome: "",
    dividend: "",
    adjustedEps: "",
    priceHigh: "",
    priceLow: "",
    capex: "",
    freeCashFlow: "",
}

const SOURCE_STYLE = {
    firebase: {label: "Gemini", color: "success"},
    polygon: {label: "Polygon.io", color: "info"},
    alphaVantage: {label: "Alpha Vantage", color: "warning"},
}

const FINANCIAL_GRID_STYLE = {
    display: "grid",
    gridTemplateColumns: "minmax(230px, 1fr) repeat(3, 92px)",
    columnGap: 1,
    alignItems: "center",
}

const EDIT_FINANCIAL_GRID_STYLE = {
    display: "grid",
    gridTemplateColumns: "minmax(230px, 1fr)",
    alignItems: "center",
}

const FINANCIAL_FIELDS = [
    {
        key: "shares",
        id: "company-financial-shares",
        label: "Shares (in Millions)",
        required: true,
        integerConstraint: 8,
        decimalConstraint: 2,
        allowNegative: false,
    },
    {
        key: "revenue",
        id: "company-financial-revenue",
        label: "Revenue (in Millions)",
        required: true,
        integerConstraint: 8,
        decimalConstraint: 2,
        allowNegative: false,
    },
    {
        key: "grossProfit",
        id: "company-financial-gross-profit",
        label: "Gross Profit (in Millions)",
        integerConstraint: 8,
        decimalConstraint: 2,
        allowNegative: true,
    },
    {
        key: "operatingIncome",
        id: "company-financial-operating-income",
        label: "Operating Income (in Millions)",
        integerConstraint: 8,
        decimalConstraint: 2,
        allowNegative: true,
    },
    {
        key: "netIncome",
        id: "company-financial-net-income",
        label: "Net Income (in Millions)",
        required: true,
        integerConstraint: 8,
        decimalConstraint: 2,
        allowNegative: true,
    },
    {
        key: "adjustedEps",
        id: "company-financial-adjusted-eps",
        label: "Adjusted EPS",
        integerConstraint: 6,
        decimalConstraint: 2,
        allowNegative: true,
    },
    {
        key: "priceHigh",
        id: "trader-period-price-high",
        label: "Highest Price",
        integerConstraint: 10,
        decimalConstraint: 4,
        allowNegative: false,
    },
    {
        key: "priceLow",
        id: "trader-period-price-low",
        label: "Lowest Price",
        integerConstraint: 10,
        decimalConstraint: 4,
        allowNegative: false,
    },
    {
        key: "capex",
        id: "company-financial-capex",
        label: "CapEx (in Millions)",
        integerConstraint: 8,
        decimalConstraint: 2,
        allowNegative: true,
    },
    {
        key: "dividend",
        id: "company-financial-dividend",
        label: "Dividend (in Millions)",
        integerConstraint: 8,
        decimalConstraint: 2,
        allowNegative: false,
    },
    {
        key: "freeCashFlow",
        id: "company-financial-free-cash-flow",
        label: "Free Cash Flow (in Millions)",
        integerConstraint: 8,
        decimalConstraint: 2,
        allowNegative: true,
    },
]

export const roundedSuggestion = (value, decimals) => {
    if (value === null || value === undefined || value === "") return value

    const number = Number(value)
    if (!Number.isFinite(number) || !Number.isInteger(decimals)) return value

    return String(Number(number.toFixed(decimals)))
}

const SourceSuggestion = ({source, fieldLabel, value, apply}) => {
    const hasValue = value !== null && value !== undefined && value !== ""

    return (
        <Box sx={{minWidth: 92}}>
            {hasValue &&
                <Tooltip title={`Use ${SOURCE_STYLE[source].label} value for ${fieldLabel}`}>
                    <Button
                        fullWidth
                        color={SOURCE_STYLE[source].color}
                        onClick={apply}
                    >
                        {`<< ${value}`}
                    </Button>
                </Tooltip>
            }
        </Box>
    )
}

const FinancialField = ({field, values, setValues, suggestions, setSuggestions, clearAlert, showSuggestions}) => {
    function suggestionValue(source) {
        return roundedSuggestion(suggestions[source]?.[field.key], field.decimalConstraint)
    }

    function applySuggestion(source) {
        setValues(previous => ({...previous, [field.key]: suggestionValue(source)}))
        clearAlert()
        setSuggestions(previous => ({
            ...previous,
            [source]: {...previous[source], [field.key]: undefined},
        }))
    }

    return (
        <Box sx={showSuggestions ? FINANCIAL_GRID_STYLE : EDIT_FINANCIAL_GRID_STYLE}>
            <DialogTextField
                id={field.id}
                value={values[field.key]}
                label={field.label}
                required={field.required === true}
                sx={{minWidth: 230}}
                onChange={(event) => {
                    setValues(previous => ({
                        ...previous,
                        [field.key]: event.target.value,
                    }))
                    clearAlert()
                }}
                validate={() => validateNumber(
                    values[field.key],
                    !field.required,
                    field.integerConstraint,
                    field.decimalConstraint,
                    field.allowNegative,
                )}
            />
            {showSuggestions &&
                <>
                    <SourceSuggestion
                        source="firebase"
                        fieldLabel={field.label}
                        value={suggestionValue("firebase")}
                        apply={() => applySuggestion("firebase")}
                    />
                    <SourceSuggestion
                        source="polygon"
                        fieldLabel={field.label}
                        value={suggestionValue("polygon")}
                        apply={() => applySuggestion("polygon")}
                    />
                    <SourceSuggestion
                        source="alphaVantage"
                        fieldLabel={field.label}
                        value={suggestionValue("alphaVantage")}
                        apply={() => applySuggestion("alphaVantage")}
                    />
                </>
            }
        </Box>
    )
}

export const PeriodFinancialFields = ({
    values,
    setValues,
    suggestions,
    setSuggestions,
    clearAlert,
    showSuggestions = true,
}) => (
    <Box>
        {showSuggestions &&
            <Box sx={FINANCIAL_GRID_STYLE}>
                <Box />
                {Object.entries(SOURCE_STYLE).map(([source, style]) => (
                    <Tooltip key={source} title={`${style.label} suggestions`}>
                        <Typography
                            sx={{textAlign: "center", fontWeight: 600}}
                            color={`${style.color}.main`}
                        >
                            {style.label}
                        </Typography>
                    </Tooltip>
                ))}
            </Box>
        }

        <Box sx={{display: "flex", flexDirection: "column", gap: 0.5}}>
            {FINANCIAL_FIELDS.map(field => (
                <FinancialField
                    key={field.key}
                    field={field}
                    values={values}
                    setValues={setValues}
                    suggestions={suggestions}
                    setSuggestions={setSuggestions}
                    clearAlert={clearAlert}
                    showSuggestions={showSuggestions}
                />
            ))}
        </Box>
    </Box>
)

export const toNullableFinancialValues = values => Object.fromEntries(
    Object.entries(values).map(([key, value]) => [key, value === "" ? null : value])
)
