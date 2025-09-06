package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.Test;
import org.kaleta.dao.CompanyDao;
import org.kaleta.dao.PeriodDao;
import org.kaleta.dto.PeriodCreateDto;
import org.kaleta.dto.PeriodDto;
import org.kaleta.dto.PeriodsDto;
import org.kaleta.entity.Company;
import org.kaleta.entity.Period;
import org.kaleta.framework.Generator;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class PeriodServiceTest
{
    @InjectMock
    CompanyDao companyDao;

    @InjectMock
    PeriodDao periodDao;

    @Inject
    PeriodService service;

    @Test
    void getByCompanyId() {
        Company company = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company, true, "25Q1", "2504");
        Period period2 = Generator.generatePeriod(company, false, "25Q2", "2507");
        Period period3 = Generator.generatePeriod(company, true, "24Q4", "2501");

        when(companyDao.get(company.getId())).thenReturn(company);
        when(periodDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(period1, period2, period3)));

        PeriodsDto dto = service.getBy(company.getId());

        assertThat(dto.getCompany().getId(), is(company.getId()));
        assertThat(dto.getCompany().getTicker(), is(company.getTicker()));

        assertThat(dto.getPeriods().size(), is(3));
        assertThat(dto.getPeriods().get(0).getName(), is("25Q2"));
        assertThat(dto.getPeriods().get(0).getRevenue(), is(""));
        assertThat(dto.getPeriods().get(1).getName(), is("25Q1"));
        assertThat(dto.getPeriods().get(1).getRevenue(), is(not("")));
        assertThat(dto.getPeriods().get(2).getName(), is("24Q4"));
        assertThat(dto.getPeriods().get(2).getRevenue(), is(not("")));

        assertThat(dto.getFinancials().size(), is(2));
        assertThat(dto.getFinancials().get(0).getPeriod(), is("25Q1"));
        assertThat(dto.getFinancials().get(1).getPeriod(), is("24Q4"));

        assertThat(dto.getTtm(), notNullValue());
    }

    @Test
    void update()
    {
        Company company = Generator.generateCompany();
        Period period = Generator.generatePeriod(company, true);

        when(periodDao.get(period.getId())).thenReturn(period);

        PeriodDto periodDto = new PeriodDto();
        periodDto.setId(period.getId());
        periodDto.setName("YOLO");
        periodDto.setEndingMonth("3012");
        periodDto.setReportDate("3030-01-01");
        periodDto.setShares("10000");
        periodDto.setPriceLatest("204");
        periodDto.setPriceLow("20");
        periodDto.setPriceHigh("300");
        periodDto.setResearch("new findings");
        periodDto.setRevenue("100250");
        periodDto.setCostGoodsSold("10250");
        periodDto.setOperatingExpenses("1250");
        periodDto.setNetIncome("125");
        periodDto.setDividend("50");

        service.update(periodDto);

        ArgumentCaptor<Period> captor = ArgumentCaptor.forClass(Period.class);
        verify(periodDao).save(captor.capture());

        assertThat(captor.getValue().getCompany().getId(), is(company.getId()));
        assertThat(captor.getValue().getName(), is("YOLO"));
        assertThat(captor.getValue().getEndingMonth(), is("3012"));
        assertThat(captor.getValue().getReportDate(), is(Date.valueOf("3030-1-1")));
        assertThat(captor.getValue().getShares(), is(new BigDecimal("10000")));
        assertThat(captor.getValue().getPriceHigh(), is(new BigDecimal("300")));
        assertThat(captor.getValue().getPriceLatest(), is(new BigDecimal("204")));
        assertThat(captor.getValue().getPriceLow(), is(new BigDecimal("20")));
        assertThat(captor.getValue().getResearch(), is("new findings"));
        assertThat(captor.getValue().getRevenue(), is(new BigDecimal("100250")));
        assertThat(captor.getValue().getCostGoodsSold(), is(new BigDecimal("10250")));
        assertThat(captor.getValue().getOperatingExpenses(), is(new BigDecimal("1250")));
        assertThat(captor.getValue().getNetIncome(), is(new BigDecimal("125")));
        assertThat(captor.getValue().getDividend(), is(new BigDecimal("50")));
    }

    @Test
    void updateNoChange()
    {
        Company company = Generator.generateCompany();
        Period period = Generator.generatePeriod(company, true);

        when(periodDao.get(period.getId())).thenReturn(period);

        PeriodDto periodDto = new PeriodDto();
        periodDto.setId(period.getId());

        service.update(periodDto);

        ArgumentCaptor<Period> captor = ArgumentCaptor.forClass(Period.class);
        verify(periodDao).save(captor.capture());

        assertThat(captor.getValue().getCompany().getId(), is(company.getId()));
        assertThat(captor.getValue().getName(), is(period.getName()));
        assertThat(captor.getValue().getEndingMonth(), is(period.getEndingMonth()));
        assertThat(captor.getValue().getReportDate(), is(period.getReportDate()));
        assertThat(captor.getValue().getShares(), is(period.getShares()));
        assertThat(captor.getValue().getPriceHigh(), is(period.getPriceHigh()));
        assertThat(captor.getValue().getPriceLatest(), is(period.getPriceLatest()));
        assertThat(captor.getValue().getPriceLow(), is(period.getPriceLow()));
        assertThat(captor.getValue().getResearch(), is(period.getResearch()));
        assertThat(captor.getValue().getRevenue(), is(period.getRevenue()));
        assertThat(captor.getValue().getCostGoodsSold(), is(period.getCostGoodsSold()));
        assertThat(captor.getValue().getOperatingExpenses(), is(period.getOperatingExpenses()));
        assertThat(captor.getValue().getNetIncome(), is(period.getNetIncome()));
        assertThat(captor.getValue().getDividend(), is(period.getDividend()));
    }

    @Test
    void updateNonexistentPeriod()
    {
        PeriodDto periodDto = new PeriodDto();
        periodDto.setId(UUID.randomUUID().toString());

        when(periodDao.get(periodDto.getId())).thenThrow(NoResultException.class);

        try {
            service.update(periodDto);
        } catch (RuntimeException e) {
            assertThat(e.getClass(), is(ServiceFailureException.class));
            assertThat(e.getMessage(), is("period with id '" + periodDto.getId() + "' not found"));
        }
        verify(periodDao, times(1)).get(periodDto.getId());
    }

    @Test
    void create ()
    {
        Company company = Generator.generateCompany();

        when(companyDao.get(company.getId())).thenReturn(company);

        PeriodCreateDto periodCreateDto = new PeriodCreateDto();
        periodCreateDto.setCompanyId(company.getId());
        periodCreateDto.setName("25FY");
        periodCreateDto.setEndingMonth("2602");
        periodCreateDto.setReportDate("2026-03-30");

        service.create(periodCreateDto);

        ArgumentCaptor<Period> captor = ArgumentCaptor.forClass(Period.class);
        verify(periodDao).create(captor.capture());

        assertThat(captor.getValue().getCompany().getId(), is(company.getId()));
        assertThat(captor.getValue().getName(), is("25FY"));
        assertThat(captor.getValue().getEndingMonth(), is("2602"));
        assertThat(captor.getValue().getReportDate(), is(Date.valueOf("2026-03-30")));
    }

    @Test
    void createWithNullReportDate ()
    {
        Company company = Generator.generateCompany();

        when(companyDao.get(company.getId())).thenReturn(company);

        PeriodCreateDto periodCreateDto = new PeriodCreateDto();
        periodCreateDto.setCompanyId(company.getId());
        periodCreateDto.setName("25FY");
        periodCreateDto.setEndingMonth("2602");

        service.create(periodCreateDto);

        ArgumentCaptor<Period> captor = ArgumentCaptor.forClass(Period.class);
        verify(periodDao).create(captor.capture());

        assertThat(captor.getValue().getCompany().getId(), is(company.getId()));
        assertThat(captor.getValue().getName(), is("25FY"));
        assertThat(captor.getValue().getEndingMonth(), is("2602"));
        assertThat(captor.getValue().getReportDate(), is(nullValue()));
    }

    @Test
    void getByCompanyIdFinancials ()
    {
        Company company = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company, true, "25Q1", "2504");
        period1.setRevenue(new BigDecimal("3000"));
        period1.setCostGoodsSold(new BigDecimal("2000"));
        period1.setOperatingExpenses(new BigDecimal("500"));
        period1.setNetIncome(new BigDecimal("100"));
        period1.setDividend(new BigDecimal("40"));
        Period period2 = Generator.generatePeriod(company, false, "25Q2", "2507");

        when(companyDao.get(company.getId())).thenReturn(company);
        when(periodDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(period1, period2)));

        PeriodsDto dto = service.getBy(company.getId());

        assertThat(dto.getCompany().getId(), is(company.getId()));
        assertThat(dto.getPeriods().size(), is(2));
        assertThat(dto.getFinancials().size(), is(1));
        assertThat(dto.getTtm(), notNullValue());

        assertThat(dto.getFinancials().get(0).getPeriod(), is("25Q1"));
        assertThat(dto.getFinancials().get(0).getRevenue(), is("3B"));
        assertThat(dto.getFinancials().get(0).getCostGoodsSold(), is("2B"));
        assertThat(dto.getFinancials().get(0).getGrossProfit(), is("1B"));
        assertThat(dto.getFinancials().get(0).getGrossMargin(), is("33"));
        assertThat(dto.getFinancials().get(0).getOperatingExpenses(), is("500M"));
        assertThat(dto.getFinancials().get(0).getOperatingIncome(), is("500M"));
        assertThat(dto.getFinancials().get(0).getOperatingMargin(), is("17"));
        assertThat(dto.getFinancials().get(0).getNetIncome(), is("100M"));
        assertThat(dto.getFinancials().get(0).getNetMargin(), is("3"));
        assertThat(dto.getFinancials().get(0).getDividend(), is("40M"));
    }

    @Test
    void getByCompanyIdTtmQuarters ()
    {
        Company company = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company, true, "25Q1", "2504");
        period1.setRevenue(new BigDecimal("3000"));
        period1.setCostGoodsSold(new BigDecimal("2000"));
        period1.setOperatingExpenses(new BigDecimal("500"));
        period1.setNetIncome(new BigDecimal("100"));
        period1.setDividend(new BigDecimal("10"));
        Period period2 = Generator.generatePeriod(company, false, "25Q2", "2507");
        Period period3 = Generator.generatePeriod(company, true, "24Q4", "2501");
        period3.setRevenue(new BigDecimal("2500"));
        period3.setCostGoodsSold(new BigDecimal("1000"));
        period3.setOperatingExpenses(new BigDecimal("400"));
        period3.setNetIncome(new BigDecimal("50"));
        period3.setDividend(new BigDecimal("5"));

        when(companyDao.get(company.getId())).thenReturn(company);
        when(periodDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(period1, period2, period3)));

        PeriodsDto dto = service.getBy(company.getId());

        assertThat(dto.getCompany().getId(), is(company.getId()));
        assertThat(dto.getPeriods().size(), is(3));
        assertThat(dto.getFinancials().size(), is(2));

        assertThat(dto.getTtm().getPeriod(), is(nullValue()));
        assertThat(dto.getTtm().getRevenue(), is("11B"));
        assertThat(dto.getTtm().getCostGoodsSold(), is("6B"));
        assertThat(dto.getTtm().getGrossProfit(), is("5B"));
        assertThat(dto.getTtm().getGrossMargin(), is("45"));
        assertThat(dto.getTtm().getOperatingExpenses(), is("1.8B"));
        assertThat(dto.getTtm().getOperatingIncome(), is("3.2B"));
        assertThat(dto.getTtm().getOperatingMargin(), is("29"));
        assertThat(dto.getTtm().getNetIncome(), is("300M"));
        assertThat(dto.getTtm().getNetMargin(), is("3"));
        assertThat(dto.getTtm().getDividend(), is("30M"));
    }

    @Test
    void getByCompanyIdTtmHalfYears ()
    {
        Company company = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company, true, "25H1", "2504");
        period1.setRevenue(new BigDecimal("3000"));
        period1.setCostGoodsSold(new BigDecimal("2000"));
        period1.setOperatingExpenses(new BigDecimal("500"));
        period1.setNetIncome(new BigDecimal("100"));
        period1.setDividend(new BigDecimal("10.55"));
        Period period2 = Generator.generatePeriod(company, false, "25H2", "2510");
        Period period3 = Generator.generatePeriod(company, true, "24H2", "2410");
        period3.setRevenue(new BigDecimal("2500"));
        period3.setCostGoodsSold(new BigDecimal("1000"));
        period3.setOperatingExpenses(new BigDecimal("400"));
        period3.setNetIncome(new BigDecimal("50"));
        period3.setDividend(new BigDecimal("9.45"));

        when(companyDao.get(company.getId())).thenReturn(company);
        when(periodDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(period1, period2, period3)));

        PeriodsDto dto = service.getBy(company.getId());

        assertThat(dto.getCompany().getId(), is(company.getId()));
        assertThat(dto.getPeriods().size(), is(3));
        assertThat(dto.getFinancials().size(), is(2));

        assertThat(dto.getTtm().getPeriod(), is(nullValue()));
        assertThat(dto.getTtm().getRevenue(), is("5.5B"));
        assertThat(dto.getTtm().getCostGoodsSold(), is("3B"));
        assertThat(dto.getTtm().getGrossProfit(), is("2.5B"));
        assertThat(dto.getTtm().getGrossMargin(), is("45"));
        assertThat(dto.getTtm().getOperatingExpenses(), is("900M"));
        assertThat(dto.getTtm().getOperatingIncome(), is("1.6B"));
        assertThat(dto.getTtm().getOperatingMargin(), is("29"));
        assertThat(dto.getTtm().getNetIncome(), is("150M"));
        assertThat(dto.getTtm().getNetMargin(), is("3"));
        assertThat(dto.getTtm().getDividend(), is("20M"));
    }

    @Test
    void getByCompanyIdTtmYears ()
    {
        Company company = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company, true, "25FY", "2501");
        period1.setRevenue(new BigDecimal("3000"));
        period1.setCostGoodsSold(new BigDecimal("2000"));
        period1.setOperatingExpenses(new BigDecimal("500"));
        period1.setNetIncome(new BigDecimal("100"));
        period1.setDividend(new BigDecimal("10"));
        Period period2 = Generator.generatePeriod(company, false, "26FY", "2601");
        Period period3 = Generator.generatePeriod(company, true, "24FY", "2401");
        period3.setRevenue(new BigDecimal("2500"));
        period3.setCostGoodsSold(new BigDecimal("1000"));
        period3.setOperatingExpenses(new BigDecimal("400"));
        period3.setNetIncome(new BigDecimal("50"));
        period3.setDividend(new BigDecimal("5"));

        when(companyDao.get(company.getId())).thenReturn(company);
        when(periodDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(period1, period2, period3)));

        PeriodsDto dto = service.getBy(company.getId());

        assertThat(dto.getCompany().getId(), is(company.getId()));
        assertThat(dto.getPeriods().size(), is(3));
        assertThat(dto.getFinancials().size(), is(2));

        assertThat(dto.getTtm().getRevenue(), is("3B"));
        assertThat(dto.getTtm().getCostGoodsSold(), is("2B"));
        assertThat(dto.getTtm().getGrossProfit(), is("1B"));
        assertThat(dto.getTtm().getGrossMargin(), is("33"));
        assertThat(dto.getTtm().getOperatingExpenses(), is("500M"));
        assertThat(dto.getTtm().getOperatingIncome(), is("500M"));
        assertThat(dto.getTtm().getOperatingMargin(), is("17"));
        assertThat(dto.getTtm().getNetIncome(), is("100M"));
        assertThat(dto.getTtm().getNetMargin(), is("3"));
        assertThat(dto.getTtm().getDividend(), is("10M"));
    }

    @Test
    void getByCompanyIdTtmMix ()
    {
        Company company = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company, true, "25Q1", "2504");
        period1.setRevenue(new BigDecimal("3000"));
        period1.setCostGoodsSold(new BigDecimal("2000"));
        period1.setOperatingExpenses(new BigDecimal("500"));
        period1.setNetIncome(new BigDecimal("100"));
        period1.setDividend(new BigDecimal("10"));
        Period period2 = Generator.generatePeriod(company, false, "25Q2", "2507");
        Period period3 = Generator.generatePeriod(company, true, "24H2", "2501");
        period3.setRevenue(new BigDecimal("2500"));
        period3.setCostGoodsSold(new BigDecimal("1000"));
        period3.setOperatingExpenses(new BigDecimal("400"));
        period3.setNetIncome(new BigDecimal("50"));
        period3.setDividend(new BigDecimal("5"));

        when(companyDao.get(company.getId())).thenReturn(company);
        when(periodDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(period1, period2, period3)));

        PeriodsDto dto = service.getBy(company.getId());

        assertThat(dto.getCompany().getId(), is(company.getId()));
        assertThat(dto.getPeriods().size(), is(3));
        assertThat(dto.getFinancials().size(), is(2));

        assertThat(dto.getTtm().getRevenue(), is("7.33B"));
        assertThat(dto.getTtm().getCostGoodsSold(), is("4B"));
        assertThat(dto.getTtm().getGrossProfit(), is("3.33B"));
        assertThat(dto.getTtm().getGrossMargin(), is("45"));
        assertThat(dto.getTtm().getOperatingExpenses(), is("1.2B"));
        assertThat(dto.getTtm().getOperatingIncome(), is("2.13B"));
        assertThat(dto.getTtm().getOperatingMargin(), is("29"));
        assertThat(dto.getTtm().getNetIncome(), is("200M"));
        assertThat(dto.getTtm().getNetMargin(), is("3"));
        assertThat(dto.getTtm().getDividend(), is("20M"));
    }

    @Test
    void getByCompanyIdTtmNoPeriods ()
    {
        Company company = Generator.generateCompany();

        when(companyDao.get(company.getId())).thenReturn(company);
        when(periodDao.list(company.getId())).thenReturn(new ArrayList<>());

        PeriodsDto dto = service.getBy(company.getId());

        assertThat(dto.getCompany().getId(), is(company.getId()));
        assertThat(dto.getPeriods().size(), is(0));
        assertThat(dto.getFinancials().size(), is(0));

        assertThat(dto.getTtm(), is(nullValue()));
    }

    @Test
    void getByCompanyIdTtmInvalidPeriodName ()
    {
        Company company = Generator.generateCompany();
        Period period = Generator.generatePeriod(company, true, "25XY", "2504");

        when(companyDao.get(company.getId())).thenReturn(company);
        when(periodDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(period)));

        try {
            service.getBy(company.getId());
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Invalid period name: '25XY'"));
        }
    }

    @Test
    void getByCompanyIdTtmNegativeValues ()
    {
        Company company = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company, true, "25Q1", "2504");
        period1.setRevenue(new BigDecimal("3000"));
        period1.setCostGoodsSold(new BigDecimal("2000"));
        period1.setOperatingExpenses(new BigDecimal("500"));
        period1.setNetIncome(new BigDecimal("-100"));

        when(companyDao.get(company.getId())).thenReturn(company);
        when(periodDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(period1)));

        PeriodsDto dto = service.getBy(company.getId());

        assertThat(dto.getCompany().getId(), is(company.getId()));
        assertThat(dto.getPeriods().size(), is(1));
        assertThat(dto.getFinancials().size(), is(1));

        assertThat(dto.getTtm().getPeriod(), is(nullValue()));
        assertThat(dto.getTtm().getRevenue(), is("12B"));
        assertThat(dto.getTtm().getCostGoodsSold(), is("8B"));
        assertThat(dto.getTtm().getGrossProfit(), is("4B"));
        assertThat(dto.getTtm().getGrossMargin(), is("33"));
        assertThat(dto.getTtm().getOperatingExpenses(), is("2B"));
        assertThat(dto.getTtm().getOperatingIncome(), is("2B"));
        assertThat(dto.getTtm().getOperatingMargin(), is("17"));
        assertThat(dto.getTtm().getNetIncome(), is("-400M"));
        assertThat(dto.getTtm().getNetMargin(), is("-3"));
    }
}
