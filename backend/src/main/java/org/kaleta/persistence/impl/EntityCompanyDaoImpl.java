package org.kaleta.persistence.impl;

import org.kaleta.persistence.api.EntityCompanyDao;
import org.kaleta.persistence.entity.AbstractEntityCompany;

import java.util.List;

public abstract class EntityCompanyDaoImpl<C extends AbstractEntityCompany> extends EntityDaoImpl<C> implements EntityCompanyDao<C>
{
    @Override
    public List<C> list(String companyId)
    {
        return entityManager.createQuery( selectQuery + "WHERE t.company.id=:companyId", getEntityClass())
                .setParameter("companyId", companyId)
                .getResultList();
    }
}
