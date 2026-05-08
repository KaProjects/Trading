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
import org.kaleta.rest.dto.PeriodImportDto;
import org.kaleta.rest.dto.PeriodUpdateDto;
import org.kaleta.rest.dto.PeriodUpdateFinancialDto;
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
    void createImport()
    {
        String validName = "25FY";
        String validEndingMonth = "2026-02";
        String validReportDate = "2026-03-30";
        String validShares = "123456.78";
        String validPriceLow = "1234.5678";
        String validPriceHigh = "2345.6789";
        String validRevenue = "123456.78";
        String validGrossProfit = "-23456.78";
        String validOperatingIncome = "-12345.67";
        String validNetIncome = "-3456.78";
        String validDividend = "12.34";

        createAndAssertImportedPeriod(validName, validEndingMonth, validReportDate,
                validShares, validPriceLow, validPriceHigh, validRevenue,
                validGrossProfit, validOperatingIncome, validNetIncome, validDividend, null);
        createAndAssertImportedPeriod(validName, validEndingMonth, null,
                null, null, null, null,
                null, null, null, null, null);
        createAndAssertImportedPeriod(validName, validEndingMonth, "",
                "", "", "", "",
                "", "", "", "", IllegalArgumentException.class);

        invalidDates().forEach(invalidDate -> createAndAssertImportedPeriod(validName, validEndingMonth, invalidDate,
                validShares, validPriceLow, validPriceHigh, validRevenue,
                validGrossProfit, validOperatingIncome, validNetIncome, validDividend, IllegalArgumentException.class));

        createAndAssertImportedPeriod(validName, null, validReportDate,
                validShares, validPriceLow, validPriceHigh, validRevenue,
                validGrossProfit, validOperatingIncome, validNetIncome, validDividend, NullPointerException.class);
        createAndAssertImportedPeriod(validName, "", validReportDate,
                validShares, validPriceLow, validPriceHigh, validRevenue,
                validGrossProfit, validOperatingIncome, validNetIncome, validDividend, DateTimeParseException.class);
        createAndAssertImportedPeriod(validName, "aaaa", validReportDate,
                validShares, validPriceLow, validPriceHigh, validRevenue,
                validGrossProfit, validOperatingIncome, validNetIncome, validDividend, DateTimeParseException.class);
        createAndAssertImportedPeriod(validName, "202510", validReportDate,
                validShares, validPriceLow, validPriceHigh, validRevenue,
                validGrossProfit, validOperatingIncome, validNetIncome, validDividend, DateTimeParseException.class);
        createAndAssertImportedPeriod(validName, "2510", validReportDate,
                validShares, validPriceLow, validPriceHigh, validRevenue,
                validGrossProfit, validOperatingIncome, validNetIncome, validDividend, DateTimeParseException.class);
        createAndAssertImportedPeriod(validName, "2025-10-10", validReportDate,
                validShares, validPriceLow, validPriceHigh, validRevenue,
                validGrossProfit, validOperatingIncome, validNetIncome, validDividend, DateTimeParseException.class);

        createAndAssertImportedPeriod(null, validEndingMonth, validReportDate,
                validShares, validPriceLow, validPriceHigh, validRevenue,
                validGrossProfit, validOperatingIncome, validNetIncome, validDividend, IllegalArgumentException.class);
        createAndAssertImportedPeriod("", validEndingMonth, validReportDate,
                validShares, validPriceLow, validPriceHigh, validRevenue,
                validGrossProfit, validOperatingIncome, validNetIncome, validDividend, IllegalArgumentException.class);
        createAndAssertImportedPeriod("2025FY", validEndingMonth, validReportDate,
                validShares, validPriceLow, validPriceHigh, validRevenue,
                validGrossProfit, validOperatingIncome, validNetIncome, validDividend, IllegalArgumentException.class);
        createAndAssertImportedPeriod("a5FY", validEndingMonth, validReportDate,
                validShares, validPriceLow, validPriceHigh, validRevenue,
                validGrossProfit, validOperatingIncome, validNetIncome, validDividend, DateTimeParseException.class);
        createAndAssertImportedPeriod("25FX", validEndingMonth, validReportDate,
                validShares, validPriceLow, validPriceHigh, validRevenue,
                validGrossProfit, validOperatingIncome, validNetIncome, validDividend, IllegalArgumentException.class);

        invalidBigDecimals().forEach(invalidBigDecimal -> createAndAssertImportedPeriod(validName, validEndingMonth, validReportDate,
                invalidBigDecimal, validPriceLow, validPriceHigh, validRevenue,
                validGrossProfit, validOperatingIncome, validNetIncome, validDividend, NumberFormatException.class));
        invalidBigDecimals().forEach(invalidBigDecimal -> createAndAssertImportedPeriod(validName, validEndingMonth, validReportDate,
                validShares, invalidBigDecimal, validPriceHigh, validRevenue,
                validGrossProfit, validOperatingIncome, validNetIncome, validDividend, NumberFormatException.class));
        invalidBigDecimals().forEach(invalidBigDecimal -> createAndAssertImportedPeriod(validName, validEndingMonth, validReportDate,
                validShares, validPriceLow, invalidBigDecimal, validRevenue,
                validGrossProfit, validOperatingIncome, validNetIncome, validDividend, NumberFormatException.class));
        invalidBigDecimals().forEach(invalidBigDecimal -> createAndAssertImportedPeriod(validName, validEndingMonth, validReportDate,
                validShares, validPriceLow, validPriceHigh, invalidBigDecimal,
                validGrossProfit, validOperatingIncome, validNetIncome, validDividend, NumberFormatException.class));
        invalidBigDecimals().forEach(invalidBigDecimal -> createAndAssertImportedPeriod(validName, validEndingMonth, validReportDate,
                validShares, validPriceLow, validPriceHigh, validRevenue,
                invalidBigDecimal, validOperatingIncome, validNetIncome, validDividend, NumberFormatException.class));
        invalidBigDecimals().forEach(invalidBigDecimal -> createAndAssertImportedPeriod(validName, validEndingMonth, validReportDate,
                validShares, validPriceLow, validPriceHigh, validRevenue,
                validGrossProfit, invalidBigDecimal, validNetIncome, validDividend, NumberFormatException.class));
        invalidBigDecimals().forEach(invalidBigDecimal -> createAndAssertImportedPeriod(validName, validEndingMonth, validReportDate,
                validShares, validPriceLow, validPriceHigh, validRevenue,
                validGrossProfit, validOperatingIncome, invalidBigDecimal, validDividend, NumberFormatException.class));
        invalidBigDecimals().forEach(invalidBigDecimal -> createAndAssertImportedPeriod(validName, validEndingMonth, validReportDate,
                validShares, validPriceLow, validPriceHigh, validRevenue,
                validGrossProfit, validOperatingIncome, validNetIncome, invalidBigDecimal, NumberFormatException.class));
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
            dto.setGrossProfit(invalidBigDecimal);
            updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        });
        dto.setGrossProfit(String.valueOf(Generator.randomBigDecimal(999999, 2)));
        updateAndAssertPeriod(dto, period, null);

        invalidBigDecimals().forEach(invalidBigDecimal -> {
            dto.setOperatingIncome(invalidBigDecimal);
            updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        });
        dto.setOperatingIncome(String.valueOf(Generator.randomBigDecimal(999999, 2)));
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
    void updateFinancial()
    {
        Company company = Generator.generateCompany();
        Period period = Generator.generatePeriod(company, true);

        when(periodDao.get(period.getId())).thenReturn(period);
        when(periodDao.get(null)).thenThrow(NoResultException.class);

        PeriodUpdateFinancialDto dto = new PeriodUpdateFinancialDto();

        updateAndAssertPeriod(dto, period, ServiceFailureException.class);

        dto.setId(period.getId());
        dto.setReportDate(Generator.randomDate(2025));
        dto.setShares(String.valueOf(Generator.randomBigDecimal(999999, 2)));
        dto.setPriceLow(String.valueOf(Generator.randomBigDecimal(999999, 4)));
        dto.setPriceHigh(String.valueOf(Generator.randomBigDecimal(999999, 4)));
        dto.setRevenue(String.valueOf(Generator.randomBigDecimal(999999, 2)));
        dto.setGrossProfit(String.valueOf(Generator.randomBigDecimal(999999, 2)));
        dto.setOperatingIncome(String.valueOf(Generator.randomBigDecimal(999999, 2)));
        dto.setNetIncome(String.valueOf(Generator.randomBigDecimal(999999, 2)));
        dto.setDividend(String.valueOf(Generator.randomBigDecimal(999999, 2)));
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

        invalidBigDecimals().forEach(invalidBigDecimal -> {
            dto.setRevenue(invalidBigDecimal);
            updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        });
        dto.setRevenue(String.valueOf(Generator.randomBigDecimal(999999, 2)));
        updateAndAssertPeriod(dto, period, null);

        invalidBigDecimals().forEach(invalidBigDecimal -> {
            dto.setGrossProfit(invalidBigDecimal);
            updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        });
        dto.setGrossProfit(String.valueOf(Generator.randomBigDecimal(999999, 2)));
        updateAndAssertPeriod(dto, period, null);

        invalidBigDecimals().forEach(invalidBigDecimal -> {
            dto.setOperatingIncome(invalidBigDecimal);
            updateAndAssertPeriod(dto, period, IllegalArgumentException.class);
        });
        dto.setOperatingIncome(String.valueOf(Generator.randomBigDecimal(999999, 2)));
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
        assertPeriod(periods.getPeriods().get(0), period2, period1.getReportDate());
        assertPeriod(periods.getPeriods().get(1), period1, period3.getReportDate());
        assertPeriod(periods.getPeriods().get(2), period3, null);

        assertThat(periods.getFinancials().size(), is(2));

        assertThat(periods.getFinancials().get(0).getPeriod(), is(period1.getName()));
        assertBigDecimals(periods.getFinancials().get(0).getRevenue(), period1.getRevenue());
        assertBigDecimals(periods.getFinancials().get(0).getGrossProfit(), period1.getGrossProfit());
        assertBigDecimals(periods.getFinancials().get(0).getGrossMargin(), new BigDecimal("30"));
        assertBigDecimals(periods.getFinancials().get(0).getOperatingIncome(), period1.getOperatingIncome());
        assertBigDecimals(periods.getFinancials().get(0).getOperatingMargin(), new BigDecimal("20"));
        assertBigDecimals(periods.getFinancials().get(0).getNetIncome(), period1.getNetIncome());
        assertBigDecimals(periods.getFinancials().get(0).getNetMargin(), new BigDecimal("10"));
        assertBigDecimals(periods.getFinancials().get(0).getDividend(), period1.getDividend());

        assertThat(periods.getFinancials().get(1).getPeriod(), is(period3.getName()));
        assertBigDecimals(periods.getFinancials().get(1).getRevenue(), period3.getRevenue());
        assertBigDecimals(periods.getFinancials().get(1).getGrossProfit(), period3.getGrossProfit());
        assertBigDecimals(periods.getFinancials().get(1).getGrossMargin(), new BigDecimal("42.11"));
        assertBigDecimals(periods.getFinancials().get(1).getOperatingIncome(), period3.getOperatingIncome());
        assertBigDecimals(periods.getFinancials().get(1).getOperatingMargin(), new BigDecimal("31.58"));
        assertBigDecimals(periods.getFinancials().get(1).getNetIncome(), period3.getNetIncome());
        assertBigDecimals(periods.getFinancials().get(1).getNetMargin(), new BigDecimal("5.26"));
        assertBigDecimals(periods.getFinancials().get(1).getDividend(), period3.getDividend());

        assertThat(periods.getTtm(), is(notNullValue()));

        assertBigDecimals(periods.getTtm().getRevenue(), new BigDecimal("3900"));
        assertBigDecimals(periods.getTtm().getGrossProfit(), new BigDecimal("1400"));
        assertBigDecimals(periods.getTtm().getGrossMargin(), new BigDecimal("35.90"));

        assertBigDecimals(periods.getTtm().getOperatingIncome(), new BigDecimal("1000"));
        assertBigDecimals(periods.getTtm().getOperatingMargin(), new BigDecimal("25.64"));

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
        assertPeriod(periods.getPeriods().get(0), period2, period3.getReportDate());
        assertPeriod(periods.getPeriods().get(1), period3, period1.getReportDate());
        assertPeriod(periods.getPeriods().get(2), period1, null);

        assertThat(periods.getFinancials().size(), is(2));

        assertThat(periods.getFinancials().get(0).getPeriod(), is(period3.getName()));
        assertBigDecimals(periods.getFinancials().get(0).getRevenue(), period3.getRevenue());
        assertBigDecimals(periods.getFinancials().get(0).getGrossProfit(), period3.getGrossProfit());
        assertBigDecimals(periods.getFinancials().get(0).getGrossMargin(), new BigDecimal("42.11"));
        assertBigDecimals(periods.getFinancials().get(0).getOperatingIncome(), period3.getOperatingIncome());
        assertBigDecimals(periods.getFinancials().get(0).getOperatingMargin(), new BigDecimal("31.58"));
        assertBigDecimals(periods.getFinancials().get(0).getNetIncome(), period3.getNetIncome());
        assertBigDecimals(periods.getFinancials().get(0).getNetMargin(), new BigDecimal("5.26"));
        assertBigDecimals(periods.getFinancials().get(0).getDividend(), period3.getDividend());

        assertThat(periods.getFinancials().get(1).getPeriod(), is(period1.getName()));
        assertBigDecimals(periods.getFinancials().get(1).getRevenue(), period1.getRevenue());
        assertBigDecimals(periods.getFinancials().get(1).getGrossProfit(), period1.getGrossProfit());
        assertBigDecimals(periods.getFinancials().get(1).getGrossMargin(), new BigDecimal("30"));
        assertBigDecimals(periods.getFinancials().get(1).getOperatingIncome(), period1.getOperatingIncome());
        assertBigDecimals(periods.getFinancials().get(1).getOperatingMargin(), new BigDecimal("20"));
        assertBigDecimals(periods.getFinancials().get(1).getNetIncome(), period1.getNetIncome());
        assertBigDecimals(periods.getFinancials().get(1).getNetMargin(), new BigDecimal("10"));
        assertBigDecimals(periods.getFinancials().get(1).getDividend(), period1.getDividend());

        assertThat(periods.getTtm(), is(notNullValue()));

        assertBigDecimals(periods.getTtm().getRevenue(), new BigDecimal("1950"));
        assertBigDecimals(periods.getTtm().getGrossProfit(), new BigDecimal("700"));
        assertBigDecimals(periods.getTtm().getGrossMargin(), new BigDecimal("35.90"));

        assertBigDecimals(periods.getTtm().getOperatingIncome(), new BigDecimal("500"));
        assertBigDecimals(periods.getTtm().getOperatingMargin(), new BigDecimal("25.64"));

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
        assertPeriod(periods.getPeriods().get(0), period3, period1.getReportDate());
        assertPeriod(periods.getPeriods().get(1), period1, period2.getReportDate());
        assertPeriod(periods.getPeriods().get(2), period2, null);

        assertThat(periods.getFinancials().size(), is(2));

        assertThat(periods.getFinancials().get(0).getPeriod(), is(period1.getName()));
        assertBigDecimals(periods.getFinancials().get(0).getRevenue(), period1.getRevenue());
        assertBigDecimals(periods.getFinancials().get(0).getGrossProfit(), period1.getGrossProfit());
        assertBigDecimals(periods.getFinancials().get(0).getGrossMargin(), new BigDecimal("30"));
        assertBigDecimals(periods.getFinancials().get(0).getOperatingIncome(), period1.getOperatingIncome());
        assertBigDecimals(periods.getFinancials().get(0).getOperatingMargin(), new BigDecimal("20"));
        assertBigDecimals(periods.getFinancials().get(0).getNetIncome(), period1.getNetIncome());
        assertBigDecimals(periods.getFinancials().get(0).getNetMargin(), new BigDecimal("10"));
        assertBigDecimals(periods.getFinancials().get(0).getDividend(), period1.getDividend());

        assertThat(periods.getFinancials().get(1).getPeriod(), is(period2.getName()));
        assertBigDecimals(periods.getFinancials().get(1).getRevenue(), period2.getRevenue());
        assertBigDecimals(periods.getFinancials().get(1).getGrossProfit(), period2.getGrossProfit());
        assertBigDecimals(periods.getFinancials().get(1).getGrossMargin(), new BigDecimal("42.11"));
        assertBigDecimals(periods.getFinancials().get(1).getOperatingIncome(), period2.getOperatingIncome());
        assertBigDecimals(periods.getFinancials().get(1).getOperatingMargin(), new BigDecimal("31.58"));
        assertBigDecimals(periods.getFinancials().get(1).getNetIncome(), period2.getNetIncome());
        assertBigDecimals(periods.getFinancials().get(1).getNetMargin(), new BigDecimal("5.26"));
        assertBigDecimals(periods.getFinancials().get(1).getDividend(), period2.getDividend());

        assertThat(periods.getTtm(), is(notNullValue()));

        assertBigDecimals(periods.getTtm().getRevenue(), new BigDecimal("1000"));
        assertBigDecimals(periods.getTtm().getGrossProfit(), new BigDecimal("300"));
        assertBigDecimals(periods.getTtm().getGrossMargin(), new BigDecimal("30"));

        assertBigDecimals(periods.getTtm().getOperatingIncome(), new BigDecimal("200"));
        assertBigDecimals(periods.getTtm().getOperatingMargin(), new BigDecimal("20"));

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
        assertPeriod(periods.getPeriods().get(0), period3, period2.getReportDate());
        assertPeriod(periods.getPeriods().get(1), period2, period1.getReportDate());
        assertPeriod(periods.getPeriods().get(2), period1, null);

        assertThat(periods.getFinancials().size(), is(2));

        assertThat(periods.getFinancials().get(0).getPeriod(), is(period2.getName()));
        assertBigDecimals(periods.getFinancials().get(0).getRevenue(), period2.getRevenue());
        assertBigDecimals(periods.getFinancials().get(0).getGrossProfit(), period2.getGrossProfit());
        assertBigDecimals(periods.getFinancials().get(0).getGrossMargin(), new BigDecimal("20"));
        assertBigDecimals(periods.getFinancials().get(0).getOperatingIncome(), period2.getOperatingIncome());
        assertBigDecimals(periods.getFinancials().get(0).getOperatingMargin(), new BigDecimal("20"));
        assertBigDecimals(periods.getFinancials().get(0).getNetIncome(), period2.getNetIncome());
        assertBigDecimals(periods.getFinancials().get(0).getNetMargin(), new BigDecimal("20"));
        assertBigDecimals(periods.getFinancials().get(0).getDividend(), period2.getDividend());

        assertThat(periods.getFinancials().get(1).getPeriod(), is(period1.getName()));
        assertBigDecimals(periods.getFinancials().get(1).getRevenue(), period1.getRevenue());
        assertBigDecimals(periods.getFinancials().get(1).getGrossProfit(), period1.getGrossProfit());
        assertBigDecimals(periods.getFinancials().get(1).getGrossMargin(), new BigDecimal("30"));
        assertBigDecimals(periods.getFinancials().get(1).getOperatingIncome(), period1.getOperatingIncome());
        assertBigDecimals(periods.getFinancials().get(1).getOperatingMargin(), new BigDecimal("20"));
        assertBigDecimals(periods.getFinancials().get(1).getNetIncome(), period1.getNetIncome());
        assertBigDecimals(periods.getFinancials().get(1).getNetMargin(), new BigDecimal("10"));
        assertBigDecimals(periods.getFinancials().get(1).getDividend(), period1.getDividend());

        assertThat(periods.getTtm(), is(notNullValue()));

        assertBigDecimals(periods.getTtm().getRevenue(), new BigDecimal("1250"));
        assertBigDecimals(periods.getTtm().getGrossProfit(), new BigDecimal("325"));
        assertBigDecimals(periods.getTtm().getGrossMargin(), new BigDecimal("26.00"));

        assertBigDecimals(periods.getTtm().getOperatingIncome(), new BigDecimal("250"));
        assertBigDecimals(periods.getTtm().getOperatingMargin(), new BigDecimal("20.00"));

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
        assertPeriod(periods.getPeriods().get(0), period3, period2.getReportDate());
        assertPeriod(periods.getPeriods().get(1), period2, period1.getReportDate());
        assertPeriod(periods.getPeriods().get(2), period1, null);

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

    private void assertPeriod(Periods.Period actual, Period expected, Date expectedPreviousReportDate)
    {
        assertThat(actual.getId(), is(expected.getId()));
        assertThat(actual.getName(), is(expected.getName()));
        assertThat(actual.getShares(), is(expected.getShares()));
        assertThat(actual.getResearch(), is(expected.getResearch()));
        assertThat(actual.getPriceHigh(), is(expected.getPriceHigh()));
        assertThat(actual.getPriceLow(), is(expected.getPriceLow()));
        assertThat(actual.getEndingMonth(), is(expected.getEndingMonth()));
        assertThat(actual.getReportDate(), is(expected.getReportDate()));
        assertThat(actual.getPreviousReportDate(), is(expectedPreviousReportDate));
        if (expected.getRevenue() != null) {
            assertThat(actual.getFinancial(), is(notNullValue()));
            assertThat(actual.getFinancial().getPeriod(), is(expected.getName()));
            assertThat(actual.getFinancial().getRevenue(), is(expected.getRevenue()));
            assertThat(actual.getFinancial().getGrossProfit(), is(notNullValue()));
            assertThat(actual.getFinancial().getGrossMargin(), is(notNullValue()));
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
        when(companyService.findEntity(company.getId())).thenReturn(company);

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

    private void createAndAssertImportedPeriod(String name, String endingMonth, String reportDate,
                                              String shares, String priceLow, String priceHigh, String revenue,
                                              String grossProfit, String operatingIncome, String netIncome, String dividend,
                                              Class<? extends Exception> expectedException)
    {
        Company company = Generator.generateCompany();
        when(companyService.findEntity(company.getId())).thenReturn(company);

        PeriodImportDto createDto = new PeriodImportDto();
        createDto.setCompanyId(company.getId());
        createDto.setName(name);
        createDto.setEndingMonth(endingMonth);
        createDto.setReportDate(reportDate);
        createDto.setShares(shares);
        createDto.setPriceLow(priceLow);
        createDto.setPriceHigh(priceHigh);
        createDto.setRevenue(revenue);
        createDto.setGrossProfit(grossProfit);
        createDto.setOperatingIncome(operatingIncome);
        createDto.setNetIncome(netIncome);
        createDto.setDividend(dividend);

        if (expectedException == null) {
            periodService.create(createDto);

            ArgumentCaptor<Period> captor = ArgumentCaptor.forClass(Period.class);
            verify(periodDao).create(captor.capture());

            assertThat(captor.getValue().getCompany().getId(), is(company.getId()));
            assertThat(captor.getValue().getName(), is(PeriodName.valueOf(name)));
            assertThat(captor.getValue().getEndingMonth(), is(YearMonth.parse(endingMonth)));
            assertThat(captor.getValue().getReportDate(), is(Utils.nullableDateValueOf(reportDate)));
            assertBigDecimals(captor.getValue().getShares(), Utils.createNullableBigDecimal(shares));
            assertBigDecimals(captor.getValue().getPriceLow(), Utils.createNullableBigDecimal(priceLow));
            assertBigDecimals(captor.getValue().getPriceHigh(), Utils.createNullableBigDecimal(priceHigh));
            assertBigDecimals(captor.getValue().getRevenue(), Utils.createNullableBigDecimal(revenue));
            assertBigDecimals(captor.getValue().getGrossProfit(), Utils.createNullableBigDecimal(grossProfit));
            assertBigDecimals(captor.getValue().getOperatingIncome(), Utils.createNullableBigDecimal(operatingIncome));
            assertBigDecimals(captor.getValue().getNetIncome(), Utils.createNullableBigDecimal(netIncome));
            assertBigDecimals(captor.getValue().getDividend(), Utils.createNullableBigDecimal(dividend));

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
            assertThat(captor.getValue().getGrossProfit(),
                    (dto.getGrossProfit() == null) ? is(period.getGrossProfit()) : is(new BigDecimal(dto.getGrossProfit())));
            assertThat(captor.getValue().getOperatingIncome(),
                    (dto.getOperatingIncome() == null) ? is(period.getOperatingIncome()) : is(new BigDecimal(dto.getOperatingIncome())));
            assertThat(captor.getValue().getNetIncome(),
                    (dto.getNetIncome() == null) ? is(period.getNetIncome()) : is(new BigDecimal(dto.getNetIncome())));
            assertThat(captor.getValue().getDividend(),
                    (dto.getDividend() == null) ? is(period.getDividend()) : is(new BigDecimal(dto.getDividend())));

            clearInvocations(periodDao);
        } else {
            assertThrows(expectedException, () -> periodService.update(dto));
        }
    }

    private void updateAndAssertPeriod(PeriodUpdateFinancialDto dto, Period period, Class<? extends Exception> expectedException) {
        if (expectedException == null) {
            periodService.update(dto);

            ArgumentCaptor<Period> captor = ArgumentCaptor.forClass(Period.class);
            verify(periodDao).save(captor.capture());

            assertThat(captor.getValue().getCompany().getId(), is(period.getCompany().getId()));
            assertThat(captor.getValue().getName(), is(period.getName()));
            assertThat(captor.getValue().getEndingMonth(), is(period.getEndingMonth()));
            assertThat(captor.getValue().getResearch(), is(period.getResearch()));
            assertThat(captor.getValue().getReportDate(), is(Date.valueOf(dto.getReportDate())));
            assertThat(captor.getValue().getShares(), is(new BigDecimal(dto.getShares())));
            assertThat(captor.getValue().getPriceHigh(), is(new BigDecimal(dto.getPriceHigh())));
            assertThat(captor.getValue().getPriceLow(), is(new BigDecimal(dto.getPriceLow())));
            assertThat(captor.getValue().getRevenue(), is(new BigDecimal(dto.getRevenue())));
            assertThat(captor.getValue().getGrossProfit(), is(new BigDecimal(dto.getGrossProfit())));
            assertThat(captor.getValue().getOperatingIncome(), is(new BigDecimal(dto.getOperatingIncome())));
            assertThat(captor.getValue().getNetIncome(), is(new BigDecimal(dto.getNetIncome())));
            assertThat(captor.getValue().getDividend(), is(new BigDecimal(dto.getDividend())));

            clearInvocations(periodDao);
        } else {
            assertThrows(expectedException, () -> periodService.update(dto));
        }
    }
}
