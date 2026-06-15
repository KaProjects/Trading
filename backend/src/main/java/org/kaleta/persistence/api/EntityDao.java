package org.kaleta.persistence.api;

import org.kaleta.persistence.entity.AbstractEntity;

import java.util.List;

public interface EntityDao<C extends AbstractEntity>
{
    /**
     * @return list of all {@link C}
     */
    List<C> list();

    /**
     * @return {@link C} according to specified ID
     */
    C get(String id);

    /**
     * creates new {@link C}
     */
    void create(C c);

    /**
     * saves the instance of the specified {@link C}
     */
    void save(C c);

    /**
     * saves all the instances of the specified {@link C}
     */
    void saveAll(List<C> list);
}
