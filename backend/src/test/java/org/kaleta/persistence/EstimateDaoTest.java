package org.kaleta.persistence;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.kaleta.persistence.api.EstimateDao;
import org.kaleta.persistence.entity.Estimate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.kaleta.framework.Assert.assertBigDecimals;

@QuarkusTest
class EstimateDaoTest
{
    @Inject
    EstimateDao estimateDao;

    @Test
    void listByPeriod()
    {
        List<Estimate> estimates = estimateDao.list(1L);

        assertThat(estimates.size(), is(1));
        Estimate estimate = estimates.getFirst();
        assertThat(estimate.getId(), is(1L));
        assertThat(estimate.getPeriod().getId(), is(1L));
        assertThat(estimate.getDatetime(), is(LocalDateTime.parse("2026-08-01T12:30:00")));
        assertBigDecimals(estimate.getTtm(), new BigDecimal("10.25"));
        assertBigDecimals(estimate.getCurrent(), new BigDecimal("11.50"));
        assertBigDecimals(estimate.getNext1(), new BigDecimal("12.75"));
        assertBigDecimals(estimate.getNext2(), new BigDecimal("14.00"));
        assertThat(estimate.getNext3(), is(nullValue()));
    }
}
