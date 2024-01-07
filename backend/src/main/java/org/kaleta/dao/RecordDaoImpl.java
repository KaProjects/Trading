package org.kaleta.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.kaleta.entity.Record;

import java.util.List;

@ApplicationScoped
public class RecordDaoImpl implements RecordDao
{
    @PersistenceContext
    EntityManager entityManager;

    private String selectQuery = "SELECT r FROM Record r WHERE r.ticker=:ticker";


    @Override
    public List<Record> list(String ticker)
    {
        return entityManager.createQuery(selectQuery, Record.class)
                .setParameter("ticker", ticker)
                .getResultList();
    }
}
