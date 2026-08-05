package org.kaleta.rest;

import io.quarkus.test.Mock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.kaleta.Utils;
import org.kaleta.framework.Assert;
import org.kaleta.framework.Generator;
import org.kaleta.persistence.api.RecordDao;
import org.kaleta.persistence.entity.Record;
import org.kaleta.rest.dto.RecordCreateDto;
import org.kaleta.rest.dto.RecordUpdateDto;
import org.kaleta.service.FirebaseService;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_3_2_false;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_4_2_false;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_4_2_true;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_4_4_false;
import static org.kaleta.framework.Assert.ExpectedViolation.BIG_DECIMAL_6_4_false;
import static org.kaleta.framework.Assert.ExpectedViolation.MATCH_DATE_FORMAT;
import static org.kaleta.framework.Assert.ExpectedViolation.NOT_NULL;
import static org.kaleta.framework.Assert.ExpectedViolation.VALID_ID;
import static org.kaleta.framework.Assert.assertBigDecimals;

@QuarkusTest
class RecordEndpointsTest
{
    String path = "/record";

    @Inject
    RecordDao recordDao;
    @Inject
    EntityManager entityManager;

    @Test
    void create()
    {
        RecordCreateDto dto = new RecordCreateDto();
        dto.setCompanyId(1565L);
        dto.setPrice(Generator.randomBigDecimal(999999,4).toString());
        dto.setDate("2020-01-01");
        dto.setPriceToRevenues(Generator.randomBigDecimal(9999,2).toString());
        dto.setPriceToGrossProfit(Generator.randomBigDecimal(9999,2).toString());
        dto.setPriceToOperatingIncome(Generator.randomBigDecimal(9999,2).toString());
        dto.setPriceToNetIncome(Generator.randomBigDecimal(9999,2).toString());
        dto.setDividendYield(Generator.randomBigDecimal(999,2).toString());
        dto.setSumAssetQuantity(Generator.randomBigDecimal(9999,4).toString());
        dto.setAvgAssetPrice(Generator.randomBigDecimal(999999,4).toString());
        dto.setTargets("some targets");

        Assert.post201(path, dto);

        List<Record> records = recordDao.list(1565L);
        assertThat(records.size(), is(1));
        assertThat(records.get(0).getCompany().getTicker(), is("CRE"));
        assertThat(records.get(0).getDate(), is(Utils.nullableDateValueOf(dto.getDate())));
        assertThat(records.get(0).getTitle(), is(nullValue()));
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
        assertThat(records.get(0).getTargets(), is(dto.getTargets()));
    }

    @Test
    void create_invalidParameters()
    {
        Long validCompanyId = 2287L;
        String validPrice = Generator.randomBigDecimal(999999,4).toString();
        String validDate = "2020-01-01";
        String validPs = Generator.randomBigDecimal(9999,2).toString();
        String validPg = Generator.randomBigDecimal(9999,2).toString();
        String validPo = Generator.randomBigDecimal(9999,2).toString();
        String validPe = Generator.randomBigDecimal(9999,2).toString();
        String validDy = Generator.randomBigDecimal(999,2).toString();
        String validQ = Generator.randomBigDecimal(9999,4).toString();
        String validPp = Generator.randomBigDecimal(999999,4).toString();

        Assert.postValidationError(path, null, NOT_NULL);

        RecordCreateDto dto = new RecordCreateDto();
        dto.setCompanyId(validCompanyId);
        dto.setDate(validDate);
        dto.setPrice(validPrice);
        dto.setPriceToRevenues(validPs);
        dto.setPriceToGrossProfit(validPg);
        dto.setPriceToOperatingIncome(validPo);
        dto.setPriceToNetIncome(validPe);
        dto.setDividendYield(validDy);
        dto.setSumAssetQuantity(validQ);
        dto.setAvgAssetPrice(validPp);

        dto.setCompanyId(null);
        Assert.postValidationError(path, dto, NOT_NULL);
        dto.setCompanyId(0L);
        Assert.postValidationError(path, dto, VALID_ID);
        dto.setCompanyId(4_294_967_295L);
        Assert.post400(path, dto, "company with id '" + dto.getCompanyId() + "' not found");
        dto.setCompanyId(validCompanyId);

        dto.setDate(null);
        Assert.postValidationError(path, dto, NOT_NULL);
        dto.setDate("");
        Assert.postValidationError(path, dto, MATCH_DATE_FORMAT);
        dto.setDate("1.1.2020");
        Assert.postValidationError(path, dto, MATCH_DATE_FORMAT);
        dto.setDate(validDate);

        dto.setPrice(null);
        Assert.postValidationError(path, dto, NOT_NULL);
        dto.setPrice("x");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice(".1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice("1.");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice("1234567");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice("10.12345");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice("-1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setPrice(validPrice);

        dto.setPriceToRevenues("x");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_false);
        dto.setPriceToRevenues(".1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_false);
        dto.setPriceToRevenues("1.");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_false);
        dto.setPriceToRevenues("12345");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_false);
        dto.setPriceToRevenues("10.123");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_false);
        dto.setPriceToRevenues("-1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_false);
        dto.setPriceToRevenues(validPs);

        dto.setPriceToGrossProfit("x");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setPriceToGrossProfit(".1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setPriceToGrossProfit("1.");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setPriceToGrossProfit("12345");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setPriceToGrossProfit("10.123");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setPriceToGrossProfit(validPg);

        dto.setPriceToOperatingIncome("x");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setPriceToOperatingIncome(".1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setPriceToOperatingIncome("1.");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setPriceToOperatingIncome("12345");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setPriceToOperatingIncome("10.123");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setPriceToOperatingIncome(validPo);

        dto.setPriceToNetIncome("x");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setPriceToNetIncome(".1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setPriceToNetIncome("1.");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setPriceToNetIncome("12345");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setPriceToNetIncome("10.123");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_2_true);
        dto.setPriceToNetIncome(validPe);

        dto.setDividendYield("x");
        Assert.postValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setDividendYield(".1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setDividendYield("1.");
        Assert.postValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setDividendYield("1234");
        Assert.postValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setDividendYield("10.123");
        Assert.postValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setDividendYield("-1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_3_2_false);
        dto.setDividendYield(validDy);

        dto.setSumAssetQuantity("x");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setSumAssetQuantity(".1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setSumAssetQuantity("1.");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setSumAssetQuantity("12345");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setSumAssetQuantity("10.12345");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setSumAssetQuantity("-1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setSumAssetQuantity(validQ);

        dto.setAvgAssetPrice("x");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setAvgAssetPrice(".1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setAvgAssetPrice("1.");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setAvgAssetPrice("1234567");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setAvgAssetPrice("10.12345");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setAvgAssetPrice("-1");
        Assert.postValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setAvgAssetPrice(validQ);
    }

    @Test
    void update()
    {
        String newTitle = "   ";
        String newContent = "[{\"type\":\"bulleted-list\",\"children\":[{\"type\":\"list-item\",\"children\":[{\"text\":\"saasdasdaa\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"as\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"das\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"s\"}]}]}]";
        String newReview = "review notes";
        String newStrategy = "buy as many as possible";
        String newRetro = "retro notes";
        String newTargets = "10-5~7";
        String newPrice = "321.45";
        String newDividendYield = "6.25";
        String newPriceToRevenues = "1.25";
        String newPriceToGrossProfit = "2.5";
        String newPriceToOperatingIncome = "-3.75";
        String newPriceToNetIncome = "4.25";
        String newSumAssetQuantity = "12.5";
        String newAvgAssetPrice = "123.45";

        RecordUpdateDto dto = new RecordUpdateDto();
        dto.setId(1974L);
        dto.setTitle(newTitle);
        dto.setContent(newContent);
        dto.setReview(newReview);
        dto.setStrategy(newStrategy);
        dto.setRetro(newRetro);
        dto.setTargets(newTargets);
        dto.setPrice(newPrice);
        dto.setDividendYield(newDividendYield);
        dto.setPriceToRevenues(newPriceToRevenues);
        dto.setPriceToGrossProfit(newPriceToGrossProfit);
        dto.setPriceToOperatingIncome(newPriceToOperatingIncome);
        dto.setPriceToNetIncome(newPriceToNetIncome);
        dto.setSumAssetQuantity(newSumAssetQuantity);
        dto.setAvgAssetPrice(newAvgAssetPrice);

        Assert.put204(path, dto);

        List<Record> records = recordDao.list(1842L);
        assertThat(records.size(), is(1));

        assertThat(records.get(0).getCompany().getTicker(), is("UPD"));
        assertThat(records.get(0).getTitle(), is(newTitle));
        assertThat(records.get(0).getContent(), is(newContent));
        assertThat(records.get(0).getReview(), is(newReview));
        assertThat(records.get(0).getStrategy(), is(newStrategy));
        assertThat(records.get(0).getRetro(), is(newRetro));
        assertThat(records.get(0).getTargets(), is(newTargets));
        assertBigDecimals(records.get(0).getPrice(), new BigDecimal(newPrice));
        assertBigDecimals(records.get(0).getDividendYield(), new BigDecimal(newDividendYield));
        assertBigDecimals(records.get(0).getPriceToRevenues(), new BigDecimal(newPriceToRevenues));
        assertBigDecimals(records.get(0).getPriceToGrossProfit(), new BigDecimal(newPriceToGrossProfit));
        assertBigDecimals(records.get(0).getPriceToOperatingIncome(), new BigDecimal(newPriceToOperatingIncome));
        assertBigDecimals(records.get(0).getPriceToNetIncome(), new BigDecimal(newPriceToNetIncome));
        assertBigDecimals(records.get(0).getSumAssetQuantity(), new BigDecimal(newSumAssetQuantity));
        assertBigDecimals(records.get(0).getAvgAssetPrice(), new BigDecimal(newAvgAssetPrice));

        io.restassured.RestAssured.given()
                .contentType(io.restassured.http.ContentType.JSON)
                .body("{\"id\":1974,\"dividendYield\":\"\"}")
                .when().put(path)
                .then().statusCode(204);
        entityManager.clear();
        assertThat(recordDao.list(1842L).get(0).getDividendYield(), is(nullValue()));

    }

    @Test
    void update_invalidParameters()
    {
        Assert.putValidationError(path, null, NOT_NULL);

        RecordUpdateDto dto =  new RecordUpdateDto();
        Assert.putValidationError(path, dto, NOT_NULL);

        dto.setId(0L);
        Assert.putValidationError(path, dto, VALID_ID);

        dto.setId(4_294_967_295L);
        Assert.put400(path, dto, "record with id '" + dto.getId() + "' not found");

        dto.setId(1974L);

        dto.setSumAssetQuantity("x");
        Assert.putValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setSumAssetQuantity(".1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setSumAssetQuantity("1.");
        Assert.putValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setSumAssetQuantity("12345");
        Assert.putValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setSumAssetQuantity("10.12345");
        Assert.putValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setSumAssetQuantity("-1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_4_4_false);
        dto.setSumAssetQuantity("12.5");

        dto.setAvgAssetPrice("x");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setAvgAssetPrice(".1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setAvgAssetPrice("1.");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setAvgAssetPrice("1234567");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setAvgAssetPrice("10.12345");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
        dto.setAvgAssetPrice("-1");
        Assert.putValidationError(path, dto, BIG_DECIMAL_6_4_false);
    }

    @Test
    void delete()
    {
        Assert.delete200(path + "/1916");
    }

    @Test
    void delete_invalidParameters()
    {
        Assert.deleteValidationError(path + "/0", VALID_ID);

        Long randomId = 4_294_967_295L;
        Assert.delete400(path + "/" + randomId, "record with id '" + randomId + "' not found");
    }
}
