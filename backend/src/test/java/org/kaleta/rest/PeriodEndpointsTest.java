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

import java.math.BigDecimal;
import java.sql.Date;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class PeriodEndpointsTest
{
    private final String path = "/period";

    @Inject
    PeriodDao periodDao;

    @Test
    void create()
    {
        String companyId = "6877c555-1234-4af5-99ef-415980484d8c";
        PeriodCreateDto dto = new PeriodCreateDto();
        dto.setCompanyId(companyId);
        dto.setName("15FY");
        dto.setEndingMonth("2015-10");
        dto.setReportDate("2015-11-11");

        Assert.post201(path, dto);

        List<Period> periods = periodDao.list(companyId);
        assertThat(periods.size(), is(1));
        Period period = periods.get(0);
        assertThat(period.getName(), is(PeriodName.valueOf(dto.getName())));
        assertThat(period.getEndingMonth(), is(YearMonth.parse(dto.getEndingMonth())));
        assertThat(period.getReportDate(), is(Date.valueOf(dto.getReportDate())));
        assertThat(period.getShares(), is(nullValue()));
        assertThat(period.getPriceHigh(), is(nullValue()));
        assertThat(period.getPriceLow(), is(nullValue()));
        assertThat(period.getResearch(), is(nullValue()));
        assertThat(period.getRevenue(), is(nullValue()));
        assertThat(period.getCostGoodsSold(), is(nullValue()));
        assertThat(period.getOperatingExpenses(), is(nullValue()));
        assertThat(period.getNetIncome(), is(nullValue()));
        assertThat(period.getDividend(), is(nullValue()));
    }

    @Test
    void createInvalidValues()
    {
        String validCompanyId = "d98c9ea1-ef2a-400a-bc7f-00d90e5d8e10";
        String validName = "19FY";
        String validEndingMonth = "2019-11";
        String validReportDate = "2020-01-01";

        Assert.postValidationError(path, null, "must not be null");

        PeriodCreateDto dto = new PeriodCreateDto();
        dto.setCompanyId(validCompanyId);
        dto.setEndingMonth(validEndingMonth);
        dto.setReportDate(validReportDate);

        dto.setName(null);
        Assert.postValidationError(path, dto, "must not be null");
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
        Assert.postValidationError(path, dto, "must not be null");
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
        Assert.postValidationError(path, dto, "must match YYYY-MM-DD");
        dto.setReportDate("1.1.2020");
        Assert.postValidationError(path, dto, "must match YYYY-MM-DD");
        dto.setReportDate(null);

        dto.setCompanyId(null);
        Assert.postValidationError(path, dto, "must not be null");
        dto.setCompanyId("x");
        Assert.postValidationError(path, dto, "must be a valid UUID");

        dto.setCompanyId(UUID.randomUUID().toString());
        Assert.post400(path, dto, "company with id '" + dto.getCompanyId() + "' not found");
    }

    @Test
    void update()
    {
        String newName = "20H2";
        String newEndingMonth = "2011-10";
        String newReportDate = "2020-12-15";
        String newShares = "12345";
        String newPriceLow = "20.5";
        String newPriceHigh = "26.5";
        String newResearch = "[{\"type\":\"bulleted-list\",\"children\":[{\"type\":\"list-item\",\"children\":[{\"text\":\"saasdasdaa\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"as\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"das\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"s\"}]}]}]";
        String newRevenue = "22.5";
        String newCostGoodsSold = "5";
        String newOperatingExpenses = "10";
        String newNetIncome = "5";
        String newDividends = "2";

        PeriodUpdateDto dto = new PeriodUpdateDto();
        dto.setId("550e8400-e29b-41d4-a716-446655440000");
        dto.setName(newName);
        dto.setEndingMonth(newEndingMonth);
        dto.setReportDate(newReportDate);
        dto.setShares(newShares);
        dto.setPriceLow(newPriceLow);
        dto.setPriceHigh(newPriceHigh);
        dto.setResearch(newResearch);
        dto.setRevenue(newRevenue);
        dto.setCostGoodsSold(newCostGoodsSold);
        dto.setOperatingExpenses(newOperatingExpenses);
        dto.setNetIncome(newNetIncome);
        dto.setDividend(newDividends);

        Assert.put204(path, dto);

        List<Period> periods = periodDao.list("9c858901-8a57-4791-81fe-4c455b099bc9");
        assertThat(periods.size(), is(1));
        Period period = periods.get(0);
        assertThat(period.getName(), is(PeriodName.valueOf(dto.getName())));
        assertThat(period.getEndingMonth(), is(YearMonth.parse(dto.getEndingMonth())));
        assertThat(period.getReportDate(), is(Date.valueOf(dto.getReportDate())));
        assertThat(period.getShares(), comparesEqualTo(new BigDecimal(dto.getShares())));
        assertThat(period.getPriceHigh(), comparesEqualTo(new BigDecimal(dto.getPriceHigh())));
        assertThat(period.getPriceLow(), comparesEqualTo(new BigDecimal(dto.getPriceLow())));
        assertThat(period.getResearch(), is(dto.getResearch()));
        assertThat(period.getRevenue(), comparesEqualTo(new BigDecimal(dto.getRevenue())));
        assertThat(period.getCostGoodsSold(), comparesEqualTo(new BigDecimal(dto.getCostGoodsSold())));
        assertThat(period.getOperatingExpenses(), comparesEqualTo(new BigDecimal(dto.getOperatingExpenses())));
        assertThat(period.getNetIncome(), comparesEqualTo(new BigDecimal(dto.getNetIncome())));
        assertThat(period.getDividend(), comparesEqualTo(new BigDecimal(dto.getDividend())));
    }

    @Test
    void updateInvalidValues()
    {
        Assert.putValidationError(path, null, "must not be null");

        PeriodUpdateDto dto =  new PeriodUpdateDto();
        Assert.putValidationError(path, dto, "must not be null");

        dto.setId("x");
        Assert.putValidationError(path, dto, "must be a valid UUID");

        dto.setId(UUID.randomUUID().toString());
        Assert.put400(path, dto, "period with id '" + dto.getId() + "' not found");

        dto.setId("2ccbf4fe-dbe7-4c40-a2a2-49bf79f15dad");
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
        Assert.putValidationError(path, dto, "must match YYYY-MM-DD");
        dto.setReportDate("1.1.2020");
        Assert.putValidationError(path, dto, "must match YYYY-MM-DD");
        dto.setReportDate(null);

        dto.setShares("x");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setShares(".1");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setShares("1.");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setShares("1234567");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setShares("10.123");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setShares(null);

        dto.setRevenue("x");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setRevenue(".1");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setRevenue("1.");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setRevenue("1234567");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setRevenue("10.123");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setRevenue(null);

        dto.setCostGoodsSold("x");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setCostGoodsSold(".1");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setCostGoodsSold("1.");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setCostGoodsSold("1234567");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setCostGoodsSold("10.123");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setCostGoodsSold(null);

        dto.setOperatingExpenses("x");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setOperatingExpenses(".1");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setOperatingExpenses("1.");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setOperatingExpenses("1234567");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setOperatingExpenses("10.123");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setOperatingExpenses(null);

        dto.setNetIncome("x");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setNetIncome(".1");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setNetIncome("1.");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setNetIncome("1234567");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setNetIncome("10.123");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setNetIncome(null);

        dto.setDividend("x");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setDividend(".1");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setDividend("1.");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setDividend("1234567");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setDividend("10.123");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setDividend(null);

        dto.setPriceLow("x");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceLow(".1");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceLow("1.");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceLow("1234567");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceLow("10.12345");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceLow(null);

        dto.setPriceHigh("x");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceHigh(".1");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceHigh("1.");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceHigh("1234567");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceHigh("10.12345");
        Assert.putValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceHigh(null);
    }
}