package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.kaleta.framework.Assert;
import org.kaleta.persistence.api.PeriodDao;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.rest.dto.PeriodCreateDto;
import org.kaleta.rest.dto.PeriodImportDto;
import org.kaleta.rest.dto.PeriodUnreportedImportDto;
import org.kaleta.rest.dto.PeriodUpdateDto;
import org.kaleta.rest.dto.PeriodUpdateFinancialDto;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.YearMonth;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_4_2_true;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_6_2_false;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_6_2_true;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_6_4_false;
import static org.kaleta.framework.Assert.ExpectedViolation.MATCH_DATE_FORMAT;
import static org.kaleta.framework.Assert.ExpectedViolation.NOT_NULL;
import static org.kaleta.framework.Assert.ExpectedViolation.VALID_ID;
import static org.kaleta.framework.Assert.assertBigDecimals;

@QuarkusTest
class PeriodEndpointsTest
{
    String path = "/period";

    @Inject
    PeriodDao periodDao;

    @Test
    void create()
    {
        PeriodCreateDto dto = new PeriodCreateDto();
        dto.setCompanyId(1565L);
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
        assertThat(period.getAdjustedEps(), is(nullValue()));
    }

    @Test
    void create_invalidParameters()
    {
        Long validCompanyId = 2287L;
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
        dto.setCompanyId(0L);
        Assert.postValidationError(path, dto, VALID_ID);

        dto.setCompanyId(4_294_967_295L);
        Assert.post400(path, dto, "company with id '" + dto.getCompanyId() + "' not found");
    }

    @Test
    void createImport()
    {
        PeriodImportDto dto = new PeriodImportDto();
        dto.setCompanyId(1564L);
        dto.setName("15FY");
        dto.setEndingMonth("2015-10");
        dto.setReportDate("2015-11-11");
        dto.setShares("12345.67");
        dto.setRevenue("22.5");
        dto.setNetIncome("-5");

        Assert.post201(path + "/import", dto);

        List<Period> periods = periodDao.list(dto.getCompanyId());
        assertThat(periods.size(), is(1));
        Period period = periods.get(0);
        assertThat(period.getCompany().getTicker(), is("IMP"));
        assertThat(period.getName(), is(PeriodName.valueOf(dto.getName())));
        assertThat(period.getEndingMonth(), is(YearMonth.parse(dto.getEndingMonth())));
        assertThat(period.getReportDate(), is(Date.valueOf(dto.getReportDate())));
        assertBigDecimals(period.getShares(), new BigDecimal(dto.getShares()));
        assertThat(period.getPriceHigh(), is(nullValue()));
        assertThat(period.getPriceLow(), is(nullValue()));
        assertThat(period.getResearch(), is(nullValue()));
        assertBigDecimals(period.getRevenue(), new BigDecimal(dto.getRevenue()));
        assertThat(period.getGrossProfit(), is(nullValue()));
        assertThat(period.getOperatingIncome(), is(nullValue()));
        assertBigDecimals(period.getNetIncome(), new BigDecimal(dto.getNetIncome()));
        assertThat(period.getDividend(), is(nullValue()));
        assertThat(period.getAdjustedEps(), is(nullValue()));
    }

    @Test
    void createUnreportedImport()
    {
        PeriodUnreportedImportDto dto = new PeriodUnreportedImportDto();
        dto.setCompanyId(2287L);
        dto.setName("25Q1");
        dto.setEndingMonth("2025-04");

        Assert.post201(path + "/import/unreported", dto);

        List<Period> periods = periodDao.list(dto.getCompanyId());
        assertThat(periods.size(), is(1));
        Period period = periods.get(0);
        assertThat(period.getCompany().getTicker(), is("CINV"));
        assertThat(period.getName(), is(PeriodName.valueOf(dto.getName())));
        assertThat(period.getEndingMonth(), is(YearMonth.parse(dto.getEndingMonth())));
        assertThat(period.getReportDate(), is(nullValue()));
        assertThat(period.getShares(), is(nullValue()));
        assertThat(period.getPriceHigh(), is(nullValue()));
        assertThat(period.getPriceLow(), is(nullValue()));
        assertThat(period.getRevenue(), is(nullValue()));
        assertThat(period.getGrossProfit(), is(nullValue()));
        assertThat(period.getOperatingIncome(), is(nullValue()));
        assertThat(period.getNetIncome(), is(nullValue()));
        assertThat(period.getDividend(), is(nullValue()));
        assertThat(period.getAdjustedEps(), is(nullValue()));
    }

    @Test
    void createUnreportedImport_invalidParameters()
    {
        String endpoint = path + "/import/unreported";
        Long validCompanyId = 2287L;
        String validName = "25Q1";
        String validEndingMonth = "2025-04";

        Assert.postValidationError(endpoint, null, NOT_NULL);

        PeriodUnreportedImportDto dto = new PeriodUnreportedImportDto();
        dto.setCompanyId(validCompanyId);
        dto.setName(validName);
        dto.setEndingMonth(validEndingMonth);

        dto.setCompanyId(null);
        Assert.postValidationError(endpoint, dto, NOT_NULL);
        dto.setCompanyId(0L);
        Assert.postValidationError(endpoint, dto, VALID_ID);
        dto.setCompanyId(4_294_967_295L);
        Assert.post400(endpoint, dto, "company with id '" + dto.getCompanyId() + "' not found");
        dto.setCompanyId(validCompanyId);

        dto.setName(null);
        Assert.postValidationError(endpoint, dto, NOT_NULL);
        dto.setName("");
        Assert.postValidationError(endpoint, dto, "must be a valid PeriodName");
        dto.setName("2025FY");
        Assert.postValidationError(endpoint, dto, "must be a valid PeriodName");
        dto.setName("25FX");
        Assert.postValidationError(endpoint, dto, "must be a valid PeriodName");
        dto.setName(validName);

        dto.setEndingMonth(null);
        Assert.postValidationError(endpoint, dto, NOT_NULL);
        dto.setEndingMonth("");
        Assert.postValidationError(endpoint, dto, "must match YYYY-MM");
        dto.setEndingMonth("202504");
        Assert.postValidationError(endpoint, dto, "must match YYYY-MM");
        dto.setEndingMonth("2025-04-01");
        Assert.postValidationError(endpoint, dto, "must match YYYY-MM");
    }

    @Test
    void createImport_invalidParameters()
    {
        Long validCompanyId = 2287L;
        String validName = "19FY";
        String validEndingMonth = "2019-11";
        String validReportDate = "2020-01-01";
        String validShares = "12345.67";
        String validPriceLow = "20.1234";
        String validPriceHigh = "26.5678";
        String validRevenue = "22.5";
        String validGrossProfit = "-5";
        String validOperatingIncome = "-10";
        String validNetIncome = "-5";
        String validDividend = "2";
        String validAdjustedEps = "-1.25";

        Assert.postValidationError(path + "/import", null, NOT_NULL);

        PeriodImportDto dto = new PeriodImportDto();
        dto.setCompanyId(validCompanyId);
        dto.setName(validName);
        dto.setEndingMonth(validEndingMonth);
        dto.setReportDate(validReportDate);
        dto.setShares(validShares);
        dto.setPriceLow(validPriceLow);
        dto.setPriceHigh(validPriceHigh);
        dto.setRevenue(validRevenue);
        dto.setGrossProfit(validGrossProfit);
        dto.setOperatingIncome(validOperatingIncome);
        dto.setNetIncome(validNetIncome);
        dto.setDividend(validDividend);
        dto.setAdjustedEps(validAdjustedEps);

        dto.setCompanyId(null);
        Assert.postValidationError(path + "/import", dto, NOT_NULL);
        dto.setCompanyId(0L);
        Assert.postValidationError(path + "/import", dto, VALID_ID);
        dto.setCompanyId(4_294_967_295L);
        Assert.post400(path + "/import", dto, "company with id '" + dto.getCompanyId() + "' not found");
        dto.setCompanyId(validCompanyId);

        dto.setName(null);
        Assert.postValidationError(path + "/import", dto, NOT_NULL);
        dto.setName("");
        Assert.postValidationError(path + "/import", dto, "must be a valid PeriodName");
        dto.setName("2025FY");
        Assert.postValidationError(path + "/import", dto, "must be a valid PeriodName");
        dto.setName("a5FY");
        Assert.postValidationError(path + "/import", dto, "must be a valid PeriodName");
        dto.setName("25FX");
        Assert.postValidationError(path + "/import", dto, "must be a valid PeriodName");
        dto.setName(validName);

        dto.setEndingMonth(null);
        Assert.postValidationError(path + "/import", dto, NOT_NULL);
        dto.setEndingMonth("");
        Assert.postValidationError(path + "/import", dto, "must match YYYY-MM");
        dto.setEndingMonth("xyz6");
        Assert.postValidationError(path + "/import", dto, "must match YYYY-MM");
        dto.setEndingMonth("202510");
        Assert.postValidationError(path + "/import", dto, "must match YYYY-MM");
        dto.setEndingMonth("2025-10-06");
        Assert.postValidationError(path + "/import", dto, "must match YYYY-MM");
        dto.setEndingMonth(validEndingMonth);

        dto.setReportDate("");
        Assert.postValidationError(path + "/import", dto, MATCH_DATE_FORMAT);
        dto.setReportDate("1.1.2020");
        Assert.postValidationError(path + "/import", dto, MATCH_DATE_FORMAT);
        dto.setReportDate(null);
        Assert.postValidationError(path + "/import", dto, NOT_NULL);
        dto.setReportDate(validReportDate);

        dto.setShares("");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setShares("x");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setShares(".1");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setShares("1.");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setShares("1234567");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setShares("10.123");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setShares("-1");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setShares(null);
        Assert.postValidationError(path + "/import", dto, NOT_NULL);
        dto.setShares(validShares);

        dto.setRevenue("");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue("x");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue(".1");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue("1.");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue("1234567");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue("10.123");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue("-1");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setRevenue(null);
        Assert.postValidationError(path + "/import", dto, NOT_NULL);
        dto.setRevenue(validRevenue);

        dto.setGrossProfit("");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_true);
        dto.setGrossProfit("x");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_true);
        dto.setGrossProfit(".1");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_true);
        dto.setGrossProfit("1.");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_true);
        dto.setGrossProfit("1234567");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_true);
        dto.setGrossProfit("10.123");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_true);
        dto.setGrossProfit(null);
        dto.setGrossProfit(validGrossProfit);

        dto.setOperatingIncome("");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_true);
        dto.setOperatingIncome("x");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_true);
        dto.setOperatingIncome(".1");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_true);
        dto.setOperatingIncome("1.");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_true);
        dto.setOperatingIncome("1234567");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_true);
        dto.setOperatingIncome("10.123");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_true);
        dto.setOperatingIncome(null);
        dto.setOperatingIncome(validOperatingIncome);

        dto.setNetIncome("");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_true);
        dto.setNetIncome("x");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_true);
        dto.setNetIncome(".1");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_true);
        dto.setNetIncome("1.");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_true);
        dto.setNetIncome("1234567");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_true);
        dto.setNetIncome("10.123");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_true);
        dto.setNetIncome(null);
        Assert.postValidationError(path + "/import", dto, NOT_NULL);
        dto.setNetIncome(validNetIncome);

        dto.setDividend("");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setDividend("x");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setDividend(".1");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setDividend("1.");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setDividend("1234567");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setDividend("10.123");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setDividend("-1");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_2_false);
        dto.setDividend(null);
        dto.setDividend(validDividend);

        dto.setAdjustedEps("");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_4_2_true);
        dto.setAdjustedEps("x");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_4_2_true);
        dto.setAdjustedEps(".1");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_4_2_true);
        dto.setAdjustedEps("1.");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_4_2_true);
        dto.setAdjustedEps("12345");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_4_2_true);
        dto.setAdjustedEps("10.123");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_4_2_true);
        dto.setAdjustedEps(null);
        dto.setAdjustedEps(validAdjustedEps);

        dto.setPriceLow("");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow("x");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow(".1");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow("1.");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow("1234567");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow("10.12345");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow("-1");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceLow(null);
        dto.setPriceLow(validPriceLow);

        dto.setPriceHigh("");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh("x");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh(".1");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh("1.");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh("1234567");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh("10.12345");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh("-1");
        Assert.postValidationError(path + "/import", dto, BIG_DECIMAL_6_4_false);
        dto.setPriceHigh(null);
    }

    @Test
    void update()
    {
        Long id = 1467L;

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
        dto.setAdjustedEps("-1.25");

        Assert.put204(path, dto);

        List<Period> periods = periodDao.list(1842L);
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
        assertBigDecimals(period.getAdjustedEps(), new BigDecimal(dto.getAdjustedEps()));
    }

    @Test
    void update_invalidParameters()
    {
        Assert.putValidationError(path, null, NOT_NULL);

        PeriodUpdateDto dto =  new PeriodUpdateDto();
        Assert.putValidationError(path, dto, NOT_NULL);

        dto.setId(0L);
        Assert.putValidationError(path, dto, VALID_ID);

        dto.setId(4_294_967_295L);
        Assert.put400(path, dto, "period with id '" + dto.getId() + "' not found");

        dto.setId(2042L);
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

        dto.setAdjustedEps("x");
        Assert.putValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setAdjustedEps(".1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setAdjustedEps("1.");
        Assert.putValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setAdjustedEps("12345");
        Assert.putValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setAdjustedEps("10.123");
        Assert.putValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setAdjustedEps(null);

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
        Long id = 1466L;

        PeriodUpdateFinancialDto dto = new PeriodUpdateFinancialDto();
        dto.setId(id);
        dto.setReportDate("2020-12-15");
        dto.setShares("12345");
        dto.setRevenue("22.5");
        dto.setNetIncome("5");

        Assert.put204(path + "/financial", dto);

        List<Period> periods = periodDao.list(1842L);
        assertThat(periods.size(), is(2));
        Period period = periods.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
        assertThat(period, is(notNullValue()));
        assertThat(period.getName(), is(PeriodName.valueOf("21Q2")));
        assertThat(period.getEndingMonth(), is(YearMonth.of(2025, 3)));
        assertThat(period.getReportDate(), is(Date.valueOf(dto.getReportDate())));
        assertBigDecimals(period.getShares(), new BigDecimal(dto.getShares()));
        assertThat(period.getPriceHigh(), is(nullValue()));
        assertThat(period.getPriceLow(), is(nullValue()));
        assertBigDecimals(period.getRevenue(), new BigDecimal(dto.getRevenue()));
        assertThat(period.getGrossProfit(), is(nullValue()));
        assertThat(period.getOperatingIncome(), is(nullValue()));
        assertBigDecimals(period.getNetIncome(), new BigDecimal(dto.getNetIncome()));
        assertThat(period.getDividend(), is(nullValue()));
        assertThat(period.getAdjustedEps(), is(nullValue()));
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
        dto.setAdjustedEps("1.75");

        Assert.putValidationError(path + "/financial", dto, NOT_NULL);

        dto.setId(0L);
        Assert.putValidationError(path + "/financial", dto, VALID_ID);

        dto.setId(4_294_967_295L);
        Assert.put400(path + "/financial", dto, "period with id '" + dto.getId() + "' not found");

        dto.setId(2042L);

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

        dto.setAdjustedEps("x");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_4_2_true);
        dto.setAdjustedEps(".1");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_4_2_true);
        dto.setAdjustedEps("1.");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_4_2_true);
        dto.setAdjustedEps("12345");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_4_2_true);
        dto.setAdjustedEps("10.123");
        Assert.putValidationError(path + "/financial", dto, BIG_DECIMAL_4_2_true);
        dto.setAdjustedEps("1");

        dto.setPriceLow(null);
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
