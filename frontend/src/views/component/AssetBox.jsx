import {formatDecimals} from "../../service/FormattingService";
import {Box} from "@mui/material";
import React, {useState} from "react";
import {Editable} from "./Editable";
import {validateNumber} from "../../service/ValidationService";


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

export const AssetBox = ({asset, currency, update, style, immutable = false}) => {
    const [assetEdited, setAssetEdited] = useState(false);
    let profitColor = 'text.primary'
    let profitPercent = null

    if (!assetEdited && !isNaN(Number(asset.profitPercent))) {

        profitPercent = formatDecimals(asset.profitPercent, 0, 2)

        if (asset.profitPercent > 0) {
            profitPercent = "+" + profitPercent
            profitColor = 'success.dark'
        }
        if (asset.profitPercent < 0){
            profitColor = 'error.dark'
        }
    }

    async function updateAssetValue(value) {
        const [quantity, purchasePrice] = value.split("@").map(part => part.trim());

        const error = await update(quantity, purchasePrice);
        if (!error) {
            setAssetEdited(true);
        }
        return error;
    }

    return (
        <Editable
            value={`${asset.quantity}@${asset.purchasePrice}`}
            label={"Asset"}
            validate={validateAssetValue}
            update={updateAssetValue}
        >
            {({showValue, setEditing}) =>
                <Box sx={style}>
                    {assetEdited &&
                        <Box sx={{color: "grey.300", fontWeight: 'bold', mx: 0.5, fontSize: 12, textAlign: "center"}}>
                            edited
                        </Box>
                    }
                    {profitPercent &&
                        <Box sx={{color: profitColor, fontWeight: 'bold', mx: 0.5, fontSize: 12, textAlign: "center"}}>
                            {profitPercent}%
                        </Box>
                    }
                    <Box sx={{color: "text.secondary", fontSize: 16, fontFamily: "Roboto", cursor: "pointer"}}
                         onClick={() => {if (!immutable) {setEditing(true);}}}
                    >
                        {showValue}{currency}
                    </Box>
                </Box>
            }
        </Editable>
    )
}
