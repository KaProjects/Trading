import {Box, ButtonBase, Collapse} from "@mui/material";
import React, {useState} from "react";
import {formatDate, formatDecimals} from "../../service/FormattingService";
import {AssetProfit, formatProfitPercent, formatProfitValue} from "./AssetProfit";
import {DateTime} from "./DateTime";

const formatQuantity = asset => formatDecimals(Number(asset.quantity), 0, 4);

const formatPrice = (asset, currency) => `@${formatDecimals(Number(asset.purchasePrice), 0, 4)}${currency}`;

const formatAsset = (asset, currency) =>
    `${formatQuantity(asset)}@${formatDecimals(Number(asset.purchasePrice), 0, 4)}${currency}`;

const charWidth = values => `${Math.max(...values.map(value => value.length))}ch`;

export const AssetsSummary = ({latest, assets, currency, sx}) => {
    const [expanded, setExpanded] = useState(false);

    const list = assets?.assets ?? [];
    const aggregate = assets?.aggregate;

    if (!latest && !aggregate) return null;

    const profits = [aggregate, ...list].filter(Boolean);
    const percentWidth = charWidth(profits.map(asset => `(${formatProfitPercent(asset.profitPercent)})`));
    const valueWidth = charWidth(profits.map(asset => formatProfitValue(asset.profitValue, currency)));
    const quantityWidth = list.length > 0 ? charWidth(list.map(formatQuantity)) : undefined;
    const priceWidth = list.length > 0 ? charWidth(list.map(asset => formatPrice(asset, currency))) : undefined;

    return (
        <Box data-testid="record-assets" sx={{flexShrink: 0, ...sx}}>
            <ButtonBase
                aria-label={expanded ? "Hide assets" : "Show assets"}
                disabled={list.length === 0}
                onClick={() => setExpanded(!expanded)}
                sx={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    gap: "14px",
                    width: "100%",
                    padding: "2px 6px",
                    borderRadius: 2,
                    textAlign: "left",
                    "&.Mui-disabled": {opacity: 1},
                }}
            >
                {latest &&
                    <Box>
                        <Box sx={{color: "text.primary", fontSize: 20, fontWeight: 500, lineHeight: 1.25}}>
                            {currency}{formatDecimals(latest.price, 0, 2)}
                        </Box>
                        <DateTime
                            value={latest.datetime}
                            sx={{color: "text.secondary", fontSize: 11}}
                            iconMarginTop={"1px"}
                        />
                    </Box>
                }
                {aggregate &&
                    <Box
                        data-testid="record-assets-aggregate"
                        sx={{display: "flex", flexDirection: "column", alignItems: "flex-end", lineHeight: 1.25}}
                    >
                        <Box sx={{color: "text.secondary", fontSize: 17, fontWeight: 500, fontFamily: "Roboto"}}>
                            {formatAsset(aggregate, currency)}
                        </Box>
                        <AssetProfit
                            value={aggregate.profitValue}
                            percent={aggregate.profitPercent}
                            currency={currency}
                            valueWidth={valueWidth}
                            percentWidth={percentWidth}
                            testId="record-assets-aggregate-profit"
                            sx={{fontFamily: "Roboto", mt: "-3px"}}
                        />
                    </Box>
                }
            </ButtonBase>
            <Collapse in={expanded} unmountOnExit>
                <Box
                    data-testid="record-assets-list"
                    sx={{display: "flex", flexDirection: "column", gap: "2px", padding: "2px 6px 4px 6px"}}
                >
                    {list.map((asset, index) => (
                        <Box
                            key={index}
                            data-testid="record-assets-item"
                            sx={{display: "flex", alignItems: "center", justifyContent: "space-between", gap: "10px"}}
                        >
                            <Box sx={{color: "text.secondary", fontSize: 12, fontFamily: "Roboto", whiteSpace: "nowrap"}}>
                                {formatDate(asset.purchaseDate)}
                            </Box>
                            <Box sx={{display: "flex", color: "text.secondary", fontSize: 14, fontFamily: "Roboto"}}>
                                <Box component="span" sx={{width: quantityWidth, textAlign: "right"}}>
                                    {formatQuantity(asset)}
                                </Box>
                                <Box component="span" sx={{width: priceWidth}}>
                                    {formatPrice(asset, currency)}
                                </Box>
                            </Box>
                            <AssetProfit
                                value={asset.profitValue}
                                percent={asset.profitPercent}
                                currency={currency}
                                valueWidth={valueWidth}
                                percentWidth={percentWidth}
                                sx={{fontFamily: "Roboto"}}
                            />
                        </Box>
                    ))}
                </Box>
            </Collapse>
        </Box>
    );
}
