package org.kaleta.persistence.api;

import org.kaleta.persistence.entity.AbstractCompanyEntity;

import java.util.List;

public interface SuperDao<C extends AbstractCompanyEntity>
{
    /**
     * @return list of all {@link C}
     */
    List<C> list();

    /**
     * @return list of {@link C} for specified company
     */
    List<C> list(String companyId);

    /**
     * @return {@link C} according to specified ID
     */
    C get(String id);

    /**
     * saves the instance of the specified {@link C}
     */
    void save(C c);

    /**
     * saves all the instances of the specified {@link C}
     */
    void saveAll(List<C> list);

    /**
     * creates new {@link C}
     */
    void create(C c);
}
