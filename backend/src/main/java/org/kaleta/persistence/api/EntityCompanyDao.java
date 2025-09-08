package org.kaleta.persistence.api;

import org.kaleta.persistence.entity.AbstractEntityCompany;

import java.util.List;

public interface EntityCompanyDao<C extends AbstractEntityCompany> extends EntityDao<C>
{
    /**
     * @return list of {@link C} for specified company
     */
    List<C> list(String companyId);
}
