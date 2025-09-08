package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.Test;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.api.PeriodDao;
import org.kaleta.dto.FinancialDto;
import org.kaleta.dto.PeriodCreateDto;
import org.kaleta.dto.PeriodDto;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Period;
import org.kaleta.framework.Generator;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
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

        List<Period> periods = service.getBy(company.getId());

        assertThat(periods.get(0).getId(), is(period2.getId()));
        assertThat(periods.get(1).getId(), is(period1.getId()));
        assertThat(periods.get(2).getId(), is(period3.getId()));
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
        assertThat(captor.getValue().getPriceLow(), is(period.getPriceLow()));
        assertThat(captor.getValue().getResearch(), is(period.getResearch()));
        assertThat(captor.getValue().getRevenue(), is(period.getRevenue()));
        assertThat(captor.getValue().getCostGoodsSold(), is(period.getCostGoodsSold()));
        assertThat(captor.getValue().getOperatingExpenses(), is(period.getOperatingExpenses()));
        assertThat(captor.getValue().getNetIncome(), is(period.getNetIncome()));
        assertThat(captor.getValue().getDividend(), is(period.getDividend()));
    }

    @Test
    void updateNonexistent()
    {
        PeriodDto dto = new PeriodDto();
        dto.setId(UUID.randomUUID().toString());

        when(periodDao.get(dto.getId())).thenThrow(NoResultException.class);

        try {
            service.update(dto);
        } catch (RuntimeException e) {
            assertThat(e.getClass(), is(ServiceFailureException.class));
            assertThat(e.getMessage(), is("period with id '" + dto.getId() + "' not found"));
        }
        verify(periodDao, times(1)).get(dto.getId());
    }

    @Test
    void create()
    {
        Company company = Generator.generateCompany();

        when(companyDao.get(company.getId())).thenReturn(company);

        PeriodCreateDto createDto = new PeriodCreateDto();
        createDto.setCompanyId(company.getId());
        createDto.setName("25FY");
        createDto.setEndingMonth("2602");
        createDto.setReportDate("2026-03-30");

        service.create(createDto);

        ArgumentCaptor<Period> captor = ArgumentCaptor.forClass(Period.class);
        verify(periodDao).create(captor.capture());

        assertThat(captor.getValue().getCompany().getId(), is(company.getId()));
        assertThat(captor.getValue().getName(), is("25FY"));
        assertThat(captor.getValue().getEndingMonth(), is("2602"));
        assertThat(captor.getValue().getReportDate(), is(Date.valueOf("2026-03-30")));
    }

    @Test
    void createWithNullReportDate()
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
    void dtoFromPeriod()
    {
        Company company = Generator.generateCompany();
        Period period = new Period();
        period.setCompany(company);
        period.setName("YOLO");
        period.setEndingMonth("3012");
        period.setReportDate(Date.valueOf("2026-03-30"));
        period.setShares(new BigDecimal("10000"));
        period.setPriceLow(new BigDecimal("20"));
        period.setPriceHigh(new BigDecimal("300"));
        period.setResearch("new findings");
        period.setRevenue(new  BigDecimal("100250"));
        period.setCostGoodsSold(new BigDecimal("10250"));
        period.setOperatingExpenses(new  BigDecimal("1250"));
        period.setNetIncome(new BigDecimal("125"));
        period.setDividend(new BigDecimal("50"));

        PeriodDto dto = service.dtoFrom(period);

        assertThat(dto.getId(), is(period.getId()));
        assertThat(dto.getName(), is(period.getName()));
        assertThat(dto.getEndingMonth(), is("12/2030"));
        assertThat(dto.getReportDate(), is("30.03.2026"));
        assertThat(dto.getShares(), is("10B"));
        assertThat(dto.getPriceHigh(), is("300"));
        assertThat(dto.getPriceLow(), is("20"));
        assertThat(dto.getResearch(), is(period.getResearch()));
        assertThat(dto.getRevenue(), is("100.25B"));
        assertThat(dto.getCostGoodsSold(), is("10.25B"));
        assertThat(dto.getOperatingExpenses(), is("1.25B"));
        assertThat(dto.getNetIncome(), is("125M"));
        assertThat(dto.getDividend(), is("50M"));
    }

    @Test
    void computeFinancialFromPeriod()
    {
        Company company = Generator.generateCompany();
        Period period = new Period();
        period.setCompany(company);
        period.setName("YOLO");
        period.setEndingMonth("3012");
        period.setReportDate(Date.valueOf("2026-03-30"));
        period.setShares(new BigDecimal("10000"));
        period.setPriceLow(new BigDecimal("20"));
        period.setPriceHigh(new BigDecimal("300"));
        period.setResearch("new findings");
        period.setRevenue(new  BigDecimal("3000"));
        period.setCostGoodsSold(new BigDecimal("2000"));
        period.setOperatingExpenses(new  BigDecimal("500"));
        period.setNetIncome(new BigDecimal("100"));
        period.setDividend(new BigDecimal("40"));

        FinancialDto dto = service.computeFinancialFrom(period);

        assertThat(dto.getPeriod(), is("YOLO"));
        assertThat(dto.getRevenue(), is("3B"));
        assertThat(dto.getCostGoodsSold(), is("2B"));
        assertThat(dto.getGrossProfit(), is("1B"));
        assertThat(dto.getGrossMargin(), is("33"));
        assertThat(dto.getOperatingExpenses(), is("500M"));
        assertThat(dto.getOperatingIncome(), is("500M"));
        assertThat(dto.getOperatingMargin(), is("17"));
        assertThat(dto.getNetIncome(), is("100M"));
        assertThat(dto.getNetMargin(), is("3"));
        assertThat(dto.getDividend(), is("40M"));
    }

    @Test
    void computeTtmFromQuarters()
    {
        Company company = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company, true, "25Q1", "2504");
        period1.setRevenue(new BigDecimal("3000"));
        period1.setCostGoodsSold(new BigDecimal("2000"));
        period1.setOperatingExpenses(new BigDecimal("500"));
        period1.setNetIncome(new BigDecimal("100"));
        period1.setDividend(new BigDecimal("10"));
        Period period2 = Generator.generatePeriod(company, true, "24Q4", "2501");
        period2.setRevenue(new BigDecimal("2500"));
        period2.setCostGoodsSold(new BigDecimal("1000"));
        period2.setOperatingExpenses(new BigDecimal("400"));
        period2.setNetIncome(new BigDecimal("50"));
        period2.setDividend(new BigDecimal("5"));

        FinancialDto dto = service.computeTtmFrom(new ArrayList<>(List.of(period1, period2)));

        assertThat(dto.getPeriod(), is(nullValue()));
        assertThat(dto.getRevenue(), is("11B"));
        assertThat(dto.getCostGoodsSold(), is("6B"));
        assertThat(dto.getGrossProfit(), is("5B"));
        assertThat(dto.getGrossMargin(), is("45"));
        assertThat(dto.getOperatingExpenses(), is("1.8B"));
        assertThat(dto.getOperatingIncome(), is("3.2B"));
        assertThat(dto.getOperatingMargin(), is("29"));
        assertThat(dto.getNetIncome(), is("300M"));
        assertThat(dto.getNetMargin(), is("3"));
        assertThat(dto.getDividend(), is("30M"));
    }

    @Test
    void computeTtmFromFromHalfYears()
    {
        Company company = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company, true, "25H1", "2504");
        period1.setRevenue(new BigDecimal("3000"));
        period1.setCostGoodsSold(new BigDecimal("2000"));
        period1.setOperatingExpenses(new BigDecimal("500"));
        period1.setNetIncome(new BigDecimal("100"));
        period1.setDividend(new BigDecimal("10.55"));
        Period period2 = Generator.generatePeriod(company, true, "24H2", "2410");
        period2.setRevenue(new BigDecimal("2500"));
        period2.setCostGoodsSold(new BigDecimal("1000"));
        period2.setOperatingExpenses(new BigDecimal("400"));
        period2.setNetIncome(new BigDecimal("50"));
        period2.setDividend(new BigDecimal("9.45"));

        FinancialDto dto = service.computeTtmFrom(new ArrayList<>(List.of(period1, period2)));

        assertThat(dto.getPeriod(), is(nullValue()));
        assertThat(dto.getRevenue(), is("5.5B"));
        assertThat(dto.getCostGoodsSold(), is("3B"));
        assertThat(dto.getGrossProfit(), is("2.5B"));
        assertThat(dto.getGrossMargin(), is("45"));
        assertThat(dto.getOperatingExpenses(), is("900M"));
        assertThat(dto.getOperatingIncome(), is("1.6B"));
        assertThat(dto.getOperatingMargin(), is("29"));
        assertThat(dto.getNetIncome(), is("150M"));
        assertThat(dto.getNetMargin(), is("3"));
        assertThat(dto.getDividend(), is("20M"));
    }

    @Test
    void computeTtmFromYears()
    {
        Company company = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company, true, "25FY", "2501");
        period1.setRevenue(new BigDecimal("3000"));
        period1.setCostGoodsSold(new BigDecimal("2000"));
        period1.setOperatingExpenses(new BigDecimal("500"));
        period1.setNetIncome(new BigDecimal("100"));
        period1.setDividend(new BigDecimal("10"));
        Period period2 = Generator.generatePeriod(company, true, "24FY", "2401");
        period2.setRevenue(new BigDecimal("2500"));
        period2.setCostGoodsSold(new BigDecimal("1000"));
        period2.setOperatingExpenses(new BigDecimal("400"));
        period2.setNetIncome(new BigDecimal("50"));
        period2.setDividend(new BigDecimal("5"));

        FinancialDto dto = service.computeTtmFrom(new ArrayList<>(List.of(period1, period2)));

        assertThat(dto.getRevenue(), is("3B"));
        assertThat(dto.getCostGoodsSold(), is("2B"));
        assertThat(dto.getGrossProfit(), is("1B"));
        assertThat(dto.getGrossMargin(), is("33"));
        assertThat(dto.getOperatingExpenses(), is("500M"));
        assertThat(dto.getOperatingIncome(), is("500M"));
        assertThat(dto.getOperatingMargin(), is("17"));
        assertThat(dto.getNetIncome(), is("100M"));
        assertThat(dto.getNetMargin(), is("3"));
        assertThat(dto.getDividend(), is("10M"));
    }

    @Test
    void computeTtmFromMix()
    {
        Company company = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company, true, "25Q1", "2504");
        period1.setRevenue(new BigDecimal("3000"));
        period1.setCostGoodsSold(new BigDecimal("2000"));
        period1.setOperatingExpenses(new BigDecimal("500"));
        period1.setNetIncome(new BigDecimal("100"));
        period1.setDividend(new BigDecimal("10"));
        Period period2 = Generator.generatePeriod(company, true, "24H2", "2501");
        period2.setRevenue(new BigDecimal("2500"));
        period2.setCostGoodsSold(new BigDecimal("1000"));
        period2.setOperatingExpenses(new BigDecimal("400"));
        period2.setNetIncome(new BigDecimal("50"));
        period2.setDividend(new BigDecimal("5"));

        FinancialDto dto = service.computeTtmFrom(new ArrayList<>(List.of(period1, period2)));

        assertThat(dto.getRevenue(), is("7.33B"));
        assertThat(dto.getCostGoodsSold(), is("4B"));
        assertThat(dto.getGrossProfit(), is("3.33B"));
        assertThat(dto.getGrossMargin(), is("45"));
        assertThat(dto.getOperatingExpenses(), is("1.2B"));
        assertThat(dto.getOperatingIncome(), is("2.13B"));
        assertThat(dto.getOperatingMargin(), is("29"));
        assertThat(dto.getNetIncome(), is("200M"));
        assertThat(dto.getNetMargin(), is("3"));
        assertThat(dto.getDividend(), is("20M"));
    }

    @Test
    void computeTtmFromNoPeriods()
    {
        FinancialDto dto = service.computeTtmFrom(new ArrayList<>());
        assertThat(dto, is(nullValue()));
    }

    @Test
    void computeTtmFromInvalidPeriodName()
    {
        Company company = Generator.generateCompany();
        Period period = Generator.generatePeriod(company, true, "25XY", "2504");

        try {
            service.computeTtmFrom(new ArrayList<>(List.of(period)));
        } catch (ServiceFailureException e) {
            assertThat(e.getMessage(), is("Invalid period name: '25XY'"));
        }
    }

    @Test
    void computeTtmFromNegativeValues()
    {
        Company company = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company, true, "25Q1", "2504");
        period1.setRevenue(new BigDecimal("3000"));
        period1.setCostGoodsSold(new BigDecimal("2000"));
        period1.setOperatingExpenses(new BigDecimal("500"));
        period1.setNetIncome(new BigDecimal("-100"));

        FinancialDto dto = service.computeTtmFrom(new ArrayList<>(List.of(period1)));

        assertThat(dto.getPeriod(), is(nullValue()));
        assertThat(dto.getRevenue(), is("12B"));
        assertThat(dto.getCostGoodsSold(), is("8B"));
        assertThat(dto.getGrossProfit(), is("4B"));
        assertThat(dto.getGrossMargin(), is("33"));
        assertThat(dto.getOperatingExpenses(), is("2B"));
        assertThat(dto.getOperatingIncome(), is("2B"));
        assertThat(dto.getOperatingMargin(), is("17"));
        assertThat(dto.getNetIncome(), is("-400M"));
        assertThat(dto.getNetMargin(), is("-3"));
    }

    @Test
    void computeTtmFromNotReportedPeriod()
    {
        Company company = Generator.generateCompany();
        Period period = Generator.generatePeriod(company, false);

        try {
            service.computeTtmFrom(new ArrayList<>(List.of(period)));
        } catch (ServiceFailureException e) {
            assertThat(e.getMessage(), is("not reported period provided for ttm computation!"));
        }
    }
}
