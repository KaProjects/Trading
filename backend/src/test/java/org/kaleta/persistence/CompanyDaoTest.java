package org.kaleta.persistence;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.entity.Company;

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
}
