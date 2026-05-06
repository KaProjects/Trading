import {formatDecimals} from "../../service/FormattingService";
import {Box} from "@mui/material";
import React from "react";

export const AssetBox = ({asset, currency}) => {
    let profitColor = 'text.primary'
    let profitPercent = null

    if (!isNaN(Number(asset.profitPercent))) {

        profitPercent = formatDecimals(asset.profitPercent, 0, 2)

        if (asset.profitPercent > 0) {
            profitPercent = "+" + profitPercent
            profitColor = 'success.dark'
        }
        if (asset.profitPercent < 0){
            profitColor = 'error.dark'
        }
    }

    return <Box sx={{marginLeft: "10px"}}>
        {profitPercent &&
            <Box sx={{color: profitColor, fontWeight: 'bold', mx: 0.5, fontSize: 12, textAlign: "center"}}>
                {profitPercent}%
            </Box>
        }
        <Box sx={{color: "text.secondary", fontSize: 16, fontFamily: "Roboto",}}>
            {asset.quantity}@{asset.purchasePrice}{currency}
        </Box>
    </Box>
}