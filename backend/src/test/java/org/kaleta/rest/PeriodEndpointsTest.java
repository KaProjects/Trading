package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.kaleta.framework.Assert;
import org.kaleta.persistence.api.PeriodDao;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.rest.dto.PeriodCreateDto;
import org.kaleta.rest.dto.PeriodUpdateDto;
import org.kaleta.rest.dto.PeriodUpdateFinancialDto;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_6_2_false;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_6_2_true;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_6_4_false;
import static org.kaleta.framework.Assert.ExpectedViolation.MATCH_DATE_FORMAT;
import static org.kaleta.framework.Assert.ExpectedViolation.NOT_NULL;
import static org.kaleta.framework.Assert.ExpectedViolation.VALID_UUID;
import static org.kaleta.framework.Assert.assertBigDecimals;

@QuarkusTest
class PeriodEndpointsTest
{
    private final String path = "/period";

    @Inject
    PeriodDao periodDao;

    @Test
    void create()
    {
        PeriodCreateDto dto = new PeriodCreateDto();
        dto.setCompanyId("6877c555-1234-4af5-99ef-415980484d8c");
        dto.setName("15FY");
        dto.setEndingMonth("2015-10");
        dto.setReportDate("2015-11-11");

        Assert.post201(path, dto);

        List<Period> periods = periodDao.list(dto.getCompanyId());
        assertThat(periods.size(), is(1));
        Period period = periods.get(0);
        assertThat(period.getCompany().getTicker(), is("CRE"));
        assertThat(period.getName(), is(PeriodName.valueOf(dto.getName())));
        assertThat(period.getEndingMonth(), is(YearMonth.parse(dto.getEndingMonth())));
        assertThat(period.getReportDate(), is(Date.valueOf(dto.getReportDate())));
        assertThat(period.getShares(), is(nullValue()));
        assertThat(period.getPriceHigh(), is(nullValue()));
        assertThat(period.getPriceLow(), is(nullValue()));
        assertThat(period.getResearch(), is(nullValue()));
        assertThat(period.getRevenue(), is(nullValue()));
        assertThat(period.getGrossProfit(), is(nullValue()));
        assertThat(period.getOperatingIncome(), is(nullValue()));
        assertThat(period.getNetIncome(), is(nullValue()));
        assertThat(period.getDividend(), is(nullValue()));
    }

    @Test
    void create_invalidParameters()
    {
        String validCompanyId = "f5b87b39-6b61-4c32-8c09-4f34e97c2d7d";
        String validName = "19FY";
        String validEndingMonth = "2019-11";
        String validReportDate = "2020-01-01";

        Assert.postValidationError(path, null, NOT_NULL);

        PeriodCreateDto dto = new PeriodCreateDto();
        dto.setCompanyId(validCompanyId);
        dto.setEndingMonth(validEndingMonth);
        dto.setReportDate(validReportDate);

        dto.setName(null);
        Assert.postValidationError(path, dto, NOT_NULL);
        dto.setName("");
        Assert.postValidationError(path, dto, "must be a valid PeriodName");
        dto.setName("2025FY");
        Assert.postValidationError(path, dto, "must be a valid PeriodName");
        dto.setName("a5FY");
        Assert.postValidationError(path, dto, "must be a valid PeriodName");
        dto.setName("25FX");
        Assert.postValidationError(path, dto, "must be a valid PeriodName");
        dto.setName(validName);

        dto.setEndingMonth(null);
        Assert.postValidationError(path, dto, NOT_NULL);
        dto.setEndingMonth("");
        Assert.postValidationError(path, dto, "must match YYYY-MM");
        dto.setEndingMonth("xyz6");
        Assert.postValidationError(path, dto, "must match YYYY-MM");
        dto.setEndingMonth("202510");
        Assert.postValidationError(path, dto, "must match YYYY-MM");
        dto.setEndingMonth("2025-10-06");
        Assert.postValidationError(path, dto, "must match YYYY-MM");
        dto.setEndingMonth(validEndingMonth);

        dto.setReportDate("");
        Assert.postValidationError(path, dto, MATCH_DATE_FORMAT);
        dto.setReportDate("1.1.2020");
        Assert.postValidationError(path, dto, MATCH_DATE_FORMAT);
        dto.setReportDate(null);

        dto.setCompanyId(null);
        Assert.postValidationError(path, dto, NOT_NULL);
        dto.setCompanyId("x");
        Assert.postValidationError(path, dto, VALID_UUID);

        dto.setCompanyId(UUID.randomUUID().toString());
        Assert.post400(path, dto, "company with id '" + dto.getCompanyId() + "' not found");
    }

    @Test
    void update()
    {
        String id = "550e8400-e29b-41d4-a716-446655440000";

        PeriodUpdateDto dto = new PeriodUpdateDto();
        dto.setId(id);
        dto.setName("20H2");
        dto.setEndingMonth("2011-10");
        dto.setReportDate("2020-12-15");
        dto.setShares("12345");
        dto.setPriceLow("20.5");
        dto.setPriceHigh("26.5");
        dto.setResearch("[{\"type\":\"bulleted-list\",\"children\":[{\"type\":\"list-item\",\"children\":[{\"text\":\"saasdasdaa\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"as\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"das\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"s\"}]}]}]");
        dto.setRevenue("22.5");
        dto.setGrossProfit("5");
        dto.setOperatingIncome("10");
        dto.setNetIncome("5");
        dto.setDividend("2");

        Assert.put204(path, dto);

        List<Period> periods = periodDao.list("9c858901-8a57-4791-81fe-4c455b099bc9");
        assertThat(periods.size(), is(2));
        Period period = periods.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
        assertThat(period, is(notNullValue()));
        assertThat(period.getName(), is(PeriodName.valueOf(dto.getName())));
        assertThat(period.getEndingMonth(), is(YearMonth.parse(dto.getEndingMonth())));
        assertThat(period.getReportDate(), is(Date.valueOf(dto.getReportDate())));
        assertBigDecimals(period.getShares(), new BigDecimal(dto.getShares()));
        assertBigDecimals(period.getPriceHigh(), new BigDecimal(dto.getPriceHigh()));
        assertBigDecimals(period.getPriceLow(), new BigDecimal(dto.getPriceLow()));
        assertThat(period.getResearch(), is(dto.getResearch()));
        assertBigDecimals(period.getRevenue(), new BigDecimal(dto.getRevenue()));
        assertBigDecimals(period.getGrossProfit(), new BigDecimal(dto.getGrossProfit()));
        assertBigDecimals(period.getOperatingIncome(), new BigDecimal(dto.getOperatingIncome()));
        assertBigDecimals(period.getNetIncome(), new BigDecimal(dto.getNetIncome()));
        assertBigDecimals(period.getDividend(), new BigDecimal(dto.getDividend()));
    }

    @Test
    void update_invalidParameters()
    {
        Assert.putValidationError(path, null, NOT_NULL);

        PeriodUpdateDto dto =  new PeriodUpdateDto();
        Assert.putValidationError(path, dto, NOT_NULL);

        dto.setId("x");
        Assert.putValidationError(path, dto, VALID_UUID);

        dto.setId(UUID.randomUUID().toString());
        Assert.put400(path, dto, "period with id '" + dto.getId() + "' not found");

        dto.setId("c40b6e24-0e9d-496d-9135-6cf4a9d1e8ce");
        dto.setName("");
        Assert.putValidationError(path, dto, "must be a valid PeriodName");
        dto.setName("2025FY");
        Assert.putValidationError(path, dto, "must be a valid PeriodName");
        dto.setName("a5FY");
        Assert.putValidationError(path, dto, "must be a valid PeriodName");
        dto.setName("25FX");
        Assert.putValidationError(path, dto, "must be a valid PeriodName");
        dto.setName(null);

        dto.setEndingMonth("");
        Assert.putValidationError(path, dto, "must match YYYY-MM");
        dto.setEndingMonth("xyz6");
        Assert.putValidationError(path, dto, "must match YYYY-MM");
        dto.setEndingMonth("202510");
        Assert.putValidationError(path, dto, "must match YYYY-MM");
        dto.setEndingMonth("2025-10-06");
        Assert.putValidationError(path, dto, "must match YYYY-MM");
        dto.setEndingMonth(null);

        dto.setReportDate("");
        Assert.putValidationError(path, dto, MATCH_DATE_FORMAT);
        dto.setReportDate("1.1.2020");
        Assert.putValidationError(path, dto, MATCH_DATE_FORMAT);
        dto.setReportDate(null);

        dto.setShares("x");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_false);
        dto.setShares(".1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_false);
        dto.setShares("1.");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_false);
        dto.setShares("1234567");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_false);
        dto.setShares("10.123");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_false);
        dto.setShares("-1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_false);
        dto.setShares(null);

        dto.setRevenue("x");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue(".1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue("1.");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue("1234567");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue("10.123");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue("-1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue(null);

        dto.setGrossProfit("x");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_true);
        dto.setGrossProfit(".1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_true);
        dto.setGrossProfit("1.");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_true);
        dto.setGrossProfit("1234567");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_true);
        dto.setGrossProfit("10.123");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_true);
        dto.setGrossProfit(null);

        dto.setOperatingIncome("x");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_true);
        dto.setOperatingIncome(".1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_true);
        dto.setOperatingIncome("1.");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_true);
        dto.setOperatingIncome("1234567");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_true);
        dto.setOperatingIncome("10.123");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_true);
        dto.setOperatingIncome(null);

        dto.setNetIncome("x");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_true);
        dto.setNetIncome(".1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_true);
        dto.setNetIncome("1.");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_true);
        dto.setNetIncome("1234567");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_true);
        dto.setNetIncome("10.123");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_true);
        dto.setNetIncome(null);

        dto.setDividend("x");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_false);
        dto.setDividend(".1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_false);
        dto.setDividend("1.");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_false);
        dto.setDividend("1234567");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_false);
        dto.setDividend("10.123");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_false);
        dto.setDividend("-1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_2_false);
        dto.setDividend(null);

        dto.setPriceLow("x");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow(".1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow("1.");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow("1234567");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow("10.12345");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow("-1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow(null);

        dto.setPriceHigh("x");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh(".1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh("1.");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh("1234567");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh("10.12345");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh("-1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh(null);
    }

    @Test
    void updateFinancial()
    {
        String id = "550e8400-e29b-41d4-a716-441111440000";

        PeriodUpdateFinancialDto dto = new PeriodUpdateFinancialDto();
        dto.setId(id);
        dto.setReportDate("2020-12-15");
        dto.setShares("12345");
        dto.setPriceLow("20.5");
        dto.setPriceHigh("26.5");
        dto.setRevenue("22.5");
        dto.setGrossProfit("5");
        dto.setOperatingIncome("10");
        dto.setNetIncome("5");
        dto.setDividend("2");

        Assert.put204(path + "/financial", dto);

        List<Period> periods = periodDao.list("9c858901-8a57-4791-81fe-4c455b099bc9");
        assertThat(periods.size(), is(2));
        Period period = periods.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
        assertThat(period, is(notNullValue()));
        assertThat(period.getName(), is(PeriodName.valueOf("21Q2")));
        assertThat(period.getEndingMonth(), is(YearMonth.of(2025, 3)));
        assertThat(period.getReportDate(), is(Date.valueOf(dto.getReportDate())));
        assertBigDecimals(period.getShares(), new BigDecimal(dto.getShares()));
        assertBigDecimals(period.getPriceHigh(), new BigDecimal(dto.getPriceHigh()));
        assertBigDecimals(period.getPriceLow(), new BigDecimal(dto.getPriceLow()));
        assertBigDecimals(period.getRevenue(), new BigDecimal(dto.getRevenue()));
        assertBigDecimals(period.getGrossProfit(), new BigDecimal(dto.getGrossProfit()));
        assertBigDecimals(period.getOperatingIncome(), new BigDecimal(dto.getOperatingIncome()));
        assertBigDecimals(period.getNetIncome(), new BigDecimal(dto.getNetIncome()));
        assertBigDecimals(period.getDividend(), new BigDecimal(dto.getDividend()));
    }

    @Test
    void updateFinancial_invalidParameters()
    {
        Assert.putValidationError(path + "/financial", null, NOT_NULL);

        PeriodUpdateFinancialDto dto =  new PeriodUpdateFinancialDto();
        dto.setReportDate("2020-12-15");
        dto.setShares("12345");
        dto.setPriceLow("20.5");
        dto.setPriceHigh("26.5");
        dto.setRevenue("22.5");
        dto.setGrossProfit("5");
        dto.setOperatingIncome("10");
        dto.setNetIncome("5");
        dto.setDividend("2");

        Assert.putValidationError(path + "/financial", dto, NOT_NULL);

        dto.setId("x");
        Assert.putValidationError(path + "/financial", dto, VALID_UUID);

        dto.setId(UUID.randomUUID().toString());
        Assert.put400(path + "/financial", dto, "period with id '" + dto.getId() + "' not found");

        dto.setId("c40b6e24-0e9d-496d-9135-6cf4a9d1e8ce");

        dto.setReportDate(null);
        Assert.putValidationError(path + "/financial", dto, NOT_NULL);
        dto.setReportDate("");
        Assert.putValidationError(path + "/financial", dto, MATCH_DATE_FORMAT);
        dto.setReportDate("1.1.2020");
        Assert.putValidationError(path + "/financial", dto, MATCH_DATE_FORMAT);
        dto.setReportDate("2020-12-15");

        dto.setShares(null);
        Assert.putValidationError(path + "/financial", dto, NOT_NULL);
        dto.setShares("x");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_false);
        dto.setShares(".1");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_false);
        dto.setShares("1.");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_false);
        dto.setShares("1234567");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_false);
        dto.setShares("10.123");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_false);
        dto.setShares("-1");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_false);
        dto.setShares("12345");

        dto.setRevenue(null);
        Assert.putValidationError(path + "/financial", dto, NOT_NULL);
        dto.setRevenue("x");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue(".1");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue("1.");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue("1234567");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue("10.123");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue("-1");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue("22.5");

        dto.setGrossProfit(null);
        Assert.putValidationError(path + "/financial", dto, NOT_NULL);
        dto.setGrossProfit("x");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_true);
        dto.setGrossProfit(".1");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_true);
        dto.setGrossProfit("1.");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_true);
        dto.setGrossProfit("1234567");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_true);
        dto.setGrossProfit("10.123");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_true);
        dto.setGrossProfit("5");

        dto.setOperatingIncome(null);
        Assert.putValidationError(path + "/financial", dto, NOT_NULL);
        dto.setOperatingIncome("x");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_true);
        dto.setOperatingIncome(".1");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_true);
        dto.setOperatingIncome("1.");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_true);
        dto.setOperatingIncome("1234567");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_true);
        dto.setOperatingIncome("10.123");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_true);
        dto.setOperatingIncome("10");

        dto.setNetIncome(null);
        Assert.putValidationError(path + "/financial", dto, NOT_NULL);
        dto.setNetIncome("x");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_true);
        dto.setNetIncome(".1");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_true);
        dto.setNetIncome("1.");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_true);
        dto.setNetIncome("1234567");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_true);
        dto.setNetIncome("10.123");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_true);
        dto.setNetIncome("5");

        dto.setDividend(null);
        Assert.putValidationError(path + "/financial", dto, NOT_NULL);
        dto.setDividend("x");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_false);
        dto.setDividend(".1");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_false);
        dto.setDividend("1.");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_false);
        dto.setDividend("1234567");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_false);
        dto.setDividend("10.123");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_false);
        dto.setDividend("-1");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_2_false);
        dto.setDividend("2");

        dto.setPriceLow(null);
        Assert.putValidationError(path + "/financial", dto, NOT_NULL);
        dto.setPriceLow("x");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow(".1");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow("1.");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow("1234567");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow("10.12345");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow("-1");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow("20.5");

        dto.setPriceHigh(null);
        Assert.putValidationError(path + "/financial", dto, NOT_NULL);
        dto.setPriceHigh("x");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh(".1");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh("1.");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh("1234567");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh("10.12345");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh("-1");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh("26.5");
    }
}