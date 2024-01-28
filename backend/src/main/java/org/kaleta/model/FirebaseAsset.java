package org.kaleta.model;

import lombok.Data;
import org.kaleta.Utils;
import org.kaleta.entity.Trade;

@Data
public class FirebaseAsset
{
    private String ticker;
    private String price;
    private String quantity;

    public static FirebaseAsset from(Trade trade)
    {
        FirebaseAsset asset = new FirebaseAsset();
        asset.setTicker(trade.getTicker());
        asset.setQuantity(Utils.format(trade.getQuantity()));
        asset.setPrice(Utils.format(trade.getPurchasePrice()));
        return asset;
    }
}
