package org.kaleta.model;

import lombok.Data;
import org.kaleta.Utils;
import org.kaleta.persistence.entity.Trade;

@Data
public class FirebaseAsset
{
    private String ticker;
    private String price;
    private String quantity;

    public static FirebaseAsset from(Trades.Trade trade)
    {
        FirebaseAsset asset = new FirebaseAsset();
        asset.setTicker(trade.getCompany().getTicker());
        asset.setQuantity(trade.getPurchaseQuantity().stripTrailingZeros().toPlainString());
        asset.setPrice(trade.getPurchasePrice().stripTrailingZeros().toPlainString());
        return asset;
    }
}
