package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.kaleta.dao.CompanyDao;
import org.kaleta.dao.PeriodDao;
import org.kaleta.dao.RecordDao;
import org.kaleta.dto.ResearchUiDto;
import org.kaleta.entity.Company;
import org.kaleta.entity.Period;
import org.kaleta.framework.Generator;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

@QuarkusTest
public class ResearchServiceTest
{
    @InjectMock
    CompanyDao companyDao;
    @InjectMock
    PeriodDao periodDao;
    @InjectMock
    RecordDao recordDao;

    @Inject
    ResearchService service;

    @Test
    void getDtoByCompanyId() {
        Company company = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company, "25Q1", "2504",
                "1000", "500", "300", "100", "10");
        Period period2 = Generator.generatePeriod(company, false, "25Q2", "2507");
        Period period3 = Generator.generatePeriod(company, "24Q4", "2501",
                "1400", "800", "300", "50", "10");

        when(companyDao.get(company.getId())).thenReturn(company);
        when(periodDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(period1, period2, period3)));

        ResearchUiDto dto = service.getDto(company.getId());

        assertThat(dto.getCompany().getId(), is(company.getId()));
        assertThat(dto.getCompany().getTicker(), is(company.getTicker()));
        assertThat(dto.getCompany().getShares(), is(company.getShares()));
        assertThat(dto.getCompany().getWatching(), is(company.isWatching()));
        assertThat(dto.getCompany().getCurrency(), is(company.getCurrency()));

        assertThat(dto.getPeriods().size(), is(3));
        assertThat(dto.getPeriods().get(0).getName(), is("25Q2"));
        assertThat(dto.getPeriods().get(0).getEndingMonth(), is("07/2025"));
        assertThat(dto.getPeriods().get(0).getRevenue(), is(""));
        assertThat(dto.getPeriods().get(0).getCostGoodsSold(), is(""));
        assertThat(dto.getPeriods().get(0).getOperatingExpenses(), is(""));
        assertThat(dto.getPeriods().get(0).getNetIncome(), is(""));
        assertThat(dto.getPeriods().get(0).getDividend(), is(""));

        assertThat(dto.getPeriods().get(1).getName(), is("25Q1"));
        assertThat(dto.getPeriods().get(1).getEndingMonth(), is("04/2025"));
        assertThat(dto.getPeriods().get(1).getRevenue(), is("1B"));
        assertThat(dto.getPeriods().get(1).getCostGoodsSold(), is("500M"));
        assertThat(dto.getPeriods().get(1).getOperatingExpenses(), is("300M"));
        assertThat(dto.getPeriods().get(1).getNetIncome(), is("100M"));
        assertThat(dto.getPeriods().get(1).getDividend(), is("10M"));

        assertThat(dto.getPeriods().get(2).getName(), is("24Q4"));
        assertThat(dto.getPeriods().get(2).getEndingMonth(), is("01/2025"));
        assertThat(dto.getPeriods().get(2).getRevenue(), is("1.4B"));
        assertThat(dto.getPeriods().get(2).getCostGoodsSold(), is("800M"));
        assertThat(dto.getPeriods().get(2).getOperatingExpenses(), is("300M"));
        assertThat(dto.getPeriods().get(2).getNetIncome(), is("50M"));
        assertThat(dto.getPeriods().get(2).getDividend(), is("10M"));

        assertThat(dto.getFinancials().size(), is(2));
        assertThat(dto.getFinancials().get(0).getPeriod(), is("25Q1"));
        assertThat(dto.getFinancials().get(0).getRevenue(), is("1B"));
        assertThat(dto.getFinancials().get(0).getCostGoodsSold(), is("500M"));
        assertThat(dto.getFinancials().get(0).getGrossProfit(), is("500M"));
        assertThat(dto.getFinancials().get(0).getGrossMargin(), is("50"));
        assertThat(dto.getFinancials().get(0).getOperatingExpenses(), is("300M"));
        assertThat(dto.getFinancials().get(0).getOperatingIncome(), is("200M"));
        assertThat(dto.getFinancials().get(0).getOperatingMargin(), is("20"));
        assertThat(dto.getFinancials().get(0).getNetIncome(), is("100M"));
        assertThat(dto.getFinancials().get(0).getNetMargin(), is("10"));
        assertThat(dto.getFinancials().get(0).getDividend(), is("10M"));

        assertThat(dto.getFinancials().get(1).getPeriod(), is("24Q4"));
        assertThat(dto.getFinancials().get(1).getRevenue(), is("1.4B"));
        assertThat(dto.getFinancials().get(1).getCostGoodsSold(), is("800M"));
        assertThat(dto.getFinancials().get(1).getGrossProfit(), is("600M"));
        assertThat(dto.getFinancials().get(1).getGrossMargin(), is("43"));
        assertThat(dto.getFinancials().get(1).getOperatingExpenses(), is("300M"));
        assertThat(dto.getFinancials().get(1).getOperatingIncome(), is("300M"));
        assertThat(dto.getFinancials().get(1).getOperatingMargin(), is("21"));
        assertThat(dto.getFinancials().get(1).getNetIncome(), is("50M"));
        assertThat(dto.getFinancials().get(1).getNetMargin(), is("4"));
        assertThat(dto.getFinancials().get(1).getDividend(), is("10M"));

        assertThat(dto.getTtm().getPeriod(), is(nullValue()));
        assertThat(dto.getTtm().getRevenue(), is("4.8B"));
        assertThat(dto.getTtm().getCostGoodsSold(), is("2.6B"));
        assertThat(dto.getTtm().getGrossProfit(), is("2.2B"));
        assertThat(dto.getTtm().getGrossMargin(), is("46"));
        assertThat(dto.getTtm().getOperatingExpenses(), is("1.2B"));
        assertThat(dto.getTtm().getOperatingIncome(), is("1B"));
        assertThat(dto.getTtm().getOperatingMargin(), is("21"));
        assertThat(dto.getTtm().getNetIncome(), is("300M"));
        assertThat(dto.getTtm().getNetMargin(), is("6"));
        assertThat(dto.getTtm().getDividend(), is("40M"));
    }
}
