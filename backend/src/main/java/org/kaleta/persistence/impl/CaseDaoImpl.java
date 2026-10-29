package org.kaleta.persistence.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.kaleta.persistence.api.CaseDao;
import org.kaleta.persistence.entity.InvestmentCase;

import java.util.List;

@ApplicationScoped
public class CaseDaoImpl extends EntityDaoImpl<InvestmentCase> implements CaseDao
{
    @Override
    protected Class<InvestmentCase> getEntityClass()
    {
        return InvestmentCase.class;
    }

    @Override
    public List<InvestmentCase> listByState()
    {
        return entityManager.createQuery(
                selectQuery + "ORDER BY CASE WHEN t.closedAt IS NULL THEN 0 ELSE 1 END, "
                        + "t.createdAt DESC, t.id DESC",
                InvestmentCase.class
        ).getResultList();
    }

    /**
     * Everything the case holds goes with it: the pictures and tables of each day, then the days
     * themselves, and only then the case. The companies it covers are a collection of the case,
     * so those rows go with the entity.
     */
    @Transactional
    @Override
    public void delete(Long caseId)
    {
        InvestmentCase managed = entityManager.find(InvestmentCase.class, caseId);
        if (managed == null) return;

        entityManager.createQuery(
                        "DELETE FROM CaseEntryImage i WHERE i.entry.id IN "
                                + "(SELECT e.id FROM CaseEntry e WHERE e.investmentCase.id=:caseId)")
                .setParameter("caseId", caseId)
                .executeUpdate();
        entityManager.createQuery(
                        "DELETE FROM CaseEntryTable t WHERE t.entry.id IN "
                                + "(SELECT e.id FROM CaseEntry e WHERE e.investmentCase.id=:caseId)")
                .setParameter("caseId", caseId)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM CaseEntry e WHERE e.investmentCase.id=:caseId")
                .setParameter("caseId", caseId)
                .executeUpdate();
        entityManager.remove(managed);
    }
}
