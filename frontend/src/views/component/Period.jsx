import {BorderedSection} from "./BorderedSection";
import React from "react";
import {Button, Typography} from "@mui/material";
import Tooltip from "@mui/material/Tooltip";
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import {formatDate, formatDecimals, formatError, formatMillions, formatPeriodName} from "../../service/FormattingService";
import axios from "axios";
import {backend} from "../../properties";
import {ContentEditor} from "./ContentEditor";

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
        const future = [estimate.next1, estimate.next2, estimate.next3]
            .filter(value => value !== null && value !== undefined)
            .map(value => " | " + formatDecimals(value, 0, 2))
            .join("")
        return "Estimates: " + formatDecimals(estimate.ttm, 0, 2)
            + " |> " + formatDecimals(estimate.current, 0, 2)
            + future
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
                            + " | Dividend: " + formatMillions(period.financial.dividend)}
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
            {!period.financial &&
                <Tooltip title="Add Financials">
                    <Button sx={{height: "25px"}} onClick={openDialog}>
                        <ControlPointIcon sx={{color: 'lightgreen'}}/>
                    </Button>
                </Tooltip>
            }
        </BorderedSection>
    )
}
