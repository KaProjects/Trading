package org.kaleta.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.kaleta.entity.Trade;

import java.util.List;

@ApplicationScoped
public class TradeDaoImpl implements TradeDao
{
    @PersistenceContext
    EntityManager entityManager;

    private String selectQuery = "SELECT t FROM Trade t";

    @Override
    public List<Trade> list()
    {
        return entityManager.createQuery(selectQuery, Trade.class).getResultList();
    }

    @Override
    public List<Trade> listActive()
    {
        return entityManager.createQuery(selectQuery + " WHERE t.sellDate IS NULL", Trade.class).getResultList();
    }
}
