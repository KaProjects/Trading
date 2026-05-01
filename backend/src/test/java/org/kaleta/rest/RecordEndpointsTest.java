package org.kaleta.rest;

import io.quarkus.test.Mock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
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
import java.util.UUID;

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
import static org.kaleta.framework.Assert.ExpectedViolation.VALID_UUID;
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
        dto.setTargets("some targets");

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
        assertThat(records.get(0).getTargets(), is(dto.getTargets()));
    }

    @Test
    void create_invalidParameters()
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

        Assert.postValidationError(path, null, NOT_NULL);

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
        Assert.postValidationError(path, dto, NOT_NULL);
        dto.setCompanyId("x");
        Assert.postValidationError(path, dto, VALID_UUID);
        dto.setCompanyId(UUID.randomUUID().toString());
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

        dto.setTitle(null);
        Assert.postValidationError(path, dto, NOT_NULL);
        dto.setTitle(validTitle);

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
        String newTitle = "new title";
        String newContent = "[{\"type\":\"bulleted-list\",\"children\":[{\"type\":\"list-item\",\"children\":[{\"text\":\"saasdasdaa\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"as\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"das\"}]},{\"type\":\"list-item\",\"children\":[{\"text\":\"s\"}]}]}]";
        String newStrategy = "buy as many as possible";
        String newTargets = "10-5~7";

        RecordUpdateDto dto = new RecordUpdateDto();
        dto.setId("b5a8a2b3-08b7-4a71-9301-f57d44a0a9cb");
        dto.setTitle(newTitle);
        dto.setContent(newContent);
        dto.setStrategy(newStrategy);
        dto.setTargets(newTargets);

        Assert.put204(path, dto);

        List<Record> records = recordDao.list("9c858901-8a57-4791-81fe-4c455b099bc9");
        assertThat(records.size(), is(1));

        assertThat(records.get(0).getCompany().getTicker(), is("UPD"));
        assertThat(records.get(0).getTitle(), is(newTitle));
        assertThat(records.get(0).getContent(), is(newContent));
        assertThat(records.get(0).getStrategy(), is(newStrategy));
        assertThat(records.get(0).getTargets(), is(newTargets));
    }

    @Test
    void update_invalidParameters()
    {
        Assert.putValidationError(path, null, NOT_NULL);

        RecordUpdateDto dto =  new RecordUpdateDto();
        Assert.putValidationError(path, dto, NOT_NULL);

        dto.setId("x");
        Assert.putValidationError(path, dto, VALID_UUID);

        dto.setId(UUID.randomUUID().toString());
        Assert.put400(path, dto, "record with id '" + dto.getId() + "' not found");
    }

    @Test
    void delete()
    {
        Assert.delete200(path + "/a9f86e1e-b81d-4b28-b4f3-91d25dfb6b43");
    }

    @Test
    void delete_invalidParameters()
    {
        Assert.deleteValidationError(path + "/x",VALID_UUID);

        String randomId = UUID.randomUUID().toString();
        Assert.delete400(path + "/" + randomId, "record with id '" + randomId + "' not found");
    }
}