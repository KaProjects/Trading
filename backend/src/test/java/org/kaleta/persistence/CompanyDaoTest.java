package org.kaleta.persistence;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.CompanyWithStats;

import java.time.YearMonth;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

@QuarkusTest
class CompanyDaoTest
{
    @Inject
    EntityManager entityManager;

    @Inject
    CompanyDao companyDao;

    @Test
    @TestTransaction
    void mapsTagValuesToCompanyStrings()
    {
        entityManager.createNativeQuery("INSERT INTO Tag (value, companyId) VALUES ('growth', 1927), ('ai', 1927)")
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        Company company = companyDao.get(1927L);

        assertThat(company.getTags(), containsInAnyOrder("growth", "ai"));
    }

    @Test
    @TestTransaction
    void listsCompanyStatsWithTagsAndLatestPeriodRegardlessOfReportingState()
    {
        entityManager.createNativeQuery("INSERT INTO Tag (value, companyId) VALUES ('growth', 1927), ('ai', 1927)")
                .executeUpdate();
        entityManager.createNativeQuery("INSERT INTO Period (id, name, ending_month, report_date, companyId) "
                        + "VALUES (4000000, '25Q4', '2601', '2026-02-15', 1927)")
                .executeUpdate();
        entityManager.flush();

        CompanyWithStats company = companyDao.listWithStats().stream()
                .filter(value -> value.getId().equals(1927L))
                .findFirst()
                .orElseThrow();

        assertThat(company.getTags(), containsInAnyOrder("growth", "ai"));
        assertThat(company.getLatestPeriodEndingMonth(), org.hamcrest.Matchers.is(YearMonth.of(2026, 1)));
    }
}
