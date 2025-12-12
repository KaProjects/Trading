package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.Test;
import org.kaleta.rest.dto.TradeCreateDto;
import org.kaleta.rest.dto.TradeSellDto;
import org.kaleta.framework.Generator;
import org.kaleta.model.Assets;
import org.kaleta.persistence.api.TradeDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Trade;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kaleta.framework.Assert.assertBigDecimals;
import static org.kaleta.framework.InvalidValues.invalidBigDecimals;
import static org.kaleta.framework.InvalidValues.invalidDates;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class TradeServiceTest
{
    @InjectMock
    TradeDao tradeDao;
    @InjectMock
    CompanyService companyService;
    @Inject
    TradeService tradeService;

    @Test
    public void getAssets()
    {
        Company company = Generator.generateCompany();
        Trade trade1 = new Trade();
        trade1.setQuantity(new BigDecimal("1250"));
        trade1.setPurchasePrice(new BigDecimal("45.68"));
        Trade trade2 = new Trade();
        trade2.setQuantity(new BigDecimal("100"));
        trade2.setPurchasePrice(new BigDecimal("61.5"));

        when(tradeDao.list(true, company.getId(), null, null, null, null)).thenReturn(List.of(trade1, trade2));

        BigDecimal currentPrice = new BigDecimal("51.27");
        Assets assets = tradeService.getAssets(company.getId(), currentPrice);

        assertThat(assets.getAssets().size(), is(2));
        assertBigDecimals(assets.getAssets().get(0).getQuantity(), trade1.getQuantity());
        assertBigDecimals(assets.getAssets().get(0).getPurchasePrice(), trade1.getPurchasePrice());
        assertBigDecimals(assets.getAssets().get(0).getCurrentPrice(), currentPrice);
        assertBigDecimals(assets.getAssets().get(0).getProfitValue(), new BigDecimal("6987.5"));
        assertBigDecimals(assets.getAssets().get(0).getProfitPercent(), new BigDecimal("12.24"));
        assertBigDecimals(assets.getAssets().get(1).getQuantity(), trade2.getQuantity());
        assertBigDecimals(assets.getAssets().get(1).getPurchasePrice(), trade2.getPurchasePrice());
        assertBigDecimals(assets.getAssets().get(1).getCurrentPrice(), currentPrice);
        assertBigDecimals(assets.getAssets().get(1).getProfitValue(), new BigDecimal("-1023"));
        assertBigDecimals(assets.getAssets().get(1).getProfitPercent(), new BigDecimal("-16.63"));

        assertThat(assets.getAggregate(), is(notNullValue()));
        assertBigDecimals(assets.getAggregate().getQuantity(), new BigDecimal("1350"));
        assertBigDecimals(assets.getAggregate().getCurrentPrice(), currentPrice);
        assertBigDecimals(assets.getAggregate().getPurchasePrice(), new BigDecimal("46.85"));
        assertBigDecimals(assets.getAggregate().getProfitValue(), new BigDecimal("5967"));
        assertBigDecimals(assets.getAggregate().getProfitPercent(), new BigDecimal("9.43"));
    }

    @Test
    public void getAssets_noTrades()
    {
        Company company = Generator.generateCompany();

        when(tradeDao.list(true, company.getId(), null, null, null, null)).thenReturn(new ArrayList<>());

        BigDecimal currentPrice = new BigDecimal("51.27");
        Assets assets = tradeService.getAssets(company.getId(), currentPrice);
        assertThat(assets.getAssets().size(), is(0));
        assertThat(assets.getAggregate(), is(nullValue()));
    }

    @Test
    public void getAssets_noCurrentPrice()
    {
        Company company = Generator.generateCompany();
        Trade trade1 = new Trade();
        trade1.setQuantity(new BigDecimal("1250"));
        trade1.setPurchasePrice(new BigDecimal("45.68"));
        Trade trade2 = new Trade();
        trade2.setQuantity(new BigDecimal("100"));
        trade2.setPurchasePrice(new BigDecimal("61.5"));

        when(tradeDao.list(true, company.getId(), null, null, null, null)).thenReturn(List.of(trade1, trade2));

        Assets assets = tradeService.getAssets(company.getId(), null);

        assertThat(assets.getAssets().size(), is(2));
        assertBigDecimals(assets.getAssets().get(0).getQuantity(), trade1.getQuantity());
        assertBigDecimals(assets.getAssets().get(0).getPurchasePrice(), trade1.getPurchasePrice());
        assertThat(assets.getAssets().get(0).getCurrentPrice(), is(nullValue()));
        assertThat(assets.getAssets().get(0).getProfitValue(), is(nullValue()));
        assertThat(assets.getAssets().get(0).getProfitPercent(), is(nullValue()));
        assertBigDecimals(assets.getAssets().get(1).getQuantity(), trade2.getQuantity());
        assertBigDecimals(assets.getAssets().get(1).getPurchasePrice(), trade2.getPurchasePrice());
        assertThat(assets.getAssets().get(1).getCurrentPrice(), is(nullValue()));
        assertThat(assets.getAssets().get(1).getProfitValue(), is(nullValue()));
        assertThat(assets.getAssets().get(1).getProfitPercent(), is(nullValue()));

        assertThat(assets.getAggregate(), is(notNullValue()));
        assertBigDecimals(assets.getAggregate().getQuantity(), new BigDecimal("1350"));
        assertBigDecimals(assets.getAggregate().getPurchasePrice(), new BigDecimal("46.85"));
        assertThat(assets.getAggregate().getCurrentPrice(), is(nullValue()));
        assertThat(assets.getAggregate().getProfitValue(), is(nullValue()));
        assertThat(assets.getAggregate().getProfitPercent(), is(nullValue()));
    }

    @Test
    void create()
    {
        String validDate = "2025-02-03";
        String validPrice = "1234";
        String validQ = "45";
        String validFees = "5.5";

        createAndAssertTrade(validDate, validPrice, validQ, validFees, null);

        createAndAssertTrade(validDate, validPrice, validQ, null, NullPointerException.class);
        invalidBigDecimals().forEach(ibd -> createAndAssertTrade(validDate, validPrice, validQ, ibd, IllegalArgumentException.class));

        createAndAssertTrade(validDate, validPrice, null, validFees, NullPointerException.class);
        invalidBigDecimals().forEach(ibd -> createAndAssertTrade(validDate, validPrice, ibd, validFees, IllegalArgumentException.class));

        createAndAssertTrade(validDate, null, validQ, validFees, NullPointerException.class);
        invalidBigDecimals().forEach(ibd -> createAndAssertTrade(validDate, ibd, validQ, validFees, IllegalArgumentException.class));

        createAndAssertTrade(null, validPrice, validQ, validFees, IllegalArgumentException.class);
        invalidDates().forEach(date -> createAndAssertTrade(date, validPrice, validQ, validFees, IllegalArgumentException.class));
    }

    @Test
    void sell()
    {
        String validDate = "2027-07-24";
        String validPrice = "1000";
        String validFees = "50";

        Company company =  Generator.generateCompany();
        when(companyService.getCompany(company.getId())).thenReturn(company);
        doThrow(new ServiceFailureException("")).when(companyService).getCompany("a9f86e1e-b81d-4b28-b4f3-91d25dfb6b43");

        Trade validTrade = Generator.generateTrade(company, new BigDecimal(5), false);
        List<TradeSellDto.Trade> validDtoTrades =  new ArrayList<>(List.of(new TradeSellDto.Trade(validTrade.getId(), "5")));
        Trade expectedTrade = sell(validTrade, validDate, validPrice, validFees);

        sellAndAssertTrade(company.getId(), validDate, validPrice, validFees, new ArrayList<>(), List.of(copy(validTrade)), List.of(copy(expectedTrade)), IllegalArgumentException.class);

        sellAndAssertTrade("a9f86e1e-b81d-4b28-b4f3-91d25dfb6b43", validDate, validPrice, validFees, validDtoTrades, List.of(copy(validTrade)), List.of(copy(expectedTrade)), ServiceFailureException.class);

        sellAndAssertTrade(company.getId(), validDate, validPrice, validFees, validDtoTrades, List.of(copy(validTrade)), List.of(copy(expectedTrade)), null);

        sellAndAssertTrade(company.getId(), validDate, validPrice, null, validDtoTrades, List.of(copy(validTrade)), List.of(copy(expectedTrade)), NullPointerException.class);
        invalidBigDecimals().forEach(ibd -> sellAndAssertTrade(company.getId(), validDate, validPrice, ibd, validDtoTrades, List.of(copy(validTrade)), List.of(copy(expectedTrade)), IllegalArgumentException.class));

        sellAndAssertTrade(company.getId(), validDate, null, validFees, validDtoTrades, List.of(copy(validTrade)), List.of(copy(expectedTrade)), NullPointerException.class);
        invalidBigDecimals().forEach(ibd -> sellAndAssertTrade(company.getId(), validDate, ibd, validFees, validDtoTrades, List.of(copy(validTrade)), List.of(copy(expectedTrade)), IllegalArgumentException.class));

        sellAndAssertTrade(company.getId(), null, validPrice, validFees, validDtoTrades, List.of(copy(validTrade)), List.of(copy(expectedTrade)), IllegalArgumentException.class);
        invalidDates().forEach(date -> sellAndAssertTrade(company.getId(), "", validPrice, validFees, validDtoTrades, List.of(copy(validTrade)), List.of(copy(expectedTrade)), IllegalArgumentException.class));

        sellAndAssertTrade(company.getId(), validDate, validPrice, validFees, validDtoTrades, List.of(copy(validTrade)), List.of(copy(expectedTrade)), null);

        // higher than trade quantity
        validDtoTrades.get(0).setQuantity("7");
        sellAndAssertTrade(company.getId(), validDate, validPrice, validFees, validDtoTrades, List.of(copy(validTrade)), List.of(copy(expectedTrade)), ServiceFailureException.class);

        // lesser than trade quantity
        validDtoTrades.get(0).setQuantity("3");
        Trade soldTrade = sell(validTrade, validDate, validPrice, validFees);
        soldTrade.setQuantity(new BigDecimal("3"));
        soldTrade.setPurchaseFees(validTrade.getPurchaseFees().multiply(new BigDecimal("3")).divide(new BigDecimal("5"), 2, RoundingMode.HALF_UP));
        Trade residualTrade = copy(validTrade);
        residualTrade.setQuantity(new BigDecimal("2"));
        residualTrade.setPurchaseFees(validTrade.getPurchaseFees().multiply(new BigDecimal("2")).divide(new BigDecimal("5"), 2, RoundingMode.HALF_UP));
        sellAndAssertTrade(company.getId(), validDate, validPrice, validFees, validDtoTrades, List.of(copy(validTrade)), List.of(soldTrade, residualTrade), null);

        // 2 trades = split fees
        Trade validTrade2 = Generator.generateTrade(company, new BigDecimal(3), false);
        validDtoTrades.get(0).setQuantity("5");
        validDtoTrades.add(new TradeSellDto.Trade(validTrade2.getId(), "3"));
        Trade soldTrade1 = sell(validTrade, validDate, validPrice, String.valueOf(new BigDecimal(validFees).multiply(new BigDecimal("5")).divide(new BigDecimal("8"), 2, RoundingMode.HALF_UP)));
        Trade soldTrade2 = sell(validTrade2, validDate, validPrice, String.valueOf(new BigDecimal(validFees).multiply(new BigDecimal("3")).divide(new BigDecimal("8"), 2, RoundingMode.HALF_UP)));
        sellAndAssertTrade(company.getId(), validDate, validPrice, validFees, validDtoTrades, List.of(copy(validTrade), copy(validTrade2)), List.of(soldTrade1, soldTrade2), null);

        // nonexistent trade
        validDtoTrades.get(0).setTradeId("d7f1c3c8-4d7e-4558-9b3e-0b1fc6df3a43");
        sellAndAssertTrade(company.getId(), validDate, validPrice, validFees, validDtoTrades, List.of(copy(validTrade), copy(validTrade2)), List.of(copy(expectedTrade)), ServiceFailureException.class);

        // attempt to sell from different company
        Trade malformed = copy(validTrade);
        malformed.setId(UUID.randomUUID().toString());
        sellAndAssertTrade(company.getId(), validDate, validPrice, validFees, validDtoTrades, List.of(malformed), List.of(copy(expectedTrade)), ServiceFailureException.class);
    }

    private Trade copy(Trade origin)
    {
        Trade copy = new Trade();
        copy.setId(origin.getId());
        copy.setCompany(origin.getCompany());
        copy.setQuantity(origin.getQuantity());
        copy.setPurchaseDate(origin.getPurchaseDate());
        copy.setPurchasePrice(origin.getPurchasePrice());
        copy.setPurchaseFees(origin.getPurchaseFees());
        copy.setSellDate(origin.getSellDate());
        copy.setSellPrice(origin.getSellPrice());
        copy.setSellFees(origin.getSellFees());
        return copy;
    }

    private Trade sell(Trade active, String date, String price, String fees)
    {
        Trade sold = copy(active);
        sold.setSellDate(Date.valueOf(date));
        sold.setSellPrice(new BigDecimal(price));
        sold.setSellFees(new BigDecimal(fees));
        return sold;
    }

    private void createAndAssertTrade(String date, String price, String q, String fees, Class<? extends Exception> expectedException)
    {
        Company company = Generator.generateCompany();
        when(companyService.getCompany(company.getId())).thenReturn(company);

        TradeCreateDto dto = new TradeCreateDto();
        dto.setCompanyId(company.getId());
        dto.setDate(date);
        dto.setPrice(price);
        dto.setQuantity(q);
        dto.setFees(fees);

        if (expectedException == null) {
            tradeService.createTrade(dto);

            ArgumentCaptor<Trade> captor = ArgumentCaptor.forClass(Trade.class);
            verify(tradeDao).create(captor.capture());

            assertThat(captor.getValue().getCompany().getId(), is(company.getId()));

            assertBigDecimals(captor.getValue().getQuantity(), new BigDecimal(q));
            assertThat(captor.getValue().getPurchaseDate(), is(Date.valueOf(date)));
            assertBigDecimals(captor.getValue().getPurchasePrice(), new BigDecimal(price));
            assertBigDecimals(captor.getValue().getPurchaseFees(), new BigDecimal(fees));

            assertThat(captor.getValue().getSellDate(), is(nullValue()));
            assertThat(captor.getValue().getSellPrice(), is(nullValue()));
            assertThat(captor.getValue().getSellFees(), is(nullValue()));

            clearInvocations(tradeDao);
        } else {
            assertThrows(expectedException, () -> tradeService.createTrade(dto));
        }
    }

    private void sellAndAssertTrade(String cid, String date, String price, String fees,
                                    List<TradeSellDto.Trade> dtoTrades,
                                    List<Trade> initTrades,
                                    List<Trade> expectedTrades,
                                    Class<? extends Exception> expectedException)
    {
        TradeSellDto dto = new TradeSellDto();
        dto.setCompanyId(cid);
        dto.setDate(date);
        dto.setPrice(price);
        dto.setFees(fees);
        dto.setTrades(dtoTrades);

        initTrades.forEach(trade ->  when(tradeDao.get(trade.getId())).thenReturn(trade));
        doThrow(new NoResultException()).when(tradeDao).get("d7f1c3c8-4d7e-4558-9b3e-0b1fc6df3a43");

        if (expectedException == null) {
            tradeService.sellTrade(dto);

            ArgumentCaptor<List<Trade>> captor = ArgumentCaptor.forClass(List.class);
            verify(tradeDao).saveAll(captor.capture());

            assertThat(captor.getValue().size(), is(expectedTrades.size()));

            for (int i=0; i<captor.getValue().size(); i++)
            {
                assertTrade(captor.getValue().get(i), expectedTrades.get(i), i < initTrades.size());
            }

            clearInvocations(tradeDao);
        } else {
            assertThrows(expectedException, () -> tradeService.sellTrade(dto));
        }
    }

    private static void assertTrade(Trade actual, Trade expected, boolean assertId)
    {
        if (assertId) assertThat(actual.getId(), is(expected.getId()));
        assertBigDecimals(actual.getQuantity(), expected.getQuantity());
        assertThat(actual.getPurchaseDate(), is(expected.getPurchaseDate()));
        assertBigDecimals(actual.getPurchasePrice(), expected.getPurchasePrice());
        assertBigDecimals(actual.getPurchaseFees(), expected.getPurchaseFees());
        assertThat(actual.getSellDate(), is(expected.getSellDate()));
        assertBigDecimals(actual.getSellPrice(), expected.getSellPrice());
        assertBigDecimals(actual.getSellFees(), expected.getSellFees());
    }
}
