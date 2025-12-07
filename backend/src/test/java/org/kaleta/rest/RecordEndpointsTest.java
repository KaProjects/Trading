package org.kaleta.rest;

import io.quarkus.test.Mock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.kaleta.Utils;
import org.kaleta.dto.RecordDto;
import org.kaleta.dto.RecordsUiDto;
import org.kaleta.framework.Assert;
import org.kaleta.framework.Generator;
import org.kaleta.persistence.api.RecordDao;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Record;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.rest.dto.RecordCreateDto;
import org.kaleta.rest.dto.RecordUpdateDto;
import org.kaleta.service.FirebaseService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.kaleta.framework.Assert.assertBigDecimals;

@QuarkusTest
class RecordEndpointsTest
{
    @Mock
    @Singleton
    public static class FirebaseServiceMock extends FirebaseService {
        @Override
        public boolean hasCompany(String ticker)
        {
            return false;
        }
    }

    private final String path = "/record";

    @Inject
    RecordDao recordDao;

    @Test
    void create()
    {
        RecordCreateDto dto = new RecordCreateDto();
        dto.setCompanyId("6877c555-1234-4af5-99ef-415980484d8c");
        dto.setPrice(Generator.randomBigDecimal(999999,4).toString());
        dto.setDate("2020-01-01");
        dto.setTitle("a title");
        dto.setPriceToRevenues(Generator.randomBigDecimal(9999,2).toString());
        dto.setPriceToGrossProfit(Generator.randomBigDecimal(9999,2).toString());
        dto.setPriceToOperatingIncome(Generator.randomBigDecimal(9999,2).toString());
        dto.setPriceToNetIncome(Generator.randomBigDecimal(9999,2).toString());
        dto.setDividendYield(Generator.randomBigDecimal(999,2).toString());
        dto.setSumAssetQuantity(Generator.randomBigDecimal(9999,4).toString());
        dto.setAvgAssetPrice(Generator.randomBigDecimal(999999,4).toString());

        Assert.post201(path, dto);

        List<Record> records = recordDao.list("6877c555-1234-4af5-99ef-415980484d8c");
        assertThat(records.size(), is(1));
        assertThat(records.get(0).getCompany().getTicker(), is("CRE"));
        assertThat(records.get(0).getDate(), is(Utils.nullableDateValueOf(dto.getDate())));
        assertThat(records.get(0).getTitle(), is(dto.getTitle()));
        assertThat(records.get(0).getPrice(), is(new BigDecimal(dto.getPrice())));
        assertBigDecimals(records.get(0).getPriceToRevenues(), new BigDecimal(dto.getPriceToRevenues()));
        assertBigDecimals(records.get(0).getPriceToGrossProfit(), new BigDecimal(dto.getPriceToGrossProfit()));
        assertBigDecimals(records.get(0).getPriceToOperatingIncome(), new BigDecimal(dto.getPriceToOperatingIncome()));
        assertBigDecimals(records.get(0).getPriceToNetIncome(), new BigDecimal(dto.getPriceToNetIncome()));
        assertBigDecimals(records.get(0).getDividendYield(), new BigDecimal(dto.getDividendYield()));
        assertBigDecimals(records.get(0).getAvgAssetPrice(), new BigDecimal(dto.getAvgAssetPrice()));
        assertBigDecimals(records.get(0).getSumAssetQuantity(), new BigDecimal(dto.getSumAssetQuantity()));
        assertThat(records.get(0).getContent(), is(nullValue()));
        assertThat(records.get(0).getStrategy(), is(nullValue()));
        assertThat(records.get(0).getTargets(), is(nullValue()));
    }

    @Test
    void createInvalidValues()
    {
        String validCompanyId = "f5b87b39-6b61-4c32-8c09-4f34e97c2d7d";
        String validPrice = Generator.randomBigDecimal(999999,4).toString();
        String validDate = "2020-01-01";
        String validTitle = "a title";
        String validPs = Generator.randomBigDecimal(9999,2).toString();
        String validPg = Generator.randomBigDecimal(9999,2).toString();
        String validPo = Generator.randomBigDecimal(9999,2).toString();
        String validPe = Generator.randomBigDecimal(9999,2).toString();
        String validDy = Generator.randomBigDecimal(999,2).toString();
        String validQ = Generator.randomBigDecimal(9999,4).toString();
        String validPp = Generator.randomBigDecimal(999999,4).toString();

        Assert.postValidationError(path, null, "must not be null");

        RecordCreateDto dto = new RecordCreateDto();
        dto.setCompanyId(validCompanyId);
        dto.setDate(validDate);
        dto.setPrice(validPrice);
        dto.setTitle(validTitle);
        dto.setPriceToRevenues(validPs);
        dto.setPriceToGrossProfit(validPg);
        dto.setPriceToOperatingIncome(validPo);
        dto.setPriceToNetIncome(validPe);
        dto.setDividendYield(validDy);
        dto.setSumAssetQuantity(validQ);
        dto.setAvgAssetPrice(validPp);

        dto.setCompanyId(null);
        Assert.postValidationError(path, dto, "must not be null");
        dto.setCompanyId("x");
        Assert.postValidationError(path, dto, "must be a valid UUID");
        dto.setCompanyId(UUID.randomUUID().toString());
        Assert.post400(path, dto, "company with id '" + dto.getCompanyId() + "' not found");
        dto.setCompanyId(validCompanyId);

        dto.setDate(null);
        Assert.postValidationError(path, dto, "must not be null");
        dto.setDate("");
        Assert.postValidationError(path, dto, "must match YYYY-MM-DD");
        dto.setDate("1.1.2020");
        Assert.postValidationError(path, dto, "must match YYYY-MM-DD");
        dto.setDate(validDate);

        dto.setPrice(null);
        Assert.postValidationError(path, dto, "must not be null");
        dto.setPrice("x");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPrice(".1");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPrice("1.");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPrice("1234567");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPrice("10.12345");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPrice(validPrice);

        dto.setTitle(null);
        Assert.postValidationError(path, dto, "must not be null");
        dto.setTitle(validTitle);

        dto.setPriceToRevenues("x");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToRevenues(".1");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToRevenues("1.");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToRevenues("12345");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToRevenues("10.123");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToRevenues(validPs);

        dto.setPriceToGrossProfit("x");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToGrossProfit(".1");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToGrossProfit("1.");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToGrossProfit("12345");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToGrossProfit("10.123");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToGrossProfit(validPg);

        dto.setPriceToOperatingIncome("x");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToOperatingIncome(".1");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToOperatingIncome("1.");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToOperatingIncome("12345");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToOperatingIncome("10.123");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToOperatingIncome(validPo);

        dto.setPriceToNetIncome("x");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToNetIncome(".1");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToNetIncome("1.");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToNetIncome("12345");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToNetIncome("10.123");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setPriceToNetIncome(validPe);

        dto.setDividendYield("x");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setDividendYield(".1");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setDividendYield("1.");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setDividendYield("1234");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setDividendYield("10.123");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setDividendYield(validDy);

        dto.setSumAssetQuantity("x");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setSumAssetQuantity(".1");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setSumAssetQuantity("1.");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setSumAssetQuantity("12345");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setSumAssetQuantity("10.12345");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setSumAssetQuantity(validQ);

        dto.setAvgAssetPrice("x");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setAvgAssetPrice(".1");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setAvgAssetPrice("1.");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setAvgAssetPrice("1234567");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setAvgAssetPrice("10.12345");
        Assert.postValidationError(path, dto, "must be a valid BigDecimal");
        dto.setAvgAssetPrice(validQ);
    }

    @Test
    void update()
    {
        String newTitle = "new title";
        String newContent = "[{\"type\":\"bulleted-list\",\"children\":[{\"type\":\"list-item\",\"children\":[{\"text\":\"saasdasdaa\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"as\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"das\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"s\"}]}]}]";
        String newStrategy = "buy as many as possible";

        RecordUpdateDto dto = new RecordUpdateDto();
        dto.setId("b5a8a2b3-08b7-4a71-9301-f57d44a0a9cb");
        dto.setTitle(newTitle);
        dto.setContent(newContent);
        dto.setStrategy(newStrategy);

        Assert.put204(path, dto);

        List<Record> records = recordDao.list("9c858901-8a57-4791-81fe-4c455b099bc9");
        assertThat(records.size(), is(1));

        assertThat(records.get(0).getCompany().getTicker(), is("UPD"));
        assertThat(records.get(0).getTitle(), is(newTitle));
        assertThat(records.get(0).getContent(), is(newContent));
        assertThat(records.get(0).getStrategy(), is(newStrategy));
    }

    @Test
    void updateInvalidValues()
    {
        Assert.putValidationError(path, null, "must not be null");

        RecordDto dto =  new RecordDto();
        Assert.putValidationError(path, dto, "must not be null");

        dto.setId("x");
        Assert.putValidationError(path, dto, "must be a valid UUID");

        dto.setId(UUID.randomUUID().toString());
        Assert.put400(path, dto, "record with id '" + dto.getId() + "' not found");
    }

    @Test
    void delete()
    {
        Assert.delete200(path + "/a9f86e1e-b81d-4b28-b4f3-91d25dfb6b43");
    }

    @Test
    void deleteInvalidValues()
    {
        Assert.deleteValidationError(path + "/x","must be a valid UUID");

        String randomId = UUID.randomUUID().toString();
        Assert.delete400(path + "/" + randomId, "record with id '" + randomId + "' not found");
    }


    @Test
    void getRecords()
    {
        RecordsUiDto dto = given().when()
                .get("/record/adb89a0a-86bc-4854-8a55-058ad2e6308f")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordsUiDto.class);

        assertThat(dto.getCompany().getTicker(), is("NVDA"));
        assertThat(dto.getCompany().getCurrency(), is(Currency.$));
        assertThat(dto.getCompany().getWatching(), is(true));
        assertThat(dto.getCompany().getSector().getName(), is(Sector.SEMICONDUCTORS.getName()));
        assertThat(dto.getLatest().getPrice().getValue(), is("500"));
        assertThat(dto.getMarketCap(), is("450.39B"));
        assertThat(dto.getRecords().size(), is(2));
        assertThat(dto.getRecords().get(0).getDate(), is("05.01.2024"));
        assertThat(dto.getRecords().get(1).getDate(), is("11.11.2023"));
    }

    @Test
    void getRecordsInvalidValues()
    {
        Assert.getValidationError("/record/" + "AAAAAA", "must be a valid UUID");

        String randomId = UUID.randomUUID().toString();
        Assert.get400(path + "/" + randomId, "company with id '" + randomId + "' not found");
    }

    @Test
    void testLatestValues()
    {
        RecordsUiDto dto = given().when()
                .get("/record/ededb691-b3c0-4c66-b03d-4e7b46bb2489")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordsUiDto.class);

        assertThat(dto.getCompany().getTicker(), is("XRL"));
        assertThat(dto.getRecords().size(), is(6));
        assertThat(dto.getLatest().getPrice(), is(new RecordsUiDto.Latest("100", "01.11.2022")));
        assertThat(dto.getLatest().getPe(), is(new RecordsUiDto.Latest("5", "01.10.2022")));
        assertThat(dto.getLatest().getPs(), is(new RecordsUiDto.Latest("4", "01.09.2022")));
        assertThat(dto.getLatest().getDy(), is(new RecordsUiDto.Latest("1.5", "01.08.2022")));
        assertThat(dto.getLatest().getTargets(), is(new RecordsUiDto.Latest("10-5~7", "01.07.2022")));
        assertThat(dto.getLatest().getStrategy(), is(new RecordsUiDto.Latest("strat", "01.06.2022")));
    }

    @Test
    void testFinancials()
    {
        RecordsUiDto dto = given().when()
                .get("/record/adb89a0a-86bc-4854-8a55-058ad2e6308f")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getObject("", RecordsUiDto.class);

        assertThat(dto.getCompany().getTicker(), is("NVDA"));
        assertThat(dto.getFinancials().getValues().size(), is(3));
        assertThat(dto.getFinancials().getValues().get(1).getQuarter(), is("23Q2"));
        assertThat(dto.getFinancials().getValues().get(1).getRevenue(), is("13.51B"));
        assertThat(dto.getFinancials().getValues().get(1).getCostGoodsSold(), is("6.01B"));
        assertThat(dto.getFinancials().getValues().get(1).getGrossProfit(), is("7.5B"));
        assertThat(dto.getFinancials().getValues().get(1).getGrossMargin(), is("56"));
        assertThat(dto.getFinancials().getValues().get(1).getOperatingExpenses(), is("1.3B"));
        assertThat(dto.getFinancials().getValues().get(1).getOperatingIncome(), is("6.2B"));
        assertThat(dto.getFinancials().getValues().get(1).getOperatingMargin(), is("46"));
        assertThat(dto.getFinancials().getValues().get(1).getNetIncome(), is("4.59B"));
        assertThat(dto.getFinancials().getValues().get(1).getNetMargin(), is("34"));

        assertThat(dto.getLatest().getPrice(), is(new RecordsUiDto.Latest("500", "05.01.2024")));
        assertThat(dto.getFinancials().getTtm().getRevenue(), is("51.76B"));
        assertThat(dto.getFinancials().getTtm().getCostGoodsSold(), is("51.76B"));
        assertThat(dto.getFinancials().getTtm().getGrossProfit(), is("30.16B"));
        assertThat(dto.getFinancials().getTtm().getGrossMargin(), is("58"));
        assertThat(dto.getFinancials().getTtm().getOperatingExpenses(), is("6.67B"));
        assertThat(dto.getFinancials().getTtm().getOperatingIncome(), is("23.49B"));
        assertThat(dto.getFinancials().getTtm().getOperatingMargin(), is("45"));
        assertThat(dto.getFinancials().getTtm().getNetIncome(), is("18.59B"));
        assertThat(dto.getFinancials().getTtm().getNetMargin(), is("36"));
    }
}