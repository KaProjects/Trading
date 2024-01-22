package org.kaleta.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.kaleta.entity.Record;

import java.util.List;

@ApplicationScoped
public class RecordDaoImpl implements RecordDao
{
    @PersistenceContext
    EntityManager entityManager;

    private final String selectQuery = "SELECT r FROM Record r";

    @Override
    public List<Record> list(String companyId)
    {
        return entityManager.createQuery(selectQuery + " WHERE r.company.id=:companyId", Record.class)
                .setParameter("companyId", companyId)
                .getResultList();
    }

    @Override
    public Record get(String recordId)
    {
        return entityManager.createQuery(selectQuery + " WHERE r.id=:recordId", Record.class)
                .setParameter("recordId", recordId)
                .getSingleResult();
    }

    @Override
    @Transactional
    public void store(Record record)
    {
        entityManager.merge(record);
    }

    @Override
    @Transactional
    public void create(Record record)
    {
        entityManager.persist(record);
    }
}
