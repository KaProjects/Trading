package org.kaleta.dao;

import org.kaleta.entity.Trade;

import java.util.List;

public interface TradeDao
{
    /**
     * @return lists of trades that match provided filters (null filter = all values)
     */
    List<Trade> list(boolean active, String companyId, String currency, String year);
}
