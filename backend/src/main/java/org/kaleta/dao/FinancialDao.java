package org.kaleta.dao;

import org.kaleta.entity.Financial;

import java.util.List;

public interface FinancialDao
{
    /**
     * @return lists of all financials
     */
    List<Financial> list();

    /**
     * @return lists of financials for specified company
     */
    List<Financial> list(String companyId);

    /**
     * creates new financial
     */
    void create(Financial financial);
}
