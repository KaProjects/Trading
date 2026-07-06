import React from "react";
import {CompanyStats} from "./component/CompanyStats";
import {PeriodStats} from "./component/PeriodStats";

const STATS_TYPES = {company: "company", monthly: "monthly", quarterly: "quarterly", yearly: "yearly"}

export const Stats = props => (
    <>
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
    </>
)
