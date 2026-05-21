package org.kaleta.persistence.api;

import org.kaleta.persistence.entity.Trade;

import java.util.List;

public interface TradeDao extends EntityCompanyDao<Trade>
{
    /**
     * @return lists of trades that match provided filters (null filter = all values)
     */
    List<Trade> list(Boolean active, String companyId, String currency, String purchaseYear, String sellYear, String sector);

    /**
     * saves all the instance of the specified trades
     */
    void saveAll(List<Trade> trades);
}
