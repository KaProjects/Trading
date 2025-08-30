package org.kaleta.dao;

import org.kaleta.entity.Period;

import java.util.List;

public interface PeriodDao
{
    /**
     * @return lists of periods for specified company
     */
    List<Period> list(String companyId);

    /**
     * @return period according to specified ID
     */
    Period get(String periodId);

    /**
     * saves the instance of the specified period
     */
    void save(Period period);

    /**
     * creates new period
     */
    void create(Period period);
}
