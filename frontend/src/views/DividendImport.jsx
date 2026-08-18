import React from "react";
import {CsvImportPage} from "./component/CsvImportPage";

export const DIVIDEND_IMPORT_TEMPLATE = `date,ticker,dividend,tax
2026-01-15,NVDA,25.50,3.83
2026-02-20,AMD,18.75,2.81`;

const columns = [
    {key: "rowNumber", label: "Row"},
    {key: "date", label: "Date", cellSx: {whiteSpace: "nowrap"}},
    {key: "ticker", label: "Ticker", cellSx: {fontWeight: 700}},
    {key: "dividend", label: "Dividend", align: "right"},
    {key: "tax", label: "Tax", align: "right"},
    {key: "net", label: "Net", align: "right"},
];

export const DividendImport = () => (
    <CsvImportPage
        entity="dividend"
        entityPlural="dividends"
        description="Upload historical dividend payments. The preview validates every row, resolves each ticker, and calculates the net amount before anything is written."
        template={DIVIDEND_IMPORT_TEMPLATE}
        previewPath="/dividend/import/preview"
        importPath="/dividend/import"
        requestFields={["rowNumber", "date", "ticker", "dividend", "tax"]}
        columns={columns}
        accentColor="#2e7d32"
    />
);
