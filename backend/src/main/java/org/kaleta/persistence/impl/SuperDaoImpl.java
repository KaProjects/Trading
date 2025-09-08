package org.kaleta.persistence.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.kaleta.persistence.api.SuperDao;
import org.kaleta.persistence.entity.AbstractCompanyEntity;

import java.util.List;

public abstract class SuperDaoImpl<C extends AbstractCompanyEntity> implements SuperDao<C>
{
    @PersistenceContext
    EntityManager entityManager;

    protected final String selectQuery = "SELECT t FROM " + getEntityClass().getSimpleName() + " t ";

    protected abstract Class<C> getEntityClass();

    @Override
    public List<C> list()
    {
        return entityManager.createQuery(selectQuery, getEntityClass()).getResultList();
    }

    @Override
    public List<C> list(String companyId)
    {
        return entityManager.createQuery( selectQuery + "WHERE t.company.id=:companyId", getEntityClass())
                .setParameter("companyId", companyId)
                .getResultList();
    }

    @Override
    public C get(String id)
    {
        return entityManager.createQuery(selectQuery + "WHERE t.id=:id", getEntityClass())
                .setParameter("id", id)
                .getSingleResult();
    }

    @Transactional
    @Override
    public void save(C c)
    {
        entityManager.merge(c);
    }

    @Transactional
    @Override
    public void saveAll(List<C> list)
    {
        for (C c : list){
            entityManager.merge(c);
        }
    }

    @Transactional
    @Override
    public void create(C c)
    {
        entityManager.persist(c);
    }
}
