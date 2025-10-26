package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.Test;
import org.kaleta.Utils;
import org.kaleta.framework.Generator;
import org.kaleta.persistence.api.PeriodDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.rest.dto.PeriodCreateDto;
import org.kaleta.rest.dto.PeriodUpdateDto;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class PeriodServiceTest
{
    @InjectMock
    PeriodDao periodDao;
    @InjectMock
    CompanyService companyService;

    @Inject
    PeriodService periodService;

    @Test
    void create()
    {
        String validName = "25FY";
        String validEndingMonth = "2026-02";
        String validReportDate = "2026-03-30";

        createAndAssertPeriod(validName, validEndingMonth, validReportDate, null);
        createAndAssertPeriod(validName, validEndingMonth, null, null);

        createAndAssertPeriod(validName, validEndingMonth, "", IllegalArgumentException.class);
        createAndAssertPeriod(validName, validEndingMonth, "abcd", IllegalArgumentException.class);
        createAndAssertPeriod(validName, validEndingMonth, "2020-30-01", IllegalArgumentException.class);
        createAndAssertPeriod(validName, validEndingMonth, "2020-12-40", IllegalArgumentException.class);

        createAndAssertPeriod(validName, null, validReportDate, NullPointerException.class);
        createAndAssertPeriod(validName, "", validReportDate, DateTimeParseException.class);
        createAndAssertPeriod(validName, "aaaa", validReportDate, DateTimeParseException.class);
        createAndAssertPeriod(validName, "202510", validReportDate, DateTimeParseException.class);
        createAndAssertPeriod(validName, "2510", validReportDate, DateTimeParseException.class);
        createAndAssertPeriod(validName, "2025-10-10", validReportDate, DateTimeParseException.class);

        createAndAssertPeriod(null, validEndingMonth, validReportDate, IllegalArgumentException.class);
        createAndAssertPeriod("", validEndingMonth, validReportDate, IllegalArgumentException.class);
        createAndAssertPeriod("2025FY", validEndingMonth, validReportDate, IllegalArgumentException.class);
        createAndAssertPeriod("a5FY", validEndingMonth, validReportDate, DateTimeParseException.class);
        createAndAssertPeriod("25FX", validEndingMonth, validReportDate, IllegalArgumentException.class);
    }

    @Test
    void update()
    {
        Company company = Generator.generateCompany();
        Period period = Generator.generatePeriod(company, true);

        when(periodDao.get(period.getId())).thenReturn(period);
        when(periodDao.get(null)).thenThrow(NoResultException.class);

        PeriodUpdateDto dto = new PeriodUpdateDto();

        updateAndAssertPeriod(dto, period, ServiceFailureException.class);

        dto.setId(period.getId());
        updateAndAssertPeriod(dto, period, null);

        dto.setName("");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setName("2025FY");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setName("a5FY");
        updateAndAssertPeriod(dto, period, DateTimeParseException.class);
        dto.setName("25FX");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setName("25FY");
        updateAndAssertPeriod(dto, period, null);

        dto.setEndingMonth("");
        updateAndAssertPeriod(dto, period, DateTimeParseException.class);
        dto.setEndingMonth("aaaa");
        updateAndAssertPeriod(dto, period, DateTimeParseException.class);
        dto.setEndingMonth("202510");
        updateAndAssertPeriod(dto, period, DateTimeParseException.class);
        dto.setEndingMonth("2510");
        updateAndAssertPeriod(dto, period, DateTimeParseException.class);
        dto.setEndingMonth("2025-10-10");
        updateAndAssertPeriod(dto, period, DateTimeParseException.class);
        dto.setEndingMonth("2025-10");
        updateAndAssertPeriod(dto, period, null);

        dto.setReportDate("");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setReportDate("aaaa");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setReportDate("2020-30-01");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setReportDate("2020-12-40");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setReportDate("2020-01-04");
        updateAndAssertPeriod(dto, period, null);

        dto.setShares("");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setShares("x");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setShares("10000");
        updateAndAssertPeriod(dto, period, null);

        dto.setPriceLow("");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setPriceLow("x");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setPriceLow("20");
        updateAndAssertPeriod(dto, period, null);

        dto.setPriceHigh("");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setPriceHigh("x");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setPriceHigh("300");
        updateAndAssertPeriod(dto, period, null);

        dto.setResearch("new findings");
        updateAndAssertPeriod(dto, period, null);

        dto.setRevenue("");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setRevenue("x");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setRevenue("100250");
        updateAndAssertPeriod(dto, period, null);

        dto.setCostGoodsSold("");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setCostGoodsSold("x");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setCostGoodsSold("10250");
        updateAndAssertPeriod(dto, period, null);

        dto.setOperatingExpenses("");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setOperatingExpenses("x");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setOperatingExpenses("1250");
        updateAndAssertPeriod(dto, period, null);

        dto.setNetIncome("");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setNetIncome("x");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setNetIncome("125");
        updateAndAssertPeriod(dto, period, null);

        dto.setDividend("");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setDividend("x");
        updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        dto.setDividend("50");
        updateAndAssertPeriod(dto, period, null);
    }

    private void createAndAssertPeriod(String name, String endingMonth, String reportDate, Class<? extends Exception> expectedException)
    {
        Company company = Generator.generateCompany();
        when(companyService.getCompany(company.getId())).thenReturn(company);

        PeriodCreateDto createDto = new PeriodCreateDto();
        createDto.setCompanyId(company.getId());
        createDto.setName(name);
        createDto.setEndingMonth(endingMonth);
        createDto.setReportDate(reportDate);

        if (expectedException == null) {
            periodService.create(createDto);

            ArgumentCaptor<Period> captor = ArgumentCaptor.forClass(Period.class);
            verify(periodDao).create(captor.capture());

            assertThat(captor.getValue().getCompany().getId(), is(company.getId()));
            assertThat(captor.getValue().getName(), is(PeriodName.valueOf(name)));
            assertThat(captor.getValue().getEndingMonth(), is(YearMonth.parse(endingMonth)));
            assertThat(captor.getValue().getReportDate(), is(Utils.nullableDateValueOf(reportDate)));

            clearInvocations(periodDao);
        } else {
            assertThrows(expectedException, () -> periodService.create(createDto));
        }
    }

    private void updateAndAssertPeriod(PeriodUpdateDto dto, Period period, Class<? extends Exception> expectedException) {
        if (expectedException == null) {
            periodService.update(dto);

            ArgumentCaptor<Period> captor = ArgumentCaptor.forClass(Period.class);
            verify(periodDao).save(captor.capture());

            assertThat(captor.getValue().getCompany().getId(), is(period.getCompany().getId()));

            assertThat(captor.getValue().getName(),
                    (dto.getName() == null) ? is(period.getName()) : is(PeriodName.valueOf(dto.getName())));
            assertThat(captor.getValue().getEndingMonth(),
                    (dto.getEndingMonth() == null) ? is(period.getEndingMonth()) : is(YearMonth.parse(dto.getEndingMonth())));
            assertThat(captor.getValue().getReportDate(),
                    (dto.getReportDate() == null) ? is(period.getReportDate()) : is(Date.valueOf(dto.getReportDate())));
            assertThat(captor.getValue().getShares(),
                    (dto.getShares() == null) ? is(period.getShares()) : is(new BigDecimal(dto.getShares())));
            assertThat(captor.getValue().getPriceHigh(),
                    (dto.getPriceHigh() == null) ? is(period.getPriceHigh()) : is(new BigDecimal(dto.getPriceHigh())));
            assertThat(captor.getValue().getPriceLow(),
                    (dto.getPriceLow() == null) ? is(period.getPriceLow()) : is(new BigDecimal(dto.getPriceLow())));
            assertThat(captor.getValue().getResearch(),
                    (dto.getResearch() == null) ? is(period.getResearch()) : is(dto.getResearch()));
            assertThat(captor.getValue().getRevenue(),
                    (dto.getRevenue() == null) ? is(period.getRevenue()) : is(new BigDecimal(dto.getRevenue())));
            assertThat(captor.getValue().getCostGoodsSold(),
                    (dto.getCostGoodsSold() == null) ? is(period.getCostGoodsSold()) : is(new BigDecimal(dto.getCostGoodsSold())));
            assertThat(captor.getValue().getOperatingExpenses(),
                    (dto.getOperatingExpenses() == null) ? is(period.getOperatingExpenses()) : is(new BigDecimal(dto.getOperatingExpenses())));
            assertThat(captor.getValue().getNetIncome(),
                    (dto.getNetIncome() == null) ? is(period.getNetIncome()) : is(new BigDecimal(dto.getNetIncome())));
            assertThat(captor.getValue().getDividend(),
                    (dto.getDividend() == null) ? is(period.getDividend()) : is(new BigDecimal(dto.getDividend())));

            clearInvocations(periodDao);
        } else {
            assertThrows(expectedException, () -> periodService.update(dto));
        }
    }



//    @Test
//    void getByCompanyId() {
//        Company company = Generator.generateCompany();
//        Period period1 = Generator.generatePeriod(company, true, "25Q1", "2504");
//        Period period2 = Generator.generatePeriod(company, false, "25Q2", "2507");
//        Period period3 = Generator.generatePeriod(company, true, "24Q4", "2501");
//
//        when(companyDao.get(company.getId())).thenReturn(company);
//        when(periodDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(period1, period2, period3)));
//
//        List<Period> periods = periodService.getFor(company.getId());
//
//        assertThat(periods.get(0).getId(), is(period2.getId()));
//        assertThat(periods.get(1).getId(), is(period1.getId()));
//        assertThat(periods.get(2).getId(), is(period3.getId()));
//    }
//


//
//
//    @Test
//    void dtoFromPeriod()
//    {
//        Company company = Generator.generateCompany();
//        Period period = new Period();
//        period.setCompany(company);
//        period.setName("YOLO");
//        period.setEndingMonth("3012");
//        period.setReportDate(Date.valueOf("2026-03-30"));
//        period.setShares(new BigDecimal("10000"));
//        period.setPriceLow(new BigDecimal("20"));
//        period.setPriceHigh(new BigDecimal("300"));
//        period.setResearch("new findings");
//        period.setRevenue(new  BigDecimal("100250"));
//        period.setCostGoodsSold(new BigDecimal("10250"));
//        period.setOperatingExpenses(new  BigDecimal("1250"));
//        period.setNetIncome(new BigDecimal("125"));
//        period.setDividend(new BigDecimal("50"));
//
//        PeriodDto dto = periodService.dtoFrom(period);
//
//        assertThat(dto.getId(), is(period.getId()));
//        assertThat(dto.getName(), is(period.getName()));
//        assertThat(dto.getEndingMonth(), is("12/2030"));
//        assertThat(dto.getReportDate(), is("30.03.2026"));
//        assertThat(dto.getShares(), is("10B"));
//        assertThat(dto.getPriceHigh(), is("300"));
//        assertThat(dto.getPriceLow(), is("20"));
//        assertThat(dto.getResearch(), is(period.getResearch()));
//        assertThat(dto.getRevenue(), is("100.25B"));
//        assertThat(dto.getCostGoodsSold(), is("10.25B"));
//        assertThat(dto.getOperatingExpenses(), is("1.25B"));
//        assertThat(dto.getNetIncome(), is("125M"));
//        assertThat(dto.getDividend(), is("50M"));
//    }
//
//    @Test
//    void computeFinancialPeriod()
//    {
//        Company company = Generator.generateCompany();
//        Period period = new Period();
//        period.setCompany(company);
//        period.setName("YOLO");
//        period.setEndingMonth("3012");
//        period.setReportDate(Date.valueOf("2026-03-30"));
//        period.setShares(new BigDecimal("10000"));
//        period.setPriceLow(new BigDecimal("20"));
//        period.setPriceHigh(new BigDecimal("300"));
//        period.setResearch("new findings");
//        period.setRevenue(new  BigDecimal("3000"));
//        period.setCostGoodsSold(new BigDecimal("2000"));
//        period.setOperatingExpenses(new  BigDecimal("500"));
//        period.setNetIncome(new BigDecimal("-100"));
//        period.setDividend(new BigDecimal("40"));
//
//        FinancialDto dto = periodService.computeFinancialFrom(period);
//
//        assertThat(dto.getPeriod(), is("YOLO"));
//        assertThat(dto.getRevenue(), is("3B"));
//        assertThat(dto.getCostGoodsSold(), is("2B"));
//        assertThat(dto.getGrossProfit(), is("1B"));
//        assertThat(dto.getGrossMargin(), is("33"));
//        assertThat(dto.getOperatingExpenses(), is("500M"));
//        assertThat(dto.getOperatingIncome(), is("500M"));
//        assertThat(dto.getOperatingMargin(), is("17"));
//        assertThat(dto.getNetIncome(), is("-100M"));
//        assertThat(dto.getNetMargin(), is("-3"));
//        assertThat(dto.getDividend(), is("40M"));
//    }
//
//    @Test
//    void computeTtmFromQuarters()
//    {
//        Company company = Generator.generateCompany();
//        Period period1 = Generator.generatePeriod(company, true, "25Q1", "2504");
//        period1.setRevenue(new BigDecimal("3000"));
//        period1.setCostGoodsSold(new BigDecimal("2000"));
//        period1.setOperatingExpenses(new BigDecimal("500"));
//        period1.setNetIncome(new BigDecimal("100"));
//        period1.setDividend(new BigDecimal("10"));
//        Period period2 = Generator.generatePeriod(company, true, "24Q4", "2501");
//        period2.setRevenue(new BigDecimal("2500"));
//        period2.setCostGoodsSold(new BigDecimal("1000"));
//        period2.setOperatingExpenses(new BigDecimal("400"));
//        period2.setNetIncome(new BigDecimal("50"));
//        period2.setDividend(new BigDecimal("5"));
//
//        Period ttm = periodService.computeTtmFrom(new ArrayList<>(List.of(period1, period2)));
//
//        assertThat(ttm.getName(), is(nullValue()));
//        assertThat(ttm.getRevenue(), is(new BigDecimal("11000")));
//        assertThat(ttm.getCostGoodsSold(), is(new BigDecimal("6000")));
//        assertThat(ttm.getOperatingExpenses(), is(new BigDecimal("1800")));
//        assertThat(ttm.getNetIncome(), is(new BigDecimal("300")));
//        assertThat(ttm.getDividend(), is(new BigDecimal("30")));
//        assertThat(ttm.getShares(), is(period1.getShares()));
//    }
//
//    @Test
//    void computeTtmFromFromHalfYears()
//    {
//        Company company = Generator.generateCompany();
//        Period period1 = Generator.generatePeriod(company, true, "25H1", "2504");
//        period1.setRevenue(new BigDecimal("3000"));
//        period1.setCostGoodsSold(new BigDecimal("2000"));
//        period1.setOperatingExpenses(new BigDecimal("500"));
//        period1.setNetIncome(new BigDecimal("100"));
//        period1.setDividend(new BigDecimal("10.55"));
//        Period period2 = Generator.generatePeriod(company, true, "24H2", "2410");
//        period2.setRevenue(new BigDecimal("2500"));
//        period2.setCostGoodsSold(new BigDecimal("1000"));
//        period2.setOperatingExpenses(new BigDecimal("400"));
//        period2.setNetIncome(new BigDecimal("50"));
//        period2.setDividend(new BigDecimal("9.45"));
//
//        Period ttm = periodService.computeTtmFrom(new ArrayList<>(List.of(period1, period2)));
//
//        assertThat(ttm.getName(), is(nullValue()));
//        assertThat(ttm.getRevenue(), is(new BigDecimal("5500")));
//        assertThat(ttm.getCostGoodsSold(), is(new BigDecimal("3000")));
//        assertThat(ttm.getOperatingExpenses(), is(new BigDecimal("900")));
//        assertThat(ttm.getNetIncome(), is(new BigDecimal("150")));
//        assertThat(ttm.getDividend(), is(new BigDecimal("20")));
//        assertThat(ttm.getShares(), is(period1.getShares()));
//    }
//
//    @Test
//    void computeTtmFromYears()
//    {
//        Company company = Generator.generateCompany();
//        Period period1 = Generator.generatePeriod(company, true, "25FY", "2501");
//        period1.setRevenue(new BigDecimal("3000"));
//        period1.setCostGoodsSold(new BigDecimal("2000"));
//        period1.setOperatingExpenses(new BigDecimal("500"));
//        period1.setNetIncome(new BigDecimal("100"));
//        period1.setDividend(new BigDecimal("10"));
//        Period period2 = Generator.generatePeriod(company, true, "24FY", "2401");
//        period2.setRevenue(new BigDecimal("2500"));
//        period2.setCostGoodsSold(new BigDecimal("1000"));
//        period2.setOperatingExpenses(new BigDecimal("400"));
//        period2.setNetIncome(new BigDecimal("50"));
//        period2.setDividend(new BigDecimal("5"));
//
//        Period ttm = periodService.computeTtmFrom(new ArrayList<>(List.of(period1, period2)));
//
//        assertThat(ttm.getName(), is(nullValue()));
//        assertThat(ttm.getRevenue(), is(new BigDecimal("3000")));
//        assertThat(ttm.getCostGoodsSold(), is(new BigDecimal("2000")));
//        assertThat(ttm.getOperatingExpenses(), is(new BigDecimal("500")));
//        assertThat(ttm.getNetIncome(), is(new BigDecimal("100")));
//        assertThat(ttm.getDividend(), is(new BigDecimal("10")));
//        assertThat(ttm.getShares(), is(period1.getShares()));
//    }
//
//    @Test
//    void computeTtmFromMix()
//    {
//        Company company = Generator.generateCompany();
//        Period period1 = Generator.generatePeriod(company, true, "25Q1", "2504");
//        period1.setRevenue(new BigDecimal("3000"));
//        period1.setCostGoodsSold(new BigDecimal("2000"));
//        period1.setOperatingExpenses(new BigDecimal("500"));
//        period1.setNetIncome(new BigDecimal("100"));
//        period1.setDividend(new BigDecimal("10"));
//        Period period2 = Generator.generatePeriod(company, true, "24H2", "2501");
//        period2.setRevenue(new BigDecimal("2500"));
//        period2.setCostGoodsSold(new BigDecimal("1000"));
//        period2.setOperatingExpenses(new BigDecimal("400"));
//        period2.setNetIncome(new BigDecimal("50"));
//        period2.setDividend(new BigDecimal("5"));
//
//        Period ttm = periodService.computeTtmFrom(new ArrayList<>(List.of(period1, period2)));
//
//        assertThat(ttm.getName(), is(nullValue()));
//        assertThat(ttm.getRevenue(), is(new BigDecimal("7333")));
//        assertThat(ttm.getCostGoodsSold(), is(new BigDecimal("4000")));
//        assertThat(ttm.getOperatingExpenses(), is(new BigDecimal("1200")));
//        assertThat(ttm.getNetIncome(), is(new BigDecimal("200")));
//        assertThat(ttm.getDividend(), is(new BigDecimal("20")));
//        assertThat(ttm.getShares(), is(period1.getShares()));
//    }
//
//    @Test
//    void computeTtmFromNoPeriods()
//    {
//        Period ttm = periodService.computeTtmFrom(new ArrayList<>());
//        assertThat(ttm, is(nullValue()));
//    }
//
//    @Test
//    void computeTtmFromInvalidPeriodName()
//    {
//        Company company = Generator.generateCompany();
//        Period period = Generator.generatePeriod(company, true, "25XY", "2504");
//
//        try {
//            periodService.computeTtmFrom(new ArrayList<>(List.of(period)));
//        } catch (ServiceFailureException e) {
//            assertThat(e.getMessage(), is("Invalid period name: '25XY'"));
//        }
//    }
//
//    @Test
//    void computeTtmFromNotReportedPeriod()
//    {
//        Company company = Generator.generateCompany();
//        Period period = Generator.generatePeriod(company, false);
//
//        try {
//            periodService.computeTtmFrom(new ArrayList<>(List.of(period)));
//        } catch (ServiceFailureException e) {
//            assertThat(e.getMessage(), is("not reported period provided for ttm computation!"));
//        }
//    }
}
