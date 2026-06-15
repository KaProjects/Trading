import {BorderedSection} from "./BorderedSection";
import React from "react";
import {Button, Typography} from "@mui/material";
import Tooltip from "@mui/material/Tooltip";
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import {formatDate, formatError, formatMillions, formatPeriodName} from "../../service/FormattingService";
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
                style={{margin: "15px 0 0 0"}}
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
                        {"Revenue: " + formatMillions(period.financial.revenue)
                            + " | Gross P.: " + formatMillions(period.financial.grossProfit)
                            + " | Op. Inc.: " + formatMillions(period.financial.operatingIncome)
                            + " | Net Income: " + formatMillions(period.financial.netIncome)}
                    </Typography>
                </>
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