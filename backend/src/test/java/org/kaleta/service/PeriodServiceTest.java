package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.Test;
import org.kaleta.Utils;
import org.kaleta.framework.Generator;
import org.kaleta.model.Periods;
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
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
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

    @Test
    void getBy_quarters()
    {
        Company company = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company, PeriodName.valueOf("25Q2"), YearMonth.of(2025, 7), "1000", "300", "200", "100", "20");
        Period period2 = Generator.generatePeriod(company, false, PeriodName.valueOf("25Q3"), YearMonth.of(2025, 10));
        Period period3 = Generator.generatePeriod(company, PeriodName.valueOf("25Q1"), YearMonth.of(2025, 4), "950", "400", "300", "50", "10");

        when(periodDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(period1, period2, period3)));

        Periods periods = periodService.getBy(company.getId());

        assertThat(periods.getPeriods().size(), is(3));
        assertPeriod(periods.getPeriods().get(0), period2);
        assertPeriod(periods.getPeriods().get(1), period1);
        assertPeriod(periods.getPeriods().get(2), period3);

        assertThat(periods.getFinancials().size(), is(2));

        assertThat(periods.getFinancials().get(0).getPeriod(), is(period1.getName()));
        assertThat(periods.getFinancials().get(0).getRevenue(), comparesEqualTo(period1.getRevenue()));
        assertThat(periods.getFinancials().get(0).getCostGoodsSold(), comparesEqualTo(period1.getCostGoodsSold()));
        assertThat(periods.getFinancials().get(0).getGrossProfit(), comparesEqualTo(new BigDecimal("700")));
        assertThat(periods.getFinancials().get(0).getGrossMargin(), comparesEqualTo(new BigDecimal("70")));
        assertThat(periods.getFinancials().get(0).getOperatingExpenses(), comparesEqualTo(period1.getOperatingExpenses()));
        assertThat(periods.getFinancials().get(0).getOperatingIncome(), comparesEqualTo(new BigDecimal("500")));
        assertThat(periods.getFinancials().get(0).getOperatingMargin(), comparesEqualTo(new BigDecimal("50")));
        assertThat(periods.getFinancials().get(0).getNetIncome(), comparesEqualTo(period1.getNetIncome()));
        assertThat(periods.getFinancials().get(0).getNetMargin(), comparesEqualTo(new BigDecimal("10")));
        assertThat(periods.getFinancials().get(0).getDividend(), comparesEqualTo(period1.getDividend()));

        assertThat(periods.getFinancials().get(1).getPeriod(), is(period3.getName()));
        assertThat(periods.getFinancials().get(1).getRevenue(), comparesEqualTo(period3.getRevenue()));
        assertThat(periods.getFinancials().get(1).getCostGoodsSold(), comparesEqualTo(period3.getCostGoodsSold()));
        assertThat(periods.getFinancials().get(1).getGrossProfit(), comparesEqualTo(new BigDecimal("550")));
        assertThat(periods.getFinancials().get(1).getGrossMargin(), comparesEqualTo(new BigDecimal("57.89")));
        assertThat(periods.getFinancials().get(1).getOperatingExpenses(), comparesEqualTo(period3.getOperatingExpenses()));
        assertThat(periods.getFinancials().get(1).getOperatingIncome(), comparesEqualTo(new BigDecimal("250")));
        assertThat(periods.getFinancials().get(1).getOperatingMargin(), comparesEqualTo(new BigDecimal("26.32")));
        assertThat(periods.getFinancials().get(1).getNetIncome(), comparesEqualTo(period3.getNetIncome()));
        assertThat(periods.getFinancials().get(1).getNetMargin(), comparesEqualTo(new BigDecimal("5.26")));
        assertThat(periods.getFinancials().get(1).getDividend(), comparesEqualTo(period3.getDividend()));

        assertThat(periods.getTtm(), is(notNullValue()));

        assertThat(periods.getTtm().getRevenue(), comparesEqualTo(new BigDecimal("3900")));
        assertThat(periods.getTtm().getCostGoodsSold(), comparesEqualTo(new BigDecimal("1400")));
        assertThat(periods.getTtm().getGrossProfit(), comparesEqualTo(new BigDecimal("2500")));
        assertThat(periods.getTtm().getGrossMargin(), comparesEqualTo(new BigDecimal("64.1")));

        assertThat(periods.getTtm().getOperatingExpenses(), comparesEqualTo(new BigDecimal("1000")));
        assertThat(periods.getTtm().getOperatingIncome(), comparesEqualTo(new BigDecimal("1500")));
        assertThat(periods.getTtm().getOperatingMargin(), comparesEqualTo(new BigDecimal("38.46")));

        assertThat(periods.getTtm().getNetIncome(), comparesEqualTo(new BigDecimal("300")));
        assertThat(periods.getTtm().getNetMargin(), comparesEqualTo(new BigDecimal("7.69")));

        assertThat(periods.getTtm().getDividend(), comparesEqualTo(new BigDecimal("60")));
    }

    @Test
    void getBy_halves()
    {
        Company company = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company, PeriodName.valueOf("24H1"), YearMonth.of(2024, 4), "1000", "300", "200", "100", "20");
        Period period2 = Generator.generatePeriod(company, false, PeriodName.valueOf("25H1"), YearMonth.of(2025, 4));
        Period period3 = Generator.generatePeriod(company, PeriodName.valueOf("24H2"), YearMonth.of(2024, 10), "950", "400", "300", "50", "10");

        when(periodDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(period1, period2, period3)));

        Periods periods = periodService.getBy(company.getId());

        assertThat(periods.getPeriods().size(), is(3));
        assertPeriod(periods.getPeriods().get(0), period2);
        assertPeriod(periods.getPeriods().get(1), period3);
        assertPeriod(periods.getPeriods().get(2), period1);

        assertThat(periods.getFinancials().size(), is(2));

        assertThat(periods.getFinancials().get(0).getPeriod(), is(period3.getName()));
        assertThat(periods.getFinancials().get(0).getRevenue(), comparesEqualTo(period3.getRevenue()));
        assertThat(periods.getFinancials().get(0).getCostGoodsSold(), comparesEqualTo(period3.getCostGoodsSold()));
        assertThat(periods.getFinancials().get(0).getGrossProfit(), comparesEqualTo(new BigDecimal("550")));
        assertThat(periods.getFinancials().get(0).getGrossMargin(), comparesEqualTo(new BigDecimal("57.89")));
        assertThat(periods.getFinancials().get(0).getOperatingExpenses(), comparesEqualTo(period3.getOperatingExpenses()));
        assertThat(periods.getFinancials().get(0).getOperatingIncome(), comparesEqualTo(new BigDecimal("250")));
        assertThat(periods.getFinancials().get(0).getOperatingMargin(), comparesEqualTo(new BigDecimal("26.32")));
        assertThat(periods.getFinancials().get(0).getNetIncome(), comparesEqualTo(period3.getNetIncome()));
        assertThat(periods.getFinancials().get(0).getNetMargin(), comparesEqualTo(new BigDecimal("5.26")));
        assertThat(periods.getFinancials().get(0).getDividend(), comparesEqualTo(period3.getDividend()));

        assertThat(periods.getFinancials().get(1).getPeriod(), is(period1.getName()));
        assertThat(periods.getFinancials().get(1).getRevenue(), comparesEqualTo(period1.getRevenue()));
        assertThat(periods.getFinancials().get(1).getCostGoodsSold(), comparesEqualTo(period1.getCostGoodsSold()));
        assertThat(periods.getFinancials().get(1).getGrossProfit(), comparesEqualTo(new BigDecimal("700")));
        assertThat(periods.getFinancials().get(1).getGrossMargin(), comparesEqualTo(new BigDecimal("70")));
        assertThat(periods.getFinancials().get(1).getOperatingExpenses(), comparesEqualTo(period1.getOperatingExpenses()));
        assertThat(periods.getFinancials().get(1).getOperatingIncome(), comparesEqualTo(new BigDecimal("500")));
        assertThat(periods.getFinancials().get(1).getOperatingMargin(), comparesEqualTo(new BigDecimal("50")));
        assertThat(periods.getFinancials().get(1).getNetIncome(), comparesEqualTo(period1.getNetIncome()));
        assertThat(periods.getFinancials().get(1).getNetMargin(), comparesEqualTo(new BigDecimal("10")));
        assertThat(periods.getFinancials().get(1).getDividend(), comparesEqualTo(period1.getDividend()));

        assertThat(periods.getTtm(), is(notNullValue()));

        assertThat(periods.getTtm().getRevenue(), comparesEqualTo(new BigDecimal("1950")));
        assertThat(periods.getTtm().getCostGoodsSold(), comparesEqualTo(new BigDecimal("700")));
        assertThat(periods.getTtm().getGrossProfit(), comparesEqualTo(new BigDecimal("1250")));
        assertThat(periods.getTtm().getGrossMargin(), comparesEqualTo(new BigDecimal("64.1")));

        assertThat(periods.getTtm().getOperatingExpenses(), comparesEqualTo(new BigDecimal("500")));
        assertThat(periods.getTtm().getOperatingIncome(), comparesEqualTo(new BigDecimal("750")));
        assertThat(periods.getTtm().getOperatingMargin(), comparesEqualTo(new BigDecimal("38.46")));

        assertThat(periods.getTtm().getNetIncome(), comparesEqualTo(new BigDecimal("150")));
        assertThat(periods.getTtm().getNetMargin(), comparesEqualTo(new BigDecimal("7.69")));

        assertThat(periods.getTtm().getDividend(), comparesEqualTo(new BigDecimal("30")));
    }

    @Test
    void getBy_years()
    {
        Company company = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company, PeriodName.valueOf("25FY"), YearMonth.of(2025, 12), "1000", "300", "200", "100", "20");
        Period period2 = Generator.generatePeriod(company, PeriodName.valueOf("24FY"), YearMonth.of(2024, 12), "950", "400", "300", "50", "10");
        Period period3 = Generator.generatePeriod(company, false ,PeriodName.valueOf("26FY"), YearMonth.of(2026, 12));

        when(periodDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(period1, period2, period3)));

        Periods periods = periodService.getBy(company.getId());

        assertThat(periods.getPeriods().size(), is(3));
        assertPeriod(periods.getPeriods().get(0), period3);
        assertPeriod(periods.getPeriods().get(1), period1);
        assertPeriod(periods.getPeriods().get(2), period2);

        assertThat(periods.getFinancials().size(), is(2));

        assertThat(periods.getFinancials().get(0).getPeriod(), is(period1.getName()));
        assertThat(periods.getFinancials().get(0).getRevenue(), comparesEqualTo(period1.getRevenue()));
        assertThat(periods.getFinancials().get(0).getCostGoodsSold(), comparesEqualTo(period1.getCostGoodsSold()));
        assertThat(periods.getFinancials().get(0).getGrossProfit(), comparesEqualTo(new BigDecimal("700")));
        assertThat(periods.getFinancials().get(0).getGrossMargin(), comparesEqualTo(new BigDecimal("70")));
        assertThat(periods.getFinancials().get(0).getOperatingExpenses(), comparesEqualTo(period1.getOperatingExpenses()));
        assertThat(periods.getFinancials().get(0).getOperatingIncome(), comparesEqualTo(new BigDecimal("500")));
        assertThat(periods.getFinancials().get(0).getOperatingMargin(), comparesEqualTo(new BigDecimal("50")));
        assertThat(periods.getFinancials().get(0).getNetIncome(), comparesEqualTo(period1.getNetIncome()));
        assertThat(periods.getFinancials().get(0).getNetMargin(), comparesEqualTo(new BigDecimal("10")));
        assertThat(periods.getFinancials().get(0).getDividend(), comparesEqualTo(period1.getDividend()));

        assertThat(periods.getFinancials().get(1).getPeriod(), is(period2.getName()));
        assertThat(periods.getFinancials().get(1).getRevenue(), comparesEqualTo(period2.getRevenue()));
        assertThat(periods.getFinancials().get(1).getCostGoodsSold(), comparesEqualTo(period2.getCostGoodsSold()));
        assertThat(periods.getFinancials().get(1).getGrossProfit(), comparesEqualTo(new BigDecimal("550")));
        assertThat(periods.getFinancials().get(1).getGrossMargin(), comparesEqualTo(new BigDecimal("57.89")));
        assertThat(periods.getFinancials().get(1).getOperatingExpenses(), comparesEqualTo(period2.getOperatingExpenses()));
        assertThat(periods.getFinancials().get(1).getOperatingIncome(), comparesEqualTo(new BigDecimal("250")));
        assertThat(periods.getFinancials().get(1).getOperatingMargin(), comparesEqualTo(new BigDecimal("26.32")));
        assertThat(periods.getFinancials().get(1).getNetIncome(), comparesEqualTo(period2.getNetIncome()));
        assertThat(periods.getFinancials().get(1).getNetMargin(), comparesEqualTo(new BigDecimal("5.26")));
        assertThat(periods.getFinancials().get(1).getDividend(), comparesEqualTo(period2.getDividend()));

        assertThat(periods.getTtm(), is(notNullValue()));

        assertThat(periods.getTtm().getRevenue(), comparesEqualTo(new BigDecimal("1000")));
        assertThat(periods.getTtm().getCostGoodsSold(), comparesEqualTo(new BigDecimal("300")));
        assertThat(periods.getTtm().getGrossProfit(), comparesEqualTo(new BigDecimal("700")));
        assertThat(periods.getTtm().getGrossMargin(), comparesEqualTo(new BigDecimal("70")));

        assertThat(periods.getTtm().getOperatingExpenses(), comparesEqualTo(new BigDecimal("200")));
        assertThat(periods.getTtm().getOperatingIncome(), comparesEqualTo(new BigDecimal("500")));
        assertThat(periods.getTtm().getOperatingMargin(), comparesEqualTo(new BigDecimal("50")));

        assertThat(periods.getTtm().getNetIncome(), comparesEqualTo(new BigDecimal("100")));
        assertThat(periods.getTtm().getNetMargin(), comparesEqualTo(new BigDecimal("10")));

        assertThat(periods.getTtm().getDividend(), comparesEqualTo(new BigDecimal("20")));
    }

    @Test
    void getBy_mix()
    {
        Company company = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company, PeriodName.valueOf("24FY"), YearMonth.of(2024, 12), "1000", "300", "200", "100", "20");
        Period period2 = Generator.generatePeriod(company, PeriodName.valueOf("25Q1"), YearMonth.of(2025, 3), "500", "100", "100", "100", "10");
        Period period3 = Generator.generatePeriod(company, false ,PeriodName.valueOf("25Q2"), YearMonth.of(2025, 6));

        when(periodDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(period1, period2, period3)));

        Periods periods = periodService.getBy(company.getId());

        assertThat(periods.getPeriods().size(), is(3));
        assertPeriod(periods.getPeriods().get(0), period3);
        assertPeriod(periods.getPeriods().get(1), period2);
        assertPeriod(periods.getPeriods().get(2), period1);

        assertThat(periods.getFinancials().size(), is(2));

        assertThat(periods.getFinancials().get(0).getPeriod(), is(period2.getName()));
        assertThat(periods.getFinancials().get(0).getRevenue(), comparesEqualTo(period2.getRevenue()));
        assertThat(periods.getFinancials().get(0).getCostGoodsSold(), comparesEqualTo(period2.getCostGoodsSold()));
        assertThat(periods.getFinancials().get(0).getGrossProfit(), comparesEqualTo(new BigDecimal("400")));
        assertThat(periods.getFinancials().get(0).getGrossMargin(), comparesEqualTo(new BigDecimal("80")));
        assertThat(periods.getFinancials().get(0).getOperatingExpenses(), comparesEqualTo(period2.getOperatingExpenses()));
        assertThat(periods.getFinancials().get(0).getOperatingIncome(), comparesEqualTo(new BigDecimal("300")));
        assertThat(periods.getFinancials().get(0).getOperatingMargin(), comparesEqualTo(new BigDecimal("60")));
        assertThat(periods.getFinancials().get(0).getNetIncome(), comparesEqualTo(period2.getNetIncome()));
        assertThat(periods.getFinancials().get(0).getNetMargin(), comparesEqualTo(new BigDecimal("20")));
        assertThat(periods.getFinancials().get(0).getDividend(), comparesEqualTo(period2.getDividend()));

        assertThat(periods.getFinancials().get(1).getPeriod(), is(period1.getName()));
        assertThat(periods.getFinancials().get(1).getRevenue(), comparesEqualTo(period1.getRevenue()));
        assertThat(periods.getFinancials().get(1).getCostGoodsSold(), comparesEqualTo(period1.getCostGoodsSold()));
        assertThat(periods.getFinancials().get(1).getGrossProfit(), comparesEqualTo(new BigDecimal("700")));
        assertThat(periods.getFinancials().get(1).getGrossMargin(), comparesEqualTo(new BigDecimal("70")));
        assertThat(periods.getFinancials().get(1).getOperatingExpenses(), comparesEqualTo(period1.getOperatingExpenses()));
        assertThat(periods.getFinancials().get(1).getOperatingIncome(), comparesEqualTo(new BigDecimal("500")));
        assertThat(periods.getFinancials().get(1).getOperatingMargin(), comparesEqualTo(new BigDecimal("50")));
        assertThat(periods.getFinancials().get(1).getNetIncome(), comparesEqualTo(period1.getNetIncome()));
        assertThat(periods.getFinancials().get(1).getNetMargin(), comparesEqualTo(new BigDecimal("10")));
        assertThat(periods.getFinancials().get(1).getDividend(), comparesEqualTo(period1.getDividend()));

        assertThat(periods.getTtm(), is(notNullValue()));

        assertThat(periods.getTtm().getRevenue(), comparesEqualTo(new BigDecimal("1250")));
        assertThat(periods.getTtm().getCostGoodsSold(), comparesEqualTo(new BigDecimal("325")));
        assertThat(periods.getTtm().getGrossProfit(), comparesEqualTo(new BigDecimal("925")));
        assertThat(periods.getTtm().getGrossMargin(), comparesEqualTo(new BigDecimal("74")));

        assertThat(periods.getTtm().getOperatingExpenses(), comparesEqualTo(new BigDecimal("250")));
        assertThat(periods.getTtm().getOperatingIncome(), comparesEqualTo(new BigDecimal("675")));
        assertThat(periods.getTtm().getOperatingMargin(), comparesEqualTo(new BigDecimal("54")));

        assertThat(periods.getTtm().getNetIncome(), comparesEqualTo(new BigDecimal("175")));
        assertThat(periods.getTtm().getNetMargin(), comparesEqualTo(new BigDecimal("14")));

        assertThat(periods.getTtm().getDividend(), comparesEqualTo(new BigDecimal("25")));
    }

    @Test
    void getBy_notReported()
    {
        Company company = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company, false, PeriodName.valueOf("24FY"), YearMonth.of(2024, 12));
        Period period2 = Generator.generatePeriod(company, false, PeriodName.valueOf("25Q1"), YearMonth.of(2025, 3));
        Period period3 = Generator.generatePeriod(company, false ,PeriodName.valueOf("25Q2"), YearMonth.of(2025, 6));

        when(periodDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(period1, period2, period3)));

        Periods periods = periodService.getBy(company.getId());

        assertThat(periods.getPeriods().size(), is(3));
        assertPeriod(periods.getPeriods().get(0), period3);
        assertPeriod(periods.getPeriods().get(1), period2);
        assertPeriod(periods.getPeriods().get(2), period1);

        assertThat(periods.getFinancials().size(), is(0));

        assertThat(periods.getTtm(), is(nullValue()));
    }

    @Test
    void getBy_noPeriods()
    {
        Company company = Generator.generateCompany();

        when(periodDao.list(company.getId())).thenReturn(new ArrayList<>());

        Periods periods = periodService.getBy(company.getId());

        assertThat(periods.getPeriods().size(), is(0));

        assertThat(periods.getFinancials().size(), is(0));

        assertThat(periods.getTtm(), is(nullValue()));
    }

    private void assertPeriod(Periods.Period actual, Period expected)
    {
        assertThat(actual.getId(), is(expected.getId()));
        assertThat(actual.getName(), is(expected.getName()));
        assertThat(actual.getShares(), is(expected.getShares()));
        assertThat(actual.getResearch(), is(expected.getResearch()));
        assertThat(actual.getPriceHigh(), is(expected.getPriceHigh()));
        assertThat(actual.getPriceLow(), is(expected.getPriceLow()));
        assertThat(actual.getEndingMonth(), is(expected.getEndingMonth()));
        assertThat(actual.getReportDate(), is(expected.getReportDate()));
        if (expected.getRevenue() != null) {
            assertThat(actual.getFinancial(), is(notNullValue()));
            assertThat(actual.getFinancial().getPeriod(), is(expected.getName()));
            assertThat(actual.getFinancial().getRevenue(), is(expected.getRevenue()));
            assertThat(actual.getFinancial().getCostGoodsSold(), is(expected.getCostGoodsSold()));
            assertThat(actual.getFinancial().getGrossProfit(), is(notNullValue()));
            assertThat(actual.getFinancial().getGrossMargin(), is(notNullValue()));
            assertThat(actual.getFinancial().getOperatingExpenses(), is(expected.getOperatingExpenses()));
            assertThat(actual.getFinancial().getOperatingIncome(), is(notNullValue()));
            assertThat(actual.getFinancial().getOperatingMargin(), is(notNullValue()));
            assertThat(actual.getFinancial().getNetIncome(), is(expected.getNetIncome()));
            assertThat(actual.getFinancial().getNetMargin(), is(notNullValue()));
            assertThat(actual.getFinancial().getDividend(), is(expected.getDividend()));
        } else {
            assertThat(actual.getFinancial(), is(nullValue()));
        }
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
}
