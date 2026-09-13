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
import org.kaleta.model.PeriodEstimates;
import org.kaleta.model.Periods;
import org.kaleta.model.PriceIndicators;
import org.kaleta.model.TargetStats;
import org.kaleta.model.TradeSaleSummary;
import org.kaleta.persistence.api.RecordDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Estimate;
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
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kaleta.framework.Assert.assertBigDecimals;
import static org.kaleta.framework.InvalidValues.invalidBigDecimals;
import static org.kaleta.framework.InvalidValues.invalidDates;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
    @InjectMock
    EstimateService estimateService;
    @InjectMock
    TargetService targetService;

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
        String pfcf = Generator.randomBigDecimal(9999, 2).toString();
        String dy = Generator.randomBigDecimal(999, 2).toString();
        String fpe = Generator.randomBigDecimal(9999, 2).toString();
        String q = Generator.randomBigDecimal(9999, 4).toString();
        String pp = Generator.randomBigDecimal(999999, 4).toString();
        String tg = "targets";

        createAndAssertRecord(d, t, p, ps, pg, po, pe, pfcf, dy, fpe, q, pp, tg, null);
        createAndAssertRecord(d, t, p, null, null, null, null, null, null, null, null, null, null, null);

        createAndAssertRecord(null, t, p, ps, pg, po, pe, pfcf, dy, fpe, q, pp, tg, IllegalArgumentException.class);
        invalidDates().forEach(date -> createAndAssertRecord(date, t, p, ps, pg, po, pe, pfcf, dy, fpe, q, pp, tg, IllegalArgumentException.class));

        createAndAssertRecord(d, t, null, ps, pg, po, pe, pfcf, dy, fpe, q, pp, tg, NullPointerException.class);
        invalidBigDecimals().forEach(ibd -> createAndAssertRecord(d, t, ibd, ps, pg, po, pe, pfcf, dy, fpe, q, pp, tg, IllegalArgumentException.class));
        invalidBigDecimals().forEach(ibd -> createAndAssertRecord(d, t, p, ibd, pg, po, pe, pfcf, dy, fpe, q, pp, tg, IllegalArgumentException.class));
        invalidBigDecimals().forEach(ibd -> createAndAssertRecord(d, t, p, ps, ibd, po, pe, pfcf, dy, fpe, q, pp, tg, IllegalArgumentException.class));
        invalidBigDecimals().forEach(ibd -> createAndAssertRecord(d, t, p, ps, pg, ibd, pe, pfcf, dy, fpe, q, pp, tg, IllegalArgumentException.class));
        invalidBigDecimals().forEach(ibd -> createAndAssertRecord(d, t, p, ps, pg, po, ibd, pfcf, dy, fpe, q, pp, tg, IllegalArgumentException.class));
        invalidBigDecimals().forEach(ibd -> createAndAssertRecord(d, t, p, ps, pg, po, pe, ibd, dy, fpe, q, pp, tg, IllegalArgumentException.class));
        invalidBigDecimals().forEach(ibd -> createAndAssertRecord(d, t, p, ps, pg, po, pe, pfcf, ibd, fpe, q, pp, tg, IllegalArgumentException.class));
        invalidBigDecimals().forEach(ibd -> createAndAssertRecord(d, t, p, ps, pg, po, pe, pfcf, dy, ibd, q, pp, tg, IllegalArgumentException.class));
        invalidBigDecimals().forEach(ibd -> createAndAssertRecord(d, t, p, ps, pg, po, pe, pfcf, dy, fpe, ibd, pp, tg, IllegalArgumentException.class));
        invalidBigDecimals().forEach(ibd -> createAndAssertRecord(d, t, p, ps, pg, po, pe, pfcf, dy, fpe, q, ibd, tg, IllegalArgumentException.class));
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
        updateAndAssertRecord(dto, record, null);

        dto.setTitle("   ");
        updateAndAssertRecord(dto, record, null);

        dto.setTitle("title");
        updateAndAssertRecord(dto, record, null);

        dto.setContent("content");
        updateAndAssertRecord(dto, record, null);

        dto.setReview("review");
        updateAndAssertRecord(dto, record, null);

        dto.setStrategy("strategy");
        updateAndAssertRecord(dto, record, null);

        dto.setRetro("retro");
        updateAndAssertRecord(dto, record, null);

        dto.setTargets("targets");
        updateAndAssertRecord(dto, record, null);

        dto.setPrice("123.45");
        updateAndAssertRecord(dto, record, null);

        dto.setDividendYield("6.25");
        updateAndAssertRecord(dto, record, null);

        dto.setDividendYield("");
        updateAndAssertRecord(dto, record, null);

        dto.setForwardPe("21.16");
        updateAndAssertRecord(dto, record, null);

        dto.setForwardPe("");
        updateAndAssertRecord(dto, record, null);

        dto.setPriceToRevenues("1.25");
        dto.setPriceToGrossProfit("2.5");
        dto.setPriceToOperatingIncome("-3.75");
        dto.setPriceToNetIncome("4.25");
        dto.setPriceToFreeCashFlow("5.75");
        updateAndAssertRecord(dto, record, null);

        dto.setSumAssetQuantity("12.5");
        updateAndAssertRecord(dto, record, null);

        dto.setAvgAssetPrice("123.45");
        updateAndAssertRecord(dto, record, null);
    }

    @Test
    void getBy() {
        Company company = Generator.generateCompany();
        Record record1 = Generator.generateRecord(company, "2025-10-01");
        Record record2 = Generator.generateRecord(company, "2024-11-21");
        Record record3 = Generator.generateRecord(company, "2025-12-15");
        record3.setReview("latest review");
        record3.setRetro("latest retro");
        record3.setPriceToFreeCashFlow(new BigDecimal("17.25"));

        when(companyService.findEntity(company.getId())).thenReturn(company);
        when(recordDao.list(company.getId())).thenReturn(new ArrayList<>(List.of(record1, record2, record3)));

        List<org.kaleta.model.Record> records = recordService.getBy(company.getId());

        assertThat(records.get(0).getId(), is(record3.getId()));
        assertThat(records.get(0).getReview(), is("latest review"));
        assertThat(records.get(0).getRetro(), is("latest retro"));
        assertBigDecimals(records.get(0).getPriceToFreeCashFlow(), new BigDecimal("17.25"));
        assertThat(records.get(1).getId(), is(record1.getId()));
        assertThat(records.get(2).getId(), is(record2.getId()));
    }

    @Test
    void delete()
    {
        Company company = Generator.generateCompany();
        Record record = Generator.generateRecord(company, "2020-01-01");

        Long randomId = 4_294_967_295L;

        when(recordDao.get(record.getId())).thenReturn(record);
        when(recordDao.get(randomId)).thenThrow(NoResultException.class);

        assertThrows(InvalidInputException.class, () -> recordService.delete(randomId));

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(recordDao, times(0)).delete(captor.capture());

        recordService.delete(record.getId());
        verify(recordDao).delete(captor.capture());

        assertThat(captor.getValue(), is(record.getId()));
    }

    @Test
    void createCurrent() {
        Company company = Generator.generateCompany();
        when(companyService.findEntity(company.getId())).thenReturn(company);
        doThrow(new InvalidInputException("")).when(companyService).findEntity(1916L);

        Periods periods = new Periods();
        periods.setTtm(Generator.generatePeriodsFinancial());
        periods.getTtm().getFreeCashFlow().setValue(new BigDecimal("2500"));
        when(periodService.getBy(company.getId())).thenReturn(periods);

        Assets assets = new Assets();
        assets.setAggregate(Generator.generateAsset());
        when(tradeService.getAssets(company.getId(), assets.getAggregate().getCurrentPrice())).thenReturn(assets);

        String validD = "3030-01-01";
        String validT = "new title";
        String validP = String.valueOf(assets.getAggregate().getCurrentPrice());

        PriceIndicators expectedRatios = new ArithmeticService().computeIndicators(new Latest(company, LocalDate.parse(validD).atStartOfDay(), new BigDecimal(validP)), periods.getTtm());

        createCurrentAndAssertRecord(company.getId(), validT, validD, validP, expectedRatios, null);

        createCurrentAndAssertRecord(1916L, validT, validD, validP, expectedRatios, InvalidInputException.class);

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

        assertThat(captor.getValue().getTitle(), is(Matchers.nullValue()));
        assertThat(captor.getValue().getStrategy(), is(bulletedList("snapshot@123$")));
        assertThat(captor.getValue().getDate(), is(Date.valueOf("2030-01-01")));
        assertBigDecimals(captor.getValue().getPrice(), new BigDecimal("123"));
        assertThat(captor.getValue().getPriceToRevenues(), is(Matchers.nullValue()));
        assertThat(captor.getValue().getPriceToGrossProfit(), is(Matchers.nullValue()));
        assertThat(captor.getValue().getPriceToOperatingIncome(), is(Matchers.nullValue()));
        assertThat(captor.getValue().getPriceToNetIncome(), is(Matchers.nullValue()));
        assertThat(captor.getValue().getPriceToFreeCashFlow(), is(Matchers.nullValue()));
        assertThat(captor.getValue().getDividendYield(), is(Matchers.nullValue()));
        assertThat(captor.getValue().getSumAssetQuantity(), is(Matchers.nullValue()));
        assertThat(captor.getValue().getAvgAssetPrice(), is(Matchers.nullValue()));
    }

    @Test
    void createCurrent_withSalePerformance()
    {
        Company company = Generator.generateCompany();
        company.setCurrency(Currency.$);
        when(companyService.findEntity(company.getId())).thenReturn(company);
        when(periodService.getBy(company.getId())).thenReturn(new Periods());
        when(tradeService.getAssets(company.getId(), new BigDecimal("123"))).thenReturn(new Assets());

        recordService.createCurrent(
                company.getId(),
                "sold 1",
                "2030-01-01",
                "123",
                new TradeSaleSummary(
                        BigDecimal.ONE,
                        new BigDecimal("100.123456"),
                        new BigDecimal("6.789"),
                        new BigDecimal("-45.678"),
                        new BigDecimal("-12.345")));

        ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
        verify(recordDao).create(captor.capture());

        assertThat(captor.getValue().getStrategy(), is(
                bulletedList(
                        "sold 1@123$",
                        "- 1@100.12346$ - 6.79$ = -45.68$ (-12.35%)")));
    }

    @Test
    void createCurrent_appendsToRecordOfTheSameDay()
    {
        Company company = Generator.generateCompany();
        company.setCurrency(Currency.$);
        when(companyService.findEntity(company.getId())).thenReturn(company);
        when(periodService.getBy(company.getId())).thenReturn(new Periods());
        when(tradeService.getAssets(company.getId(), new BigDecimal("123"))).thenReturn(new Assets());

        Record existing = new Record();
        existing.setId(11L);
        existing.setCompany(company);
        existing.setDate(Date.valueOf("2030-01-01"));
        existing.setPrice(new BigDecimal("100"));
        existing.setStrategy(bulletedList("earlier note"));
        existing.setTargets("165-210$");
        when(recordDao.list(company.getId())).thenReturn(List.of(existing));

        recordService.createCurrent(company.getId(), "bought 1", "2030-01-01", "123");

        ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
        verify(recordDao).save(captor.capture());
        verify(recordDao, never()).create(any(Record.class));

        assertThat(captor.getValue().getId(), is(11L));
        assertThat(captor.getValue().getStrategy(), is(
                merged(bulletedList("earlier note"), bulletedList("bought 1@123$"))));
        assertBigDecimals(captor.getValue().getPrice(), new BigDecimal("123"));
        assertThat(captor.getValue().getTargets(), is("165-210$"));
    }

    @Test
    void createCurrent_replacesEmptyStrategyOfTheSameDay()
    {
        Company company = Generator.generateCompany();
        company.setCurrency(Currency.$);
        when(companyService.findEntity(company.getId())).thenReturn(company);
        when(periodService.getBy(company.getId())).thenReturn(new Periods());
        when(tradeService.getAssets(company.getId(), new BigDecimal("123"))).thenReturn(new Assets());

        Record existing = new Record();
        existing.setId(11L);
        existing.setCompany(company);
        existing.setDate(Date.valueOf("2030-01-01"));
        existing.setPrice(new BigDecimal("100"));
        existing.setStrategy("[{\"type\":\"paragraph\",\"children\":[{\"text\":\"\"}]}]");
        when(recordDao.list(company.getId())).thenReturn(List.of(existing));

        recordService.createCurrent(company.getId(), "bought 1", "2030-01-01", "123");

        ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
        verify(recordDao).save(captor.capture());

        assertThat(captor.getValue().getStrategy(), is(bulletedList("bought 1@123$")));
    }

    @Test
    void createCurrent_createsRecordWhenTheDayDiffersOrContentCannotBeMerged()
    {
        Company company = Generator.generateCompany();
        company.setCurrency(Currency.$);
        when(companyService.findEntity(company.getId())).thenReturn(company);
        when(periodService.getBy(company.getId())).thenReturn(new Periods());
        when(tradeService.getAssets(company.getId(), new BigDecimal("123"))).thenReturn(new Assets());

        Record otherDay = new Record();
        otherDay.setId(11L);
        otherDay.setCompany(company);
        otherDay.setDate(Date.valueOf("2029-12-31"));
        otherDay.setStrategy(bulletedList("earlier note"));
        when(recordDao.list(company.getId())).thenReturn(List.of(otherDay));

        recordService.createCurrent(company.getId(), "bought 1", "2030-01-01", "123");

        verify(recordDao).create(any(Record.class));
        verify(recordDao, never()).save(any(Record.class));
        clearInvocations(recordDao);

        Record corrupted = new Record();
        corrupted.setId(12L);
        corrupted.setCompany(company);
        corrupted.setDate(Date.valueOf("2030-01-01"));
        corrupted.setStrategy("not a content");
        when(recordDao.list(company.getId())).thenReturn(List.of(corrupted));

        recordService.createCurrent(company.getId(), "bought 1", "2030-01-01", "123");

        verify(recordDao).create(any(Record.class));
        verify(recordDao, never()).save(any(Record.class));
    }

    @Test
    void createCurrent_withForwardPeAndTargetsOfLatestPeriod()
    {
        Company company = Generator.generateCompany();
        company.setCurrency(Currency.$);
        when(companyService.findEntity(company.getId())).thenReturn(company);

        Periods.Period unreported = new Periods.Period();
        unreported.setId(77L);
        Periods periods = new Periods();
        periods.getPeriods().add(unreported);
        when(periodService.getBy(company.getId())).thenReturn(periods);
        when(tradeService.getAssets(company.getId(), new BigDecimal("123"))).thenReturn(new Assets());

        PeriodEstimates estimates = new PeriodEstimates();
        estimates.setCurrent(new BigDecimal("1.2"));
        estimates.setNext1(new BigDecimal("1.3"));
        estimates.setNext2(new BigDecimal("1.5"));
        estimates.setNext3(new BigDecimal("1.8"));
        when(estimateService.getLatest(77L, Estimate.EPS)).thenReturn(Optional.of(estimates));
        when(targetService.getStatistics(List.of(77L))).thenReturn(Map.of(77L,
                new TargetStats(3, new BigDecimal("288"), new BigDecimal("317.33"), new BigDecimal("352"))));

        recordService.createCurrent(company.getId(), "bought 1", "2030-01-01", "123");

        ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
        verify(recordDao).create(captor.capture());

        assertBigDecimals(captor.getValue().getForwardPe(), new BigDecimal("21.21"));
        assertThat(captor.getValue().getTargets(), is("3@(352-288)~317$"));
    }

    @Test
    void createCurrent_withoutForwardPeWhenThePeriodIsReportedOrEstimatesAreIncomplete()
    {
        Company company = Generator.generateCompany();
        company.setCurrency(Currency.$);
        when(companyService.findEntity(company.getId())).thenReturn(company);
        when(tradeService.getAssets(company.getId(), new BigDecimal("123"))).thenReturn(new Assets());

        Periods.Period reported = new Periods.Period();
        reported.setId(77L);
        reported.setFinancial(new Periods.Financial());
        Periods reportedPeriods = new Periods();
        reportedPeriods.getPeriods().add(reported);
        when(periodService.getBy(company.getId())).thenReturn(reportedPeriods);

        recordService.createCurrent(company.getId(), "bought 1", "2030-01-01", "123");

        ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
        verify(recordDao).create(captor.capture());
        assertThat(captor.getValue().getForwardPe(), is(Matchers.nullValue()));
        clearInvocations(recordDao);

        Periods.Period unreported = new Periods.Period();
        unreported.setId(78L);
        Periods unreportedPeriods = new Periods();
        unreportedPeriods.getPeriods().add(unreported);
        when(periodService.getBy(company.getId())).thenReturn(unreportedPeriods);

        PeriodEstimates estimates = new PeriodEstimates();
        estimates.setCurrent(new BigDecimal("1.2"));
        estimates.setNext1(new BigDecimal("1.3"));
        when(estimateService.getLatest(78L, Estimate.EPS)).thenReturn(Optional.of(estimates));

        recordService.createCurrent(company.getId(), "bought 1", "2030-01-01", "123");

        verify(recordDao).create(captor.capture());
        assertThat(captor.getValue().getForwardPe(), is(Matchers.nullValue()));
    }

    private void createCurrentAndAssertRecord(Long cid, String t, String d, String p,
                                              PriceIndicators expectedRatios,
                                              Class<? extends Exception> expectedException)
    {
        if (expectedException == null)
        {
            recordService.createCurrent(cid, t, d, p);

            ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
            verify(recordDao).create(captor.capture());

            assertThat(captor.getValue().getCompany().getId(), is(cid));
            assertThat(captor.getValue().getTitle(), is(Matchers.nullValue()));
            assertThat(captor.getValue().getStrategy(), is(bulletedList(
                    String.valueOf(t) + "@" + p + captor.getValue().getCompany().getCurrency())));
            assertThat(captor.getValue().getDate(), is(Date.valueOf(d)));
            assertBigDecimals(captor.getValue().getPrice(), new BigDecimal(p));

            assertBigDecimals(captor.getValue().getPriceToRevenues(), expectedRatios.getTtm().getMarketCapToRevenues());
            assertBigDecimals(captor.getValue().getPriceToGrossProfit(), expectedRatios.getTtm().getMarketCapToGrossProfit());
            assertBigDecimals(captor.getValue().getPriceToOperatingIncome(), expectedRatios.getTtm().getMarketCapToOperatingIncome());
            assertBigDecimals(captor.getValue().getPriceToNetIncome(), expectedRatios.getTtm().getMarketCapToNetIncome());
            assertBigDecimals(captor.getValue().getPriceToFreeCashFlow(), expectedRatios.getTtm().getMarketCapToFreeCashFlow());
            assertBigDecimals(captor.getValue().getDividendYield(), expectedRatios.getTtm().getDividendYield());

            clearInvocations(recordDao);
        } else {
            assertThrows(expectedException, () -> recordService.createCurrent(cid, t, d, p));
        }
    }

    private static String merged(String first, String second)
    {
        return "[" + first.substring(1, first.length() - 1) + "," + second.substring(1, second.length() - 1) + "]";
    }

    private static String bulletedList(String text)
    {
        return "[{\"type\":\"bulleted-list\",\"children\":[{\"type\":\"list-item\",\"children\":[{\"text\":\""
                + text + "\"}]}]}]";
    }

    private static String bulletedList(String text, String... innerTexts)
    {
        StringBuilder innerList = new StringBuilder();
        for (String innerText : innerTexts) {
            if (!innerList.isEmpty()) innerList.append(",");
            innerList.append("{\"type\":\"list-item\",\"children\":[{\"text\":\"")
                    .append(innerText)
                    .append("\"}]}");
        }
        return "[{\"type\":\"bulleted-list\",\"children\":[{\"type\":\"list-item\",\"children\":[{\"text\":\""
                + text + "\"},{\"type\":\"bulleted-list\",\"children\":[" + innerList + "]}]}]}]";
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
            assertThat(captor.getValue().getReview(), (dto.getReview() == null) ? is(record.getReview()) : is(dto.getReview()));
            assertThat(captor.getValue().getStrategy(), (dto.getStrategy() == null) ? is(record.getStrategy()) : is(dto.getStrategy()));
            assertThat(captor.getValue().getRetro(), (dto.getRetro() == null) ? is(record.getRetro()) : is(dto.getRetro()));
            assertThat(captor.getValue().getTargets(), (dto.getTargets() == null) ? is(record.getTargets()) : is(dto.getTargets()));
            assertBigDecimals(captor.getValue().getPrice(), (dto.getPrice() == null) ? record.getPrice() : new BigDecimal(dto.getPrice()));
            assertBigDecimals(captor.getValue().getDividendYield(),
                    dto.getDividendYield() == null
                            ? record.getDividendYield()
                            : (dto.getDividendYield().isBlank() ? null : new BigDecimal(dto.getDividendYield())));
            assertBigDecimals(captor.getValue().getForwardPe(),
                    dto.getForwardPe() == null
                            ? record.getForwardPe()
                            : (dto.getForwardPe().isBlank() ? null : new BigDecimal(dto.getForwardPe())));
            assertBigDecimals(captor.getValue().getPriceToRevenues(), (dto.getPriceToRevenues() == null) ? record.getPriceToRevenues() : Utils.createNullableBigDecimal(dto.getPriceToRevenues()));
            assertBigDecimals(captor.getValue().getPriceToGrossProfit(), (dto.getPriceToGrossProfit() == null) ? record.getPriceToGrossProfit() : Utils.createNullableBigDecimal(dto.getPriceToGrossProfit()));
            assertBigDecimals(captor.getValue().getPriceToOperatingIncome(), (dto.getPriceToOperatingIncome() == null) ? record.getPriceToOperatingIncome() : Utils.createNullableBigDecimal(dto.getPriceToOperatingIncome()));
            assertBigDecimals(captor.getValue().getPriceToNetIncome(), (dto.getPriceToNetIncome() == null) ? record.getPriceToNetIncome() : Utils.createNullableBigDecimal(dto.getPriceToNetIncome()));
            assertBigDecimals(captor.getValue().getPriceToFreeCashFlow(), (dto.getPriceToFreeCashFlow() == null) ? record.getPriceToFreeCashFlow() : Utils.createNullableBigDecimal(dto.getPriceToFreeCashFlow()));
            assertBigDecimals(captor.getValue().getSumAssetQuantity(), (dto.getSumAssetQuantity() == null) ? record.getSumAssetQuantity() : Utils.createNullableBigDecimal(dto.getSumAssetQuantity()));
            assertBigDecimals(captor.getValue().getAvgAssetPrice(), (dto.getAvgAssetPrice() == null) ? record.getAvgAssetPrice() : Utils.createNullableBigDecimal(dto.getAvgAssetPrice()));

            clearInvocations(recordDao);
        } else {
            assertThrows(expectedException, () -> recordService.update(dto));
        }
    }

    private void createAndAssertRecord(String date, String title, String price,
                                       String ps, String pg, String po, String pe, String pfcf, String dy, String fpe,
                                       String q, String pp, String targets,
                                       Class<? extends Exception> expectedException)
    {
        Company company = Generator.generateCompany();
        when(companyService.findEntity(company.getId())).thenReturn(company);

        RecordCreateDto dto = new RecordCreateDto();
        dto.setCompanyId(company.getId());
        dto.setDate(date);
        dto.setPrice(price);
        dto.setPriceToRevenues(ps);
        dto.setPriceToGrossProfit(pg);
        dto.setPriceToOperatingIncome(po);
        dto.setPriceToNetIncome(pe);
        dto.setPriceToFreeCashFlow(pfcf);
        dto.setDividendYield(dy);
        dto.setForwardPe(fpe);
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
            assertThat(captor.getValue().getTitle(), Matchers.nullValue());

            assertBigDecimals(captor.getValue().getPriceToRevenues(), Utils.createNullableBigDecimal(ps));
            assertBigDecimals(captor.getValue().getPriceToGrossProfit(), Utils.createNullableBigDecimal(pg));
            assertBigDecimals(captor.getValue().getPriceToOperatingIncome(), Utils.createNullableBigDecimal(po));
            assertBigDecimals(captor.getValue().getPriceToNetIncome(), Utils.createNullableBigDecimal(pe));
            assertBigDecimals(captor.getValue().getPriceToFreeCashFlow(), Utils.createNullableBigDecimal(pfcf));

            assertBigDecimals(captor.getValue().getDividendYield(), Utils.createNullableBigDecimal(dy));

            assertBigDecimals(captor.getValue().getForwardPe(), Utils.createNullableBigDecimal(fpe));

            assertBigDecimals(captor.getValue().getSumAssetQuantity(), Utils.createNullableBigDecimal(q));
            assertBigDecimals(captor.getValue().getAvgAssetPrice(), Utils.createNullableBigDecimal(pp));
            assertThat(captor.getValue().getTargets(), is(targets));

            clearInvocations(recordDao);
        } else {
            assertThrows(expectedException, () -> recordService.create(dto));
        }
    }
}
