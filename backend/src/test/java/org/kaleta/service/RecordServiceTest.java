package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.kaleta.Utils;
import org.kaleta.framework.Generator;
import org.kaleta.model.Assets;
import org.kaleta.model.Periods;
import org.kaleta.model.PriceIndicators;
import org.kaleta.persistence.api.RecordDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Latest;
import org.kaleta.persistence.entity.Record;
import org.kaleta.rest.dto.RecordCreateDto;
import org.kaleta.rest.dto.RecordUpdateDto;
import org.kaleta.rest.error.InvalidInputException;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kaleta.framework.Assert.assertBigDecimals;
import static org.kaleta.framework.InvalidValues.invalidBigDecimals;
import static org.kaleta.framework.InvalidValues.invalidDates;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class RecordServiceTest
{
    @InjectMock
    CompanyService companyService;
    @InjectMock
    RecordDao recordDao;
    @InjectMock
    PeriodService periodService;
    @InjectMock
    TradeService tradeService;

    @Inject
    RecordService recordService;

    @Test
    void create()
    {
        String d = "3030-01-01";
        String t = "new title";
        String p = Generator.randomBigDecimal(999999, 4).toString();
        String ps = Generator.randomBigDecimal(9999, 2).toString();
        String pg = Generator.randomBigDecimal(9999, 2).toString();
        String po = Generator.randomBigDecimal(9999, 2).toString();
        String pe = Generator.randomBigDecimal(9999, 2).toString();
        String dy = Generator.randomBigDecimal(999, 2).toString();
        String q = Generator.randomBigDecimal(9999, 4).toString();
        String pp = Generator.randomBigDecimal(999999, 4).toString();
        String tg = "targets";

        createAndAssertRecord(d, t, p, ps, pg, po, pe, dy, q, pp, tg, null);
        createAndAssertRecord(d, t, p, null, null, null, null, null, null, null, null, null);

        createAndAssertRecord(null, t, p, ps, pg, po, pe, dy, q, pp, tg, IllegalArgumentException.class);
        invalidDates().forEach(date -> createAndAssertRecord(date, t, p, ps, pg, po, pe, dy, q, pp, tg, IllegalArgumentException.class));

        createAndAssertRecord(d, t, null, ps, pg, po, pe, dy, q, pp, tg, NullPointerException.class);
        invalidBigDecimals().forEach(ibd -> createAndAssertRecord(d, t, ibd, ps, pg, po, pe, dy, q, pp, tg, IllegalArgumentException.class));
        invalidBigDecimals().forEach(ibd -> createAndAssertRecord(d, t, p, ibd, pg, po, pe, dy, q, pp, tg, IllegalArgumentException.class));
        invalidBigDecimals().forEach(ibd -> createAndAssertRecord(d, t, p, ps, ibd, po, pe, dy, q, pp, tg, IllegalArgumentException.class));
        invalidBigDecimals().forEach(ibd -> createAndAssertRecord(d, t, p, ps, pg, ibd, pe, dy, q, pp, tg, IllegalArgumentException.class));
        invalidBigDecimals().forEach(ibd -> createAndAssertRecord(d, t, p, ps, pg, po, ibd, dy, q, pp, tg, IllegalArgumentException.class));
        invalidBigDecimals().forEach(ibd -> createAndAssertRecord(d, t, p, ps, pg, po, pe, ibd, q, pp, tg, IllegalArgumentException.class));
        invalidBigDecimals().forEach(ibd -> createAndAssertRecord(d, t, p, ps, pg, po, pe, dy, ibd, pp, tg, IllegalArgumentException.class));
        invalidBigDecimals().forEach(ibd -> createAndAssertRecord(d, t, p, ps, pg, po, pe, dy, q, ibd, tg, IllegalArgumentException.class));
    }

    @Test
    void update()
    {
        Company company = Generator.generateCompany();
        Record record = Generator.generateRecord(company, "2020-01-01");

        when(recordDao.get(record.getId())).thenReturn(record);
        when(recordDao.get(null)).thenThrow(NoResultException.class);

        RecordUpdateDto dto = new RecordUpdateDto();

        updateAndAssertRecord(dto, record, InvalidInputException.class);

        dto.setId(record.getId());
        updateAndAssertRecord(dto, record, null);

        dto.setTitle("");
        updateAndAssertRecord(dto, record, InvalidInputException.class);

        dto.setTitle("title");
        updateAndAssertRecord(dto, record, null);

        dto.setContent("content");
        updateAndAssertRecord(dto, record, null);

        dto.setStrategy("strategy");
        updateAndAssertRecord(dto, record, null);

        dto.setTargets("targets");
        updateAndAssertRecord(dto, record, null);
    }

    @Test
    void getBy() {
        Company company = Generator.generateCompany();
        Record record1 = Generator.generateRecord(company, "2025-10-01");
        Record record2 = Generator.generateRecord(company, "2024-11-21");
        Record record3 = Generator.generateRecord(company, "2025-12-15");

        when(companyService.findEntity(company.getId())).thenReturn(company);
        when(recordDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(record1, record2, record3)));

        List<org.kaleta.model.Record> records = recordService.getBy(company.getId());

        assertThat(records.get(0).getId(), is(record3.getId()));
        assertThat(records.get(1).getId(), is(record1.getId()));
        assertThat(records.get(2).getId(), is(record2.getId()));
    }

    @Test
    void delete()
    {
        Company company = Generator.generateCompany();
        Record record = Generator.generateRecord(company, "2020-01-01");

        String randomId = UUID.randomUUID().toString();

        when(recordDao.get(record.getId())).thenReturn(record);
        when(recordDao.get(randomId)).thenThrow(NoResultException.class);

        assertThrows(InvalidInputException.class, () -> recordService.delete(randomId));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(recordDao, times(0)).delete(captor.capture());

        recordService.delete(record.getId());
        verify(recordDao).delete(captor.capture());

        assertThat(captor.getValue(), is(record.getId()));
    }

    @Test
    void createCurrent() {
        Company company = Generator.generateCompany();
        when(companyService.findEntity(company.getId())).thenReturn(company);
        doThrow(new InvalidInputException("")).when(companyService).findEntity("a9f86e1e-b81d-4b28-b4f3-91d25dfb6b43");

        Periods periods = new Periods();
        periods.setTtm(Generator.generatePeriodsFinancial());
        when(periodService.getBy(company.getId())).thenReturn(periods);

        Assets assets = new Assets();
        assets.setAggregate(Generator.generateAsset());
        when(tradeService.getAssets(company.getId(), assets.getAggregate().getCurrentPrice())).thenReturn(assets);

        String validD = "3030-01-01";
        String validT = "new title";
        String validP = String.valueOf(assets.getAggregate().getCurrentPrice());

        PriceIndicators expectedRatios = new ArithmeticService().computeIndicators(new Latest(company, LocalDate.parse(validD).atStartOfDay(), new BigDecimal(validP)), periods.getTtm());

        createCurrentAndAssertRecord(company.getId(), validT, validD, validP, expectedRatios, null);

        createCurrentAndAssertRecord("a9f86e1e-b81d-4b28-b4f3-91d25dfb6b43", validT, validD, validP, expectedRatios, InvalidInputException.class);

        createCurrentAndAssertRecord(company.getId(), null, validD, validP, expectedRatios, null);

        createCurrentAndAssertRecord(company.getId(), validT, null, validP, expectedRatios, IllegalArgumentException.class);
        invalidDates().forEach(d -> createCurrentAndAssertRecord(company.getId(), validT, d, validP, expectedRatios, IllegalArgumentException.class));

        createCurrentAndAssertRecord(company.getId(), validT, validD, null, expectedRatios, NullPointerException.class);
        invalidBigDecimals().forEach(p -> createCurrentAndAssertRecord(company.getId(), validT, validD, p, expectedRatios, IllegalArgumentException.class));
    }

    @Test
    void createCurrent_withoutFinancialsOrAssets()
    {
        Company company = Generator.generateCompany();
        company.setCurrency(Currency.$);
        when(companyService.findEntity(company.getId())).thenReturn(company);

        Periods periods = new Periods();
        when(periodService.getBy(company.getId())).thenReturn(periods);

        Assets assets = new Assets();
        when(tradeService.getAssets(company.getId(), new BigDecimal("123"))).thenReturn(assets);

        recordService.createCurrent(company.getId(), "snapshot", "2030-01-01", "123");

        ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
        verify(recordDao).create(captor.capture());

        assertThat(captor.getValue().getTitle(), is("snapshot@123$"));
        assertThat(captor.getValue().getDate(), is(Date.valueOf("2030-01-01")));
        assertBigDecimals(captor.getValue().getPrice(), new BigDecimal("123"));
        assertThat(captor.getValue().getPriceToRevenues(), is(Matchers.nullValue()));
        assertThat(captor.getValue().getPriceToGrossProfit(), is(Matchers.nullValue()));
        assertThat(captor.getValue().getPriceToOperatingIncome(), is(Matchers.nullValue()));
        assertThat(captor.getValue().getPriceToNetIncome(), is(Matchers.nullValue()));
        assertThat(captor.getValue().getDividendYield(), is(Matchers.nullValue()));
        assertThat(captor.getValue().getSumAssetQuantity(), is(Matchers.nullValue()));
        assertThat(captor.getValue().getAvgAssetPrice(), is(Matchers.nullValue()));
    }

    @Test
    void getCompanyAggregates()
    {
        Company company1 = Generator.generateCompany("company-1");
        Company company2 = Generator.generateCompany("company-2");

        when(recordDao.list()).thenReturn(List.of(
                Generator.generateRecord(company1, "2024-01-01"),
                Generator.generateRecord(company1, "2024-02-01"),
                Generator.generateRecord(company2, "2024-03-01")
        ));

        Map<String, int[]> aggregates = recordService.getCompanyAggregates();

        assertThat(aggregates.size(), is(2));
        assertThat(aggregates.get(company1.getId())[0], is(2));
        assertThat(aggregates.get(company2.getId())[0], is(1));
    }

    private void createCurrentAndAssertRecord(String cid, String t, String d, String p,
                                              PriceIndicators expectedRatios,
                                              Class<? extends Exception> expectedException)
    {
        if (expectedException == null)
        {
            recordService.createCurrent(cid, t, d, p);

            ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
            verify(recordDao).create(captor.capture());

            assertThat(captor.getValue().getCompany().getId(), is(cid));
            assertThat(captor.getValue().getTitle(), Matchers.startsWith((t == null) ? "null" : t));
            assertThat(captor.getValue().getDate(), is(Date.valueOf(d)));
            assertBigDecimals(captor.getValue().getPrice(), new BigDecimal(p));

            assertBigDecimals(captor.getValue().getPriceToRevenues(), expectedRatios.getTtm().getMarketCapToRevenues());
            assertBigDecimals(captor.getValue().getPriceToGrossProfit(), expectedRatios.getTtm().getMarketCapToGrossProfit());
            assertBigDecimals(captor.getValue().getPriceToOperatingIncome(), expectedRatios.getTtm().getMarketCapToOperatingIncome());
            assertBigDecimals(captor.getValue().getPriceToNetIncome(), expectedRatios.getTtm().getMarketCapToNetIncome());
            assertBigDecimals(captor.getValue().getDividendYield(), expectedRatios.getTtm().getDividendYield());

            clearInvocations(recordDao);
        } else {
            assertThrows(expectedException, () -> recordService.createCurrent(cid, t, d, p));
        }
    }

    private void updateAndAssertRecord(RecordUpdateDto dto, Record record, Class<? extends Exception> expectedException)
    {
        if (expectedException == null)
        {
            recordService.update(dto);

            ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
            verify(recordDao).save(captor.capture());

            assertThat(captor.getValue().getCompany().getId(), is(record.getCompany().getId()));

            assertThat(captor.getValue().getTitle(), (dto.getTitle() == null) ? is(record.getTitle()) : is(dto.getTitle()));
            assertThat(captor.getValue().getContent(), (dto.getContent() == null) ? is(record.getContent()) : is(dto.getContent()));
            assertThat(captor.getValue().getStrategy(), (dto.getStrategy() == null) ? is(record.getStrategy()) : is(dto.getStrategy()));
            assertThat(captor.getValue().getTargets(), (dto.getTargets() == null) ? is(record.getTargets()) : is(dto.getTargets()));

            clearInvocations(recordDao);
        } else {
            assertThrows(expectedException, () -> recordService.update(dto));
        }
    }

    private void createAndAssertRecord(String date, String title, String price,
                                       String ps, String pg, String po, String pe, String dy,
                                       String q, String pp, String targets,
                                       Class<? extends Exception> expectedException)
    {
        Company company = Generator.generateCompany();
        when(companyService.findEntity(company.getId())).thenReturn(company);

        RecordCreateDto dto = new RecordCreateDto();
        dto.setCompanyId(company.getId());
        dto.setDate(date);
        dto.setPrice(price);
        dto.setTitle(title);
        dto.setPriceToRevenues(ps);
        dto.setPriceToGrossProfit(pg);
        dto.setPriceToOperatingIncome(po);
        dto.setPriceToNetIncome(pe);
        dto.setDividendYield(dy);
        dto.setSumAssetQuantity(q);
        dto.setAvgAssetPrice(pp);
        dto.setTargets(targets);

        if (expectedException == null) {
            recordService.create(dto);

            ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
            verify(recordDao).create(captor.capture());

            assertThat(captor.getValue().getCompany().getId(), is(company.getId()));
            assertThat(captor.getValue().getDate(), is(Date.valueOf(date)));
            assertBigDecimals(captor.getValue().getPrice(), Utils.createNullableBigDecimal(price));
            assertThat(captor.getValue().getTitle(), is(title));

            assertBigDecimals(captor.getValue().getPriceToRevenues(), Utils.createNullableBigDecimal(ps));
            assertBigDecimals(captor.getValue().getPriceToGrossProfit(), Utils.createNullableBigDecimal(pg));
            assertBigDecimals(captor.getValue().getPriceToOperatingIncome(), Utils.createNullableBigDecimal(po));
            assertBigDecimals(captor.getValue().getPriceToNetIncome(), Utils.createNullableBigDecimal(pe));

            assertBigDecimals(captor.getValue().getDividendYield(), Utils.createNullableBigDecimal(dy));

            assertBigDecimals(captor.getValue().getSumAssetQuantity(), Utils.createNullableBigDecimal(q));
            assertBigDecimals(captor.getValue().getAvgAssetPrice(), Utils.createNullableBigDecimal(pp));
            assertThat(captor.getValue().getTargets(), is(targets));

            clearInvocations(recordDao);
        } else {
            assertThrows(expectedException, () -> recordService.create(dto));
        }
    }
}
