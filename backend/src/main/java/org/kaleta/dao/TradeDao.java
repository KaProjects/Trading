package org.kaleta.dao;

import org.kaleta.entity.Trade;

import java.util.List;

public interface TradeDao
{
    /**
     * @return lists of all trades
     */
    List<Trade> list();

    /**
     * @return lists of all active trades
     */
    List<Trade> listActive();
}
