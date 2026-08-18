import {Chip, Typography} from "@mui/material";
import React from "react";
import {CsvImportPage} from "./component/CsvImportPage";

export const TRADE_IMPORT_TEMPLATE = `date,type,ticker,quantity,price,fees,portfolio
2026-01-10,BUY,NVDA,5,145.50,4.95,PATRIA_STANDARD
2026-02-15,BUY,NVDA,3,152.25,4.95,PATRIA_STANDARD
2026-04-01,SELL,NVDA,8,180.00,6.95,PATRIA_STANDARD`;

const columns = [
    {key: "rowNumber", label: "Row"},
    {key: "date", label: "Date", cellSx: {whiteSpace: "nowrap"}},
    {
        key: "type",
        label: "Type",
        render: row => (
            <Chip
                size="small"
                label={row.type}
                color={row.type === "BUY" ? "success" : "warning"}
                variant="outlined"
            />
        ),
    },
    {key: "ticker", label: "Ticker", cellSx: {fontWeight: 700}},
    {key: "quantity", label: "Quantity", align: "right"},
    {key: "price", label: "Price", align: "right"},
    {key: "fees", label: "Fees", align: "right"},
    {key: "portfolio", label: "Portfolio"},
    {
        key: "allocations",
        label: "FIFO allocations",
        sx: {minWidth: 220},
        render: row => row.allocations?.length > 0
            ? row.allocations.map((allocation, index) => (
                <Typography variant="caption" component="div" key={`${allocation.source}-${index}`}>
                    {allocation.source}: {allocation.quantity} ({allocation.purchaseDate})
                </Typography>
            ))
            : <Typography variant="caption" color="text.secondary">-</Typography>,
    },
    {
        key: "remainingQuantity",
        label: "Remaining",
        align: "right",
        render: row => row.remainingQuantity ?? "-",
    },
];

export const TradeImport = () => (
    <CsvImportPage
        entity="trade"
        entityPlural="trades"
        description="Upload chronologically recorded purchases and sales. The preview validates every row and shows the FIFO lots each sale will consume before anything is written."
        template={TRADE_IMPORT_TEMPLATE}
        previewPath="/trade/import/preview"
        importPath="/trade/import"
        requestFields={["rowNumber", "date", "type", "ticker", "quantity", "price", "fees", "portfolio"]}
        columns={columns}
        accentColor="#ed9b40"
        loadingMessage="Building FIFO preview..."
    />
);
