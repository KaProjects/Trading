package org.kaleta.dao;

import org.kaleta.entity.Trade;
import org.kaleta.model.CompanyInfo;

import java.util.List;

public interface TradeDao
{
    /**
     * @return lists of trades that match provided filters (null filter = all values)
     */
    List<Trade> list(Boolean active, String companyId, String currency, String year);

    /**
     * @return latest purchase dates for every company (that have at least open trade)
     */
    List<CompanyInfo> latestPurchase();

    /**
     * creates new trade
     */
    void create(Trade trade);

    /**
     * @return trade according to specified ID
     */
    Trade get(String id);
}
