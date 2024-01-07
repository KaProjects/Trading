package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.dao.TradeDao;
import org.kaleta.entity.Trade;

import java.util.List;

@ApplicationScoped
public class TradeService
{
    @Inject
    TradeDao tradeDao;

    public List<Trade> getAllTrades()
    {
        return tradeDao.list();
    }

    public List<Trade> getActiveTrades()
    {
        return tradeDao.listActive();
    }
}
