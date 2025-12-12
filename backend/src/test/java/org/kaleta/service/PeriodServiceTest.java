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
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kaleta.framework.Assert.assertBigDecimals;
import static org.kaleta.framework.InvalidValues.invalidBigDecimals;
import static org.kaleta.framework.InvalidValues.invalidDates;
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

        invalidDates().forEach(d -> createAndAssertPeriod(validName, validEndingMonth, d, IllegalArgumentException.class));

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

        invalidDates().forEach(invalidDate -> {
            dto.setReportDate(invalidDate);
            updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        });
        dto.setReportDate(Generator.randomDate(2025));
        updateAndAssertPeriod(dto, period, null);

        invalidBigDecimals().forEach(invalidBigDecimal -> {
            dto.setShares(invalidBigDecimal);
            updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        });
        dto.setShares(String.valueOf(Generator.randomBigDecimal(999999, 2)));
        updateAndAssertPeriod(dto, period, null);

        invalidBigDecimals().forEach(invalidBigDecimal -> {
            dto.setPriceLow(invalidBigDecimal);
            updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        });
        dto.setPriceLow(String.valueOf(Generator.randomBigDecimal(999999, 4)));
        updateAndAssertPeriod(dto, period, null);

        invalidBigDecimals().forEach(invalidBigDecimal -> {
            dto.setPriceHigh(invalidBigDecimal);
            updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        });
        dto.setPriceHigh(String.valueOf(Generator.randomBigDecimal(999999, 4)));
        updateAndAssertPeriod(dto, period, null);

        dto.setResearch("new findings");
        updateAndAssertPeriod(dto, period, null);

        invalidBigDecimals().forEach(invalidBigDecimal -> {
            dto.setRevenue(invalidBigDecimal);
            updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        });
        dto.setRevenue(String.valueOf(Generator.randomBigDecimal(999999, 2)));
        updateAndAssertPeriod(dto, period, null);

        invalidBigDecimals().forEach(invalidBigDecimal -> {
            dto.setCostGoodsSold(invalidBigDecimal);
            updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        });
        dto.setCostGoodsSold(String.valueOf(Generator.randomBigDecimal(999999, 2)));
        updateAndAssertPeriod(dto, period, null);

        invalidBigDecimals().forEach(invalidBigDecimal -> {
            dto.setOperatingExpenses(invalidBigDecimal);
            updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        });
        dto.setOperatingExpenses(String.valueOf(Generator.randomBigDecimal(999999, 2)));
        updateAndAssertPeriod(dto, period, null);

        invalidBigDecimals().forEach(invalidBigDecimal -> {
            dto.setNetIncome(invalidBigDecimal);
            updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        });
        dto.setNetIncome(String.valueOf(Generator.randomBigDecimal(999999, 2)));
        updateAndAssertPeriod(dto, period, null);

        invalidBigDecimals().forEach(invalidBigDecimal -> {
            dto.setDividend(invalidBigDecimal);
            updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        });
        dto.setDividend(String.valueOf(Generator.randomBigDecimal(999999, 2)));
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
        assertBigDecimals(periods.getFinancials().get(0).getRevenue(), period1.getRevenue());
        assertBigDecimals(periods.getFinancials().get(0).getCostGoodsSold(), period1.getCostGoodsSold());
        assertBigDecimals(periods.getFinancials().get(0).getGrossProfit(), new BigDecimal("700"));
        assertBigDecimals(periods.getFinancials().get(0).getGrossMargin(), new BigDecimal("70"));
        assertBigDecimals(periods.getFinancials().get(0).getOperatingExpenses(), period1.getOperatingExpenses());
        assertBigDecimals(periods.getFinancials().get(0).getOperatingIncome(), new BigDecimal("500"));
        assertBigDecimals(periods.getFinancials().get(0).getOperatingMargin(), new BigDecimal("50"));
        assertBigDecimals(periods.getFinancials().get(0).getNetIncome(), period1.getNetIncome());
        assertBigDecimals(periods.getFinancials().get(0).getNetMargin(), new BigDecimal("10"));
        assertBigDecimals(periods.getFinancials().get(0).getDividend(), period1.getDividend());

        assertThat(periods.getFinancials().get(1).getPeriod(), is(period3.getName()));
        assertBigDecimals(periods.getFinancials().get(1).getRevenue(), period3.getRevenue());
        assertBigDecimals(periods.getFinancials().get(1).getCostGoodsSold(), period3.getCostGoodsSold());
        assertBigDecimals(periods.getFinancials().get(1).getGrossProfit(), new BigDecimal("550"));
        assertBigDecimals(periods.getFinancials().get(1).getGrossMargin(), new BigDecimal("57.89"));
        assertBigDecimals(periods.getFinancials().get(1).getOperatingExpenses(), period3.getOperatingExpenses());
        assertBigDecimals(periods.getFinancials().get(1).getOperatingIncome(), new BigDecimal("250"));
        assertBigDecimals(periods.getFinancials().get(1).getOperatingMargin(), new BigDecimal("26.32"));
        assertBigDecimals(periods.getFinancials().get(1).getNetIncome(), period3.getNetIncome());
        assertBigDecimals(periods.getFinancials().get(1).getNetMargin(), new BigDecimal("5.26"));
        assertBigDecimals(periods.getFinancials().get(1).getDividend(), period3.getDividend());

        assertThat(periods.getTtm(), is(notNullValue()));

        assertBigDecimals(periods.getTtm().getRevenue(), new BigDecimal("3900"));
        assertBigDecimals(periods.getTtm().getCostGoodsSold(), new BigDecimal("1400"));
        assertBigDecimals(periods.getTtm().getGrossProfit(), new BigDecimal("2500"));
        assertBigDecimals(periods.getTtm().getGrossMargin(), new BigDecimal("64.1"));

        assertBigDecimals(periods.getTtm().getOperatingExpenses(), new BigDecimal("1000"));
        assertBigDecimals(periods.getTtm().getOperatingIncome(), new BigDecimal("1500"));
        assertBigDecimals(periods.getTtm().getOperatingMargin(), new BigDecimal("38.46"));

        assertBigDecimals(periods.getTtm().getNetIncome(), new BigDecimal("300"));
        assertBigDecimals(periods.getTtm().getNetMargin(), new BigDecimal("7.69"));

        assertBigDecimals(periods.getTtm().getDividend(), new BigDecimal("60"));

        assertBigDecimals(periods.getTtm().getShares(), period1.getShares());
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
        assertBigDecimals(periods.getFinancials().get(0).getRevenue(), period3.getRevenue());
        assertBigDecimals(periods.getFinancials().get(0).getCostGoodsSold(), period3.getCostGoodsSold());
        assertBigDecimals(periods.getFinancials().get(0).getGrossProfit(), new BigDecimal("550"));
        assertBigDecimals(periods.getFinancials().get(0).getGrossMargin(), new BigDecimal("57.89"));
        assertBigDecimals(periods.getFinancials().get(0).getOperatingExpenses(), period3.getOperatingExpenses());
        assertBigDecimals(periods.getFinancials().get(0).getOperatingIncome(), new BigDecimal("250"));
        assertBigDecimals(periods.getFinancials().get(0).getOperatingMargin(), new BigDecimal("26.32"));
        assertBigDecimals(periods.getFinancials().get(0).getNetIncome(), period3.getNetIncome());
        assertBigDecimals(periods.getFinancials().get(0).getNetMargin(), new BigDecimal("5.26"));
        assertBigDecimals(periods.getFinancials().get(0).getDividend(), period3.getDividend());

        assertThat(periods.getFinancials().get(1).getPeriod(), is(period1.getName()));
        assertBigDecimals(periods.getFinancials().get(1).getRevenue(), period1.getRevenue());
        assertBigDecimals(periods.getFinancials().get(1).getCostGoodsSold(), period1.getCostGoodsSold());
        assertBigDecimals(periods.getFinancials().get(1).getGrossProfit(), new BigDecimal("700"));
        assertBigDecimals(periods.getFinancials().get(1).getGrossMargin(), new BigDecimal("70"));
        assertBigDecimals(periods.getFinancials().get(1).getOperatingExpenses(), period1.getOperatingExpenses());
        assertBigDecimals(periods.getFinancials().get(1).getOperatingIncome(), new BigDecimal("500"));
        assertBigDecimals(periods.getFinancials().get(1).getOperatingMargin(), new BigDecimal("50"));
        assertBigDecimals(periods.getFinancials().get(1).getNetIncome(), period1.getNetIncome());
        assertBigDecimals(periods.getFinancials().get(1).getNetMargin(), new BigDecimal("10"));
        assertBigDecimals(periods.getFinancials().get(1).getDividend(), period1.getDividend());

        assertThat(periods.getTtm(), is(notNullValue()));

        assertBigDecimals(periods.getTtm().getRevenue(), new BigDecimal("1950"));
        assertBigDecimals(periods.getTtm().getCostGoodsSold(), new BigDecimal("700"));
        assertBigDecimals(periods.getTtm().getGrossProfit(), new BigDecimal("1250"));
        assertBigDecimals(periods.getTtm().getGrossMargin(), new BigDecimal("64.1"));

        assertBigDecimals(periods.getTtm().getOperatingExpenses(), new BigDecimal("500"));
        assertBigDecimals(periods.getTtm().getOperatingIncome(), new BigDecimal("750"));
        assertBigDecimals(periods.getTtm().getOperatingMargin(), new BigDecimal("38.46"));

        assertBigDecimals(periods.getTtm().getNetIncome(), new BigDecimal("150"));
        assertBigDecimals(periods.getTtm().getNetMargin(), new BigDecimal("7.69"));

        assertBigDecimals(periods.getTtm().getDividend(), new BigDecimal("30"));

        assertBigDecimals(periods.getTtm().getShares(), period3.getShares());
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
        assertBigDecimals(periods.getFinancials().get(0).getRevenue(), period1.getRevenue());
        assertBigDecimals(periods.getFinancials().get(0).getCostGoodsSold(), period1.getCostGoodsSold());
        assertBigDecimals(periods.getFinancials().get(0).getGrossProfit(), new BigDecimal("700"));
        assertBigDecimals(periods.getFinancials().get(0).getGrossMargin(), new BigDecimal("70"));
        assertBigDecimals(periods.getFinancials().get(0).getOperatingExpenses(), period1.getOperatingExpenses());
        assertBigDecimals(periods.getFinancials().get(0).getOperatingIncome(), new BigDecimal("500"));
        assertBigDecimals(periods.getFinancials().get(0).getOperatingMargin(), new BigDecimal("50"));
        assertBigDecimals(periods.getFinancials().get(0).getNetIncome(), period1.getNetIncome());
        assertBigDecimals(periods.getFinancials().get(0).getNetMargin(), new BigDecimal("10"));
        assertBigDecimals(periods.getFinancials().get(0).getDividend(), period1.getDividend());

        assertThat(periods.getFinancials().get(1).getPeriod(), is(period2.getName()));
        assertBigDecimals(periods.getFinancials().get(1).getRevenue(), period2.getRevenue());
        assertBigDecimals(periods.getFinancials().get(1).getCostGoodsSold(), period2.getCostGoodsSold());
        assertBigDecimals(periods.getFinancials().get(1).getGrossProfit(), new BigDecimal("550"));
        assertBigDecimals(periods.getFinancials().get(1).getGrossMargin(), new BigDecimal("57.89"));
        assertBigDecimals(periods.getFinancials().get(1).getOperatingExpenses(), period2.getOperatingExpenses());
        assertBigDecimals(periods.getFinancials().get(1).getOperatingIncome(), new BigDecimal("250"));
        assertBigDecimals(periods.getFinancials().get(1).getOperatingMargin(), new BigDecimal("26.32"));
        assertBigDecimals(periods.getFinancials().get(1).getNetIncome(), period2.getNetIncome());
        assertBigDecimals(periods.getFinancials().get(1).getNetMargin(), new BigDecimal("5.26"));
        assertBigDecimals(periods.getFinancials().get(1).getDividend(), period2.getDividend());

        assertThat(periods.getTtm(), is(notNullValue()));

        assertBigDecimals(periods.getTtm().getRevenue(), new BigDecimal("1000"));
        assertBigDecimals(periods.getTtm().getCostGoodsSold(), new BigDecimal("300"));
        assertBigDecimals(periods.getTtm().getGrossProfit(), new BigDecimal("700"));
        assertBigDecimals(periods.getTtm().getGrossMargin(), new BigDecimal("70"));

        assertBigDecimals(periods.getTtm().getOperatingExpenses(), new BigDecimal("200"));
        assertBigDecimals(periods.getTtm().getOperatingIncome(), new BigDecimal("500"));
        assertBigDecimals(periods.getTtm().getOperatingMargin(), new BigDecimal("50"));

        assertBigDecimals(periods.getTtm().getNetIncome(), new BigDecimal("100"));
        assertBigDecimals(periods.getTtm().getNetMargin(), new BigDecimal("10"));

        assertBigDecimals(periods.getTtm().getDividend(), new BigDecimal("20"));

        assertBigDecimals(periods.getTtm().getShares(), period1.getShares());
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
        assertBigDecimals(periods.getFinancials().get(0).getRevenue(), period2.getRevenue());
        assertBigDecimals(periods.getFinancials().get(0).getCostGoodsSold(), period2.getCostGoodsSold());
        assertBigDecimals(periods.getFinancials().get(0).getGrossProfit(), new BigDecimal("400"));
        assertBigDecimals(periods.getFinancials().get(0).getGrossMargin(), new BigDecimal("80"));
        assertBigDecimals(periods.getFinancials().get(0).getOperatingExpenses(), period2.getOperatingExpenses());
        assertBigDecimals(periods.getFinancials().get(0).getOperatingIncome(), new BigDecimal("300"));
        assertBigDecimals(periods.getFinancials().get(0).getOperatingMargin(), new BigDecimal("60"));
        assertBigDecimals(periods.getFinancials().get(0).getNetIncome(), period2.getNetIncome());
        assertBigDecimals(periods.getFinancials().get(0).getNetMargin(), new BigDecimal("20"));
        assertBigDecimals(periods.getFinancials().get(0).getDividend(), period2.getDividend());

        assertThat(periods.getFinancials().get(1).getPeriod(), is(period1.getName()));
        assertBigDecimals(periods.getFinancials().get(1).getRevenue(), period1.getRevenue());
        assertBigDecimals(periods.getFinancials().get(1).getCostGoodsSold(), period1.getCostGoodsSold());
        assertBigDecimals(periods.getFinancials().get(1).getGrossProfit(), new BigDecimal("700"));
        assertBigDecimals(periods.getFinancials().get(1).getGrossMargin(), new BigDecimal("70"));
        assertBigDecimals(periods.getFinancials().get(1).getOperatingExpenses(), period1.getOperatingExpenses());
        assertBigDecimals(periods.getFinancials().get(1).getOperatingIncome(), new BigDecimal("500"));
        assertBigDecimals(periods.getFinancials().get(1).getOperatingMargin(), new BigDecimal("50"));
        assertBigDecimals(periods.getFinancials().get(1).getNetIncome(), period1.getNetIncome());
        assertBigDecimals(periods.getFinancials().get(1).getNetMargin(), new BigDecimal("10"));
        assertBigDecimals(periods.getFinancials().get(1).getDividend(), period1.getDividend());

        assertThat(periods.getTtm(), is(notNullValue()));

        assertBigDecimals(periods.getTtm().getRevenue(), new BigDecimal("1250"));
        assertBigDecimals(periods.getTtm().getCostGoodsSold(), new BigDecimal("325"));
        assertBigDecimals(periods.getTtm().getGrossProfit(), new BigDecimal("925"));
        assertBigDecimals(periods.getTtm().getGrossMargin(), new BigDecimal("74"));

        assertBigDecimals(periods.getTtm().getOperatingExpenses(), new BigDecimal("250"));
        assertBigDecimals(periods.getTtm().getOperatingIncome(), new BigDecimal("675"));
        assertBigDecimals(periods.getTtm().getOperatingMargin(), new BigDecimal("54"));

        assertBigDecimals(periods.getTtm().getNetIncome(), new BigDecimal("175"));
        assertBigDecimals(periods.getTtm().getNetMargin(), new BigDecimal("14"));

        assertBigDecimals(periods.getTtm().getDividend(), new BigDecimal("25"));

        assertBigDecimals(periods.getTtm().getShares(), period2.getShares());
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

    @Test
    void getCompanyAggregates()
    {
        Company company1 = Generator.generateCompany();
        Period period1 = Generator.generatePeriod(company1, true);
        Period period2 = Generator.generatePeriod(company1, true);
        Period period3 = Generator.generatePeriod(company1, true);
        Period period4 = Generator.generatePeriod(company1, false);
        Company company2 = Generator.generateCompany();
        Period period5 = Generator.generatePeriod(company2, true);
        Period period6 = Generator.generatePeriod(company2, false);
        Company company3 = Generator.generateCompany();
        Period period7 = Generator.generatePeriod(company3, true);
        Period period8 = Generator.generatePeriod(company3, false);
        Period period9 = Generator.generatePeriod(company3, true);
        Company company4 = Generator.generateCompany();
        Period period10 = Generator.generatePeriod(company4, false);

        when(periodDao.list()).thenReturn(List.of(period1, period2, period3, period4, period5, period6, period7, period8, period9, period10));

        Map<String, int[]> map = periodService.getCompanyAggregates();
        assertThat(map.size(), is(3));
        assertThat(map.get(company1.getId()).length, is(1));
        assertThat(map.get(company1.getId())[0], is(3));

        assertThat(map.get(company2.getId()).length, is(1));
        assertThat(map.get(company2.getId())[0], is(1));

        assertThat(map.get(company3.getId()).length, is(1));
        assertThat(map.get(company3.getId())[0], is(2));

        assertThat(map.get(company4.getId()), is(nullValue()));
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
