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

    private final String selectQuery = "SELECT r FROM Record r WHERE r.company.id=:companyId";


    @Override
    public List<Record> list(String companyId)
    {
        return entityManager.createQuery(selectQuery, Record.class)
                .setParameter("companyId", companyId)
                .getResultList();
    }
}
