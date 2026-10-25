package org.kaleta.persistence.api;

import org.kaleta.persistence.entity.InvestmentCase;

import java.util.List;

public interface CaseDao extends EntityDao<InvestmentCase>
{
    /** Every case, the ones still open first and the newest of each first. */
    List<InvestmentCase> listByState();

    void delete(Long caseId);
}
