import {BorderedSection} from "./BorderedSection";
import React from "react";
import {Button, Stack, Typography} from "@mui/material";
import Tooltip from "@mui/material/Tooltip";
import {formatDate, formatDecimals, formatError, formatMillions, formatPeriodName} from "../../service/FormattingService";
import axios from "axios";
import {backend} from "../../properties";
import {ContentEditor} from "./ContentEditor";
import {ReactComponent as FinancialsPlusIcon} from "../../assets/icons/financials-plus.svg";
import {ReactComponent as EstimatesPlusIcon} from "../../assets/icons/estimates-plus.svg";

export const Period = ({period, currency, setAlert, openDialog}) => {

    function formatEndingMonth(endingMonth) {
        if (endingMonth === null || endingMonth === undefined) return "";
        return endingMonth.substring(5, 7) + "/" + endingMonth.substring(2, 4);
    }

    function formatPrice(price, currency) {
        if (price === null || price === undefined) return "";
        return price + currency;
    }

    function formatEstimate(estimate) {
        const formatValue = (value) => {
            if (value === null || value === undefined || value === "") return "-"
            return formatDecimals(value, 0, 2) || "-"
        }
        const past = [estimate.past4, estimate.past3, estimate.past2, estimate.past1]
            .map(formatValue)
            .join(" | ")
        const future = [estimate.next1, estimate.next2, estimate.next3]
            .map(formatValue)
            .join(" | ")
        return "Estimates: " + past + " |> " + formatValue(estimate.current) + " | " + future
    }

    function updateResearch(id, content) {
        return axios.put(backend + "/period", {id: id, research: JSON.stringify(content)})
            .then((response) => {})
            .catch((error) => {
                const formatted = formatError(error)
                setAlert(formatted)
                return formatted
            })
    }

    return (
        <BorderedSection
            title={formatPeriodName(period.name) + " - ending: " + formatEndingMonth(period.endingMonth) + " - report: " + formatDate(period.reportDate)}
            style={{color: 'text.primary'}}
        >
            <ContentEditor
                content={period.research}
                update={(value) => updateResearch(period.id, value)}
                style={{margin: "5px 5px 10px 5px"}}
            />

            {period.financial &&
                <>
                    <Typography sx={{color: 'text.secondary', fontSize: 14}}>
                        {"Shares: " + formatMillions(period.shares)
                            + " | H: " + formatPrice(period.priceHigh, currency)
                            + " | L: " + formatPrice(period.priceLow, currency)
                            + " | Dividend: " + formatMillions(period.financial.dividend)
                            + " | Adj. Eps: " + formatDecimals(period.financial.adjustedEps, 0, 2)}
                    </Typography>
                    <Typography sx={{color: 'text.secondary', fontSize: 14}} >
                        {"Revenue: " + formatMillions(period.financial.revenue.value)
                            + " | Gross P.: " + formatMillions(period.financial.grossProfit.value)
                            + " | Op. Inc.: " + formatMillions(period.financial.operatingIncome.value)
                            + " | Net Income: " + formatMillions(period.financial.netIncome.value)}
                    </Typography>
                </>
            }
            {period.estimate &&
                <Typography sx={{color: 'text.secondary', fontSize: 14}}>
                    {formatEstimate(period.estimate)}
                </Typography>
            }
            <Stack direction="column" justifyContent="flex-start" alignItems="center" spacing={1}
                   sx={{
                       position: "absolute", top: "6px", right: "8px", zIndex: 1, opacity: 0, pointerEvents: "none",
                       maxHeight: "calc(100% - 12px)", overflowY: "auto", overflowX: "hidden",
                       paddingRight: "8px", marginRight: "-8px",
                       transition: "opacity 120ms ease-in-out",
                       ".mainContainer:hover &": {opacity: 1, pointerEvents: "auto",},
                       "& .MuiButton-root": {minWidth: 0, padding: "2px", lineHeight: 0,},
                       "& svg": {width: "20px", height: "20px", display: "block",},
                   }}
            >
                {!period.financial &&
                    <Tooltip title="Add Financials" placement="left">
                        <Button onClick={openDialog}>
                            <FinancialsPlusIcon/>
                        </Button>
                    </Tooltip>
                }
                {!period.reportDate &&
                    <Tooltip title="Add Estimates" placement="left">
                        <Button>
                            <EstimatesPlusIcon/>
                        </Button>
                    </Tooltip>
                }
            </Stack>
        </BorderedSection>
    )
}
