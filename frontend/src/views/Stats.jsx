import React from "react";
import {Box} from "@mui/material";
import {CompanyStats} from "./component/CompanyStats";
import {PeriodStats} from "./component/PeriodStats";
import {ProfitLossStats} from "./component/ProfitLossStats";
import {SWIPE_AREA_SX, useSwipeNavigation} from "./component/useSwipeNavigation";

const STATS_TYPES = {company: "company", monthly: "monthly", quarterly: "quarterly", yearly: "yearly"}

const STATS_TAB_COUNT = 5

export const Stats = props => {
    const swipe = useSwipeNavigation(props.statsTabsIndex, props.setStatsTabsIndex, STATS_TAB_COUNT)

    return (
    <Box sx={SWIPE_AREA_SX} {...swipe}>
        {props.statsTabsIndex === 0 && (
            <CompanyStats type={STATS_TYPES.company} {...props} />
        )}
        {props.statsTabsIndex === 1 && (
            <PeriodStats type={STATS_TYPES.monthly} {...props} />
        )}
        {props.statsTabsIndex === 2 && (
            <PeriodStats type={STATS_TYPES.quarterly} {...props} />
        )}
        {props.statsTabsIndex === 3 && (
            <PeriodStats type={STATS_TYPES.yearly} {...props} />
        )}
        {props.statsTabsIndex === 4 && (
            <ProfitLossStats {...props}/>
        )}
    </Box>
    )
}
