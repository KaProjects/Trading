import React, {useState} from "react";
import {
    Box,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TableSortLabel,
    Typography,
} from "@mui/material";
import {useData} from "../service/BackendService";
import {Loader} from "./component/Loader";
import {STICKY_COLUMN_BODY_SX, STICKY_COLUMN_HEAD_SX, TABLE_CONTAINER_SX} from "./component/tableStyles";
import {SWIPE_AREA_SX, useSwipeNavigation} from "./component/useSwipeNavigation";
import {formatDecimals, formatMillions, formatPercent} from "../service/FormattingService";

const EPS_COLUMNS = [
    {key: "ticker", label: "Ticker", align: "left"},
    {key: "ttmEps", label: "TTM EPS", format: value => formatDecimals(value, 0, 2)},
    {key: "quarter1Change", label: "+1Q change", format: value => formatPercent(value, true, 2)},
    {key: "quarter2Change", label: "+2Q change", format: value => formatPercent(value, true, 2)},
    {key: "quarter3Change", label: "+3Q change", format: value => formatPercent(value, true, 2)},
    {key: "quarter4Change", label: "+4Q change", format: value => formatPercent(value, true, 2)},
];

const MARGIN_COLUMNS = [
    {key: "ticker", label: "Ticker", align: "left"},
    {key: "revenue", label: "Revenue (TTM)", format: value => formatMillions(value)},
    {key: "grossMargin", label: "Gross margin", format: value => formatPercent(value, false, 2)},
    {key: "operatingMargin", label: "Operating margin", format: value => formatPercent(value, false, 2)},
    {key: "netMargin", label: "Net margin", format: value => formatPercent(value, false, 2)},
];

const SENTIMENT_COLUMNS = [
    {key: "ticker", label: "Ticker", align: "left"},
    {key: "articleCount", label: "Articles (31d)"},
    {key: "positiveCount", label: "Positive"},
    {key: "neutralCount", label: "Neutral"},
    {key: "negativeCount", label: "Negative"},
    {key: "weightedSentimentSum", label: "Weighted sum"},
    {key: "sentimentScore", label: "Sentiment score", format: value => formatDecimals(value, 0, 2)},
];

const TARGET_COLUMNS = [
    {key: "ticker", label: "Ticker", align: "left"},
    {key: "price", label: "Price", format: value => formatDecimals(value, 0, 2)},
    {key: "averageTarget", label: "Avg. target", format: value => formatDecimals(value, 0, 2)},
    {key: "percentDiff", label: "Avg. target vs price", format: value => formatPercent(value, true, 2)},
];

const TAB_CONFIG = [
    {
        dataKey: "epsEstimates", disqualifiedKey: "epsEstimatesDisqualified", columns: EPS_COLUMNS,
        defaultSort: {key: "quarter4Change", direction: "desc"},
        criteria: {
            hard: [
                "The company has at least one period, and an estimate snapshot has been recorded for its latest period.",
            ],
            soft: [
                "The estimate snapshot is no more than 3 months old.",
                "At least one of the four forward-looking changes can be computed (needs 4 quarters of reported EPS history plus the estimate).",
            ],
        },
    },
    {
        dataKey: "margins", disqualifiedKey: "marginsDisqualified", columns: MARGIN_COLUMNS,
        defaultSort: {key: "grossMargin", direction: "desc"},
        criteria: {
            hard: [
                "The company has at least one reported period (financials on file).",
            ],
            soft: [
                "The latest reported period's report date is no more than 3 months old.",
                "At least 4 consecutive quarterly (Q1–Q4) reports are available; fewer would make the TTM figures unreliable.",
            ],
        },
    },
    {
        dataKey: "sentiment", disqualifiedKey: "sentimentDisqualified", columns: SENTIMENT_COLUMNS,
        defaultSort: {key: "sentimentScore", direction: "desc"},
        criteria: {
            hard: [
                "Firebase has at least one news-sentiment record for the company.",
            ],
            soft: [
                "At least 5 articles were recorded in the last 31 days.",
            ],
        },
    },
    {
        dataKey: "targets", disqualifiedKey: "targetsDisqualified", columns: TARGET_COLUMNS,
        defaultSort: {key: "percentDiff", direction: "desc"},
        criteria: {
            hard: [
                "At least one price target has ever been recorded for the company.",
            ],
            soft: [
                "At least 5 targets were recorded in the last 3 months.",
                "A cached price exists and is no more than 7 days old.",
            ],
        },
    },
];

function sortRows(rows, sort) {
    if (!sort) return rows;
    const {key, direction} = sort;
    const factor = direction === "asc" ? 1 : -1;
    return [...rows].sort((a, b) => {
        const first = a[key];
        const second = b[key];
        if (first === null || first === undefined) return 1;
        if (second === null || second === undefined) return -1;
        if (typeof first === "string") return factor * first.localeCompare(second);
        return factor * (first - second);
    });
}

function RatingCriteria({criteria}) {
    return (
        <Box sx={{
            width: {xs: "100%", sm: "max-content"},
            minWidth: {sm: 320},
            margin: "16px auto",
            padding: "0 8px",
            boxSizing: "border-box",
        }}>
            <Typography sx={{fontSize: 13, fontWeight: 600, color: "text.secondary"}}>
                Rating criteria
            </Typography>
            <Typography sx={{fontSize: 12, fontWeight: 600, color: "text.secondary", marginTop: "8px"}}>
                To be considered at all:
            </Typography>
            <Box component="ul" sx={{margin: "2px 0", paddingLeft: "20px"}}>
                {criteria.hard.map(text => (
                    <Typography key={text} component="li" sx={{fontSize: 12, color: "text.secondary"}}>
                        {text}
                    </Typography>
                ))}
            </Box>
            <Typography sx={{fontSize: 12, fontWeight: 600, color: "text.secondary", marginTop: "8px"}}>
                To qualify for the ranking above:
            </Typography>
            <Box component="ul" sx={{margin: "2px 0", paddingLeft: "20px"}}>
                {criteria.soft.map(text => (
                    <Typography key={text} component="li" sx={{fontSize: 12, color: "text.secondary"}}>
                        {text}
                    </Typography>
                ))}
            </Box>
        </Box>
    );
}

function OutperformersTable({columns, rows, disqualified, criteria, sort, setSort}) {
    function handleSort(columnKey) {
        setSort(previous => ({
            key: columnKey,
            direction: previous?.key === columnKey && previous.direction === "desc" ? "asc" : "desc",
        }));
    }

    const sortedRows = sortRows(rows, sort);

    return (
        <>
            <TableContainer component={Paper} sx={TABLE_CONTAINER_SX}>
                <Table size="small" stickyHeader>
                    <TableHead>
                        <TableRow>
                            {columns.map((column, index) => (
                                <TableCell
                                    key={column.key}
                                    align={column.align ?? "right"}
                                    sx={index === 0 ? STICKY_COLUMN_HEAD_SX : undefined}
                                >
                                    <TableSortLabel
                                        active={sort?.key === column.key}
                                        direction={sort?.key === column.key ? sort.direction : "desc"}
                                        onClick={() => handleSort(column.key)}
                                    >
                                        {column.label}
                                    </TableSortLabel>
                                </TableCell>
                            ))}
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {sortedRows.map(row => (
                            <TableRow key={row.ticker}>
                                {columns.map((column, index) => (
                                    <TableCell
                                        key={column.key}
                                        align={column.align ?? "right"}
                                        sx={index === 0 ? STICKY_COLUMN_BODY_SX : undefined}
                                    >
                                        {column.key === "ticker"
                                            ? row.ticker
                                            : (column.format ? column.format(row[column.key]) : row[column.key])}
                                    </TableCell>
                                ))}
                            </TableRow>
                        ))}
                        {sortedRows.length === 0 &&
                            <TableRow>
                                <TableCell colSpan={columns.length} align="center">No qualifying companies</TableCell>
                            </TableRow>
                        }
                    </TableBody>
                </Table>
            </TableContainer>

            <RatingCriteria criteria={criteria}/>

            {disqualified.length > 0 &&
                <TableContainer component={Paper} sx={TABLE_CONTAINER_SX}>
                    <Table size="small">
                        <TableHead>
                            <TableRow>
                                <TableCell colSpan={2}>
                                    <Typography sx={{fontSize: 13, color: "text.secondary"}}>
                                        Met the basic criteria, but not currently qualified
                                    </Typography>
                                </TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {disqualified.map(entry => (
                                <TableRow key={entry.ticker}>
                                    <TableCell sx={{fontWeight: 600}}>{entry.ticker}</TableCell>
                                    <TableCell sx={{color: "text.secondary"}}>{entry.reason}</TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </TableContainer>
            }
        </>
    );
}

export const Outperformers = props => {
    const {data, loaded, error} = useData("/outperformers");
    const [sorts, setSorts] = useState(TAB_CONFIG.map(config => config.defaultSort));
    const tab = props.outperformersTabsIndex ?? 0;
    const swipe = useSwipeNavigation(tab, props.setOutperformersTabsIndex, TAB_CONFIG.length);

    if (!loaded) return <Loader error={error}/>;

    function setSortForTab(index, updater) {
        setSorts(previous => previous.map((sort, i) => (
            i === index ? (typeof updater === "function" ? updater(sort) : updater) : sort
        )));
    }

    const activeConfig = TAB_CONFIG[tab];

    return (
        <Box
            sx={{
                ...SWIPE_AREA_SX,
                minHeight: 0,
                maxHeight: {
                    xs: "calc(100dvh - var(--main-bar-height, 48px) - 8px)",
                    sm: "calc(100dvh - var(--main-bar-height, 48px) - 36px)",
                },
                overflowY: "auto",
                overscrollBehavior: "contain",
                margin: {xs: 0, sm: "10px"},
                "& > *": {flexShrink: 0},
            }}
            {...swipe}
        >
            <OutperformersTable
                columns={activeConfig.columns}
                rows={data[activeConfig.dataKey] ?? []}
                disqualified={data[activeConfig.disqualifiedKey] ?? []}
                criteria={activeConfig.criteria}
                sort={sorts[tab]}
                setSort={updater => setSortForTab(tab, updater)}
            />
        </Box>
    );
};
