package org.kaleta.persistence.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.kaleta.persistence.api.EntityDao;
import org.kaleta.persistence.entity.AbstractEntity;

import java.util.List;

public abstract class EntityDaoImpl<C extends AbstractEntity> implements EntityDao<C>
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
    public C get(String id)
    {
        return entityManager.createQuery(selectQuery + "WHERE t.id=:id", getEntityClass())
                .setParameter("id", id)
                .getSingleResult();
    }

    @Transactional
    @Override
    public void create(C c)
    {
        entityManager.persist(c);
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
}
