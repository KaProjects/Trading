import {Box} from "@mui/material";
import React, {useState} from "react";
import {formatDecimals} from "../../service/FormattingService";
import {validateNumber} from "../../service/ValidationService";
import {EditableValueBox} from "./EditableValueBox";

function validateAssetNumber(label, value, lengthConstraint, decimalConstraint) {
    const numberValidation = validateNumber(value, false, lengthConstraint, decimalConstraint, false);
    if (numberValidation) return `${label}: ${numberValidation}`;
    if (Number(value) <= 0) return `${label}: must be greater than 0`;
    return "";
}

function validateAssetValue(value) {
    const values = value.split("@").map(part => part.trim());
    if (values.length !== 2) return "Use format quantity@purchasePrice";

    return validateAssetNumber("Quantity", values[0], 8, 4)
        || validateAssetNumber("Purchase price", values[1], 10, 4);
}

function profitColor(value) {
    if (Number(value) > 0) return "success.dark";
    if (Number(value) < 0) return "error.dark";
    return "text.primary";
}

function formatProfitPercent(value) {
    if (value === null || value === undefined || isNaN(Number(value))) return "-";
    const formatted = formatDecimals(value, 0, 2);
    return `${Number(value) > 0 ? "+" : ""}${formatted}%`;
}

function formatProfitValue(value, currency) {
    if (value === null || value === undefined || isNaN(Number(value))) return "-";
    const number = Number(value);
    const formatted = formatDecimals(Math.abs(number), 0, 2);
    const sign = number > 0 ? "+" : number < 0 ? "-" : "";
    return `${sign}${formatted}${currency}`;
}

function formatAssetValue(value) {
    const [quantity, purchasePrice] = String(value).split("@");
    const quantityNumber = Number(quantity);
    const purchasePriceNumber = Number(purchasePrice);
    if (!Number.isFinite(quantityNumber) || !Number.isFinite(purchasePriceNumber)) return value;

    return `${formatDecimals(quantityNumber, 0, 4)}@${formatDecimals(purchasePriceNumber, 0, 4)}`;
}

export const RecordAssetAggregate = ({asset, currency, update}) => {
    const [assetEdited, setAssetEdited] = useState(false);

    async function updateAssetValue(value) {
        const [quantity, purchasePrice] = value.split("@").map(part => part.trim());
        const error = await update(quantity, purchasePrice);
        if (!error) setAssetEdited(true);
        return error;
    }

    return (
        <Box
            data-testid="record-asset-aggregate"
            sx={{
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                margin: "3px 0 2px 0",
                width: "fit-content",
                maxWidth: "100%",
                lineHeight: 1.25,
            }}
        >
            <EditableValueBox
                value={`${asset.quantity}@${asset.purchasePrice}`}
                suffix={currency}
                label="Asset aggregate"
                valueStyle={{fontSize: 17, fontWeight: 500}}
                formatValue={formatAssetValue}
                validate={validateAssetValue}
                update={updateAssetValue}
                secondary={assetEdited
                    ? <Box sx={{color: "text.secondary", fontWeight: 500, fontSize: 14}}>edited</Box>
                    : <Box
                        data-testid="record-asset-profit"
                        sx={{color: profitColor(asset.profitValue), opacity: 0.78, fontWeight: 500, fontSize: 14, whiteSpace: "nowrap", mt: "-3px"}}
                    >
                            {formatProfitValue(asset.profitValue, currency)}
                            {" "}
                            <Box component="span" data-testid="record-asset-profit-percent" sx={{fontSize: 12}}>
                                ({formatProfitPercent(asset.profitPercent)})
                            </Box>
                        </Box>
                }
            />
        </Box>
    );
}
