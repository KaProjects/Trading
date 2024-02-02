package org.kaleta.dao;

import org.kaleta.entity.Dividend;

import java.util.List;

public interface DividendDao
{
    /**
     * @return lists of dividends that match provided filters (null filter = all values)
     */
    List<Dividend> list(String companyId, String currency, String year);

    /**
     * creates new dividend
     */
    void create(Dividend dividend);

    /**
     * @return dividend according to specified ID
     */
    Dividend get(String id);
}
