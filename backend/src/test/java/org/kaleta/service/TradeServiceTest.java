package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.Test;
import org.kaleta.framework.Generator;
import org.kaleta.model.Assets;
import org.kaleta.model.PeriodFrequency;
import org.kaleta.model.TradeSaleSummary;
import org.kaleta.model.Trades;
import org.kaleta.persistence.api.TradeDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Portfolio;
import org.kaleta.persistence.entity.Trade;
import org.kaleta.rest.dto.TradeCreateDto;
import org.kaleta.rest.dto.TradeSellDto;
import org.kaleta.rest.dto.TradeUpdateDto;
import org.kaleta.rest.error.InvalidInputException;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
    void getBy()
    {
        Company soldCompany = Generator.generateCompany();
        soldCompany.setTicker("NVDA");
        soldCompany.setCurrency(Currency.$);
        org.kaleta.model.Company soldModelCompany = new org.kaleta.model.Company();
        soldModelCompany.setId(soldCompany.getId());
        soldModelCompany.setTicker(soldCompany.getTicker());
        soldModelCompany.setCurrency(soldCompany.getCurrency());

        Company activeCompany = Generator.generateCompany();
        activeCompany.setTicker("SHELL");
        activeCompany.setCurrency(Currency.€);
        org.kaleta.model.Company activeModelCompany = new org.kaleta.model.Company();
        activeModelCompany.setId(activeCompany.getId());
        activeModelCompany.setTicker(activeCompany.getTicker());
        activeModelCompany.setCurrency(activeCompany.getCurrency());

        Trade soldTrade = new Trade();
        soldTrade.setId(11L);
        soldTrade.setCompany(soldCompany);
        soldTrade.setQuantity(new BigDecimal("5"));
        soldTrade.setPurchaseDate(Date.valueOf("2024-01-10"));
        soldTrade.setPurchasePrice(new BigDecimal("10.00"));
        soldTrade.setPurchaseFees(new BigDecimal("2.00"));
        soldTrade.setSellDate(Date.valueOf("2024-02-15"));
        soldTrade.setSellPrice(new BigDecimal("12.00"));
        soldTrade.setSellFees(new BigDecimal("1.00"));
        soldTrade.setPortfolio(Portfolio.PATRIA_MARGIN);

        Trade activeTrade = new Trade();
        activeTrade.setId(10L);
        activeTrade.setCompany(activeCompany);
        activeTrade.setQuantity(new BigDecimal("3"));
        activeTrade.setPurchaseDate(Date.valueOf("2025-03-20"));
        activeTrade.setPurchasePrice(new BigDecimal("20.00"));
        activeTrade.setPurchaseFees(new BigDecimal("3.00"));

        when(tradeDao.list(null, null, null, null, null, null)).thenReturn(List.of(soldTrade, activeTrade));
        when(companyService.from(soldCompany)).thenReturn(soldModelCompany);
        when(companyService.from(activeCompany)).thenReturn(activeModelCompany);

        Trades trades = tradeService.getBy(null, null, null, null, null, null);

        assertThat(trades.getTrades().size(), is(2));

        assertThat(trades.getTrades().get(0).getId(), is(10L));
        assertThat(trades.getTrades().get(0).isActive(), is(true));
        assertThat(trades.getTrades().get(0).getCompany().getTicker(), is("SHELL"));
        assertThat(trades.getTrades().get(0).getCompany().getCurrency(), is(Currency.€));
        assertThat(trades.getTrades().get(0).getPortfolio(), is(nullValue()));
        assertThat(trades.getTrades().get(0).getPurchaseDate().toString(), is("2025-03-20"));
        assertBigDecimals(trades.getTrades().get(0).getPurchaseQuantity(), new BigDecimal("3"));
        assertBigDecimals(trades.getTrades().get(0).getPurchasePrice(), new BigDecimal("20.00"));
        assertBigDecimals(trades.getTrades().get(0).getPurchaseFees(), new BigDecimal("3.00"));
        assertBigDecimals(trades.getTrades().get(0).getPurchaseTotal(), new BigDecimal("63.00"));
        assertThat(trades.getTrades().get(0).getSellDate(), is(nullValue()));
        assertThat(trades.getTrades().get(0).getSellQuantity(), is(nullValue()));
        assertThat(trades.getTrades().get(0).getSellPrice(), is(nullValue()));
        assertThat(trades.getTrades().get(0).getSellFees(), is(nullValue()));
        assertThat(trades.getTrades().get(0).getSellTotal(), is(nullValue()));
        assertThat(trades.getTrades().get(0).getProfit(), is(nullValue()));
        assertThat(trades.getTrades().get(0).getProfitPercentage(), is(nullValue()));

        assertThat(trades.getTrades().get(1).getId(), is(11L));
        assertThat(trades.getTrades().get(1).isActive(), is(false));
        assertThat(trades.getTrades().get(1).getCompany().getTicker(), is("NVDA"));
        assertThat(trades.getTrades().get(1).getCompany().getCurrency(), is(Currency.$));
        assertThat(trades.getTrades().get(1).getPortfolio().getKey(), is(Portfolio.PATRIA_MARGIN.toString()));
        assertThat(trades.getTrades().get(1).getPortfolio().getName(), is(Portfolio.PATRIA_MARGIN.getName()));
        assertThat(trades.getTrades().get(1).getPortfolio().getAbbreviation(), is("Pm"));
        assertThat(trades.getTrades().get(1).getPurchaseDate().toString(), is("2024-01-10"));
        assertBigDecimals(trades.getTrades().get(1).getPurchaseQuantity(), new BigDecimal("5"));
        assertBigDecimals(trades.getTrades().get(1).getPurchasePrice(), new BigDecimal("10.00"));
        assertBigDecimals(trades.getTrades().get(1).getPurchaseFees(), new BigDecimal("2.00"));
        assertBigDecimals(trades.getTrades().get(1).getPurchaseTotal(), new BigDecimal("52.00"));
        assertThat(trades.getTrades().get(1).getSellDate().toString(), is("2024-02-15"));
        assertBigDecimals(trades.getTrades().get(1).getSellQuantity(), new BigDecimal("5"));
        assertBigDecimals(trades.getTrades().get(1).getSellPrice(), new BigDecimal("12.00"));
        assertBigDecimals(trades.getTrades().get(1).getSellFees(), new BigDecimal("1.00"));
        assertBigDecimals(trades.getTrades().get(1).getSellTotal(), new BigDecimal("59.00"));
        assertBigDecimals(trades.getTrades().get(1).getProfit(), new BigDecimal("7.00"));
        assertBigDecimals(trades.getTrades().get(1).getProfitPercentage(), new BigDecimal("13.46"));

        assertThat(trades.getAggregates().getCompanies(), is(2));
        assertThat(trades.getAggregates().getCurrencies(), is(2));
        assertThat(trades.getAggregates().getPortfolios(), is(1));
        assertBigDecimals(trades.getAggregates().getPurchaseFees(), new BigDecimal("5.00"));
        assertBigDecimals(trades.getAggregates().getPurchaseTotal(), new BigDecimal("115.00"));
        assertBigDecimals(trades.getAggregates().getSellFees(), new BigDecimal("1.00"));
        assertBigDecimals(trades.getAggregates().getSellTotal(), new BigDecimal("59.00"));
        assertBigDecimals(trades.getAggregates().getProfit(), new BigDecimal("7.00"));
        assertBigDecimals(trades.getAggregates().getProfitPercentage(), new BigDecimal("13.46"));
    }

    @Test
    void getBy_activeTradesOnly()
    {
        Company company = Generator.generateCompany();
        company.setTicker("CEZ");
        company.setCurrency(Currency.K);
        org.kaleta.model.Company modelCompany = new org.kaleta.model.Company();
        modelCompany.setId(company.getId());
        modelCompany.setTicker(company.getTicker());
        modelCompany.setCurrency(company.getCurrency());

        Trade trade = new Trade();
        trade.setId(10L);
        trade.setCompany(company);
        trade.setQuantity(new BigDecimal("4"));
        trade.setPurchaseDate(Date.valueOf("2025-05-10"));
        trade.setPurchasePrice(new BigDecimal("15.00"));
        trade.setPurchaseFees(new BigDecimal("1.50"));

        when(tradeDao.list(true, company.getId(), company.getCurrency().name(), "2025", null, null)).thenReturn(List.of(trade));
        when(companyService.from(company)).thenReturn(modelCompany);

        Trades trades = tradeService.getBy(true, company.getId(), company.getCurrency().name(), "2025", null, null);

        assertThat(trades.getTrades().size(), is(1));
        assertThat(trades.getTrades().get(0).getCompany().getTicker(), is("CEZ"));
        assertBigDecimals(trades.getTrades().get(0).getPurchaseTotal(), new BigDecimal("61.50"));

        assertThat(trades.getAggregates().getCompanies(), is(1));
        assertThat(trades.getAggregates().getCurrencies(), is(1));
        assertBigDecimals(trades.getAggregates().getPurchaseFees(), new BigDecimal("1.50"));
        assertBigDecimals(trades.getAggregates().getPurchaseTotal(), new BigDecimal("61.50"));
        assertBigDecimals(trades.getAggregates().getSellFees(), new BigDecimal("0.00"));
        assertBigDecimals(trades.getAggregates().getSellTotal(), new BigDecimal("0.00"));
        assertThat(trades.getAggregates().getProfit(), is(nullValue()));
        assertThat(trades.getAggregates().getProfitPercentage(), is(nullValue()));
    }

    @Test
    void getBy_portfolio()
    {
        String portfolio = Portfolio.PATRIA_MARGIN.name();
        when(tradeDao.list(null, null, null, null, null, null, portfolio)).thenReturn(List.of());

        Trades trades = tradeService.getBy(null, null, null, null, null, null, portfolio);

        assertThat(trades.getTrades().isEmpty(), is(true));
        verify(tradeDao).list(null, null, null, null, null, null, portfolio);
    }

    @Test
    void getYears()
    {
        Company company = Generator.generateCompany(1L);

        Trade trade1 = soldTrade(1L, company, "2024-01-10", "10", "1", "2025-02-15", "12", "1", "5");
        Trade trade2 = soldTrade(2L, company, "2023-03-10", "10", "1", "2024-04-15", "12", "1", "5");
        Trade trade3 = new Trade();
        trade3.setId(3L);
        trade3.setCompany(company);
        trade3.setQuantity(new BigDecimal("1"));
        trade3.setPurchaseDate(Date.valueOf("2021-05-10"));
        trade3.setPurchasePrice(new BigDecimal("10"));
        trade3.setPurchaseFees(new BigDecimal("1"));

        when(tradeDao.list()).thenReturn(List.of(trade1, trade2, trade3));

        List<String> years = tradeService.getYears();

        assertThat(years, is(List.of("2025", "2024", "2023", "2021")));
    }

    @Test
    void getByCompany()
    {
        Company company1 = Generator.generateCompany(1L);
        company1.setTicker("NVDA");
        company1.setCurrency(Currency.$);
        org.kaleta.model.Company modelCompany1 = toModelCompany(company1);

        Company company2 = Generator.generateCompany(2L);
        company2.setTicker("SHELL");
        company2.setCurrency(Currency.€);
        org.kaleta.model.Company modelCompany2 = toModelCompany(company2);

        Trade trade1 = soldTrade(1L, company1, "2024-01-10", "10", "1", "2024-02-15", "12", "1", "5");
        Trade trade2 = soldTrade(2L, company1, "2023-11-10", "20", "2", "2024-01-15", "22", "2", "3");
        Trade trade3 = soldTrade(3L, company2, "2024-03-01", "30", "3", "2024-03-20", "40", "4", "2");

        when(tradeDao.list(false, null, null, null, null, null)).thenReturn(List.of(trade1, trade2, trade3));
        when(companyService.from(company1)).thenReturn(modelCompany1);
        when(companyService.from(company2)).thenReturn(modelCompany2);

        Map<org.kaleta.model.Company, List<Trades.Trade>> byCompany = tradeService.getByCompany(null, null, null, null);

        assertThat(byCompany.size(), is(2));
        assertThat(byCompany.get(modelCompany1).size(), is(2));
        assertThat(byCompany.get(modelCompany1).get(0).getId(), is(1L));
        assertThat(byCompany.get(modelCompany1).get(1).getId(), is(2L));
        assertThat(byCompany.get(modelCompany2).size(), is(1));
        assertThat(byCompany.get(modelCompany2).get(0).getId(), is(3L));
    }

    @Test
    void getByPeriod()
    {
        Company company = Generator.generateCompany(1L);
        company.setTicker("NVDA");
        company.setCurrency(Currency.$);
        org.kaleta.model.Company modelCompany = toModelCompany(company);

        Trade trade1 = soldTrade(1L, company, "2023-12-10", "10", "1", "2024-01-15", "12", "1", "5");
        Trade trade2 = soldTrade(2L, company, "2024-02-01", "20", "2", "2024-03-20", "25", "2", "3");

        when(tradeDao.list(false, company.getId(), null, null, null, null)).thenReturn(List.of(trade1, trade2));
        when(companyService.from(company)).thenReturn(modelCompany);

        Map<String, List<Trades.Trade>> byPeriod = tradeService.getByPeriod(PeriodFrequency.QUARTERLY, company.getId(), null, null);

        assertThat(byPeriod.size(), is(4));
        assertThat(byPeriod.get("2024-Q1").size(), is(2));
        assertThat(byPeriod.get("2024-Q2").isEmpty(), is(true));
        assertThat(byPeriod.get("2024-Q3").isEmpty(), is(true));
        assertThat(byPeriod.get("2024-Q4").isEmpty(), is(true));
        assertThat(
                byPeriod.get("2024-Q1").stream().map(Trades.Trade::getId).collect(Collectors.toList()),
                containsInAnyOrder(1L, 2L)
        );
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
        when(companyService.findEntity(company.getId())).thenReturn(company);
        doThrow(new InvalidInputException("")).when(companyService).findEntity(1916L);

        Trade validTrade = Generator.generateTrade(company, new BigDecimal(5), false);
        validTrade.setPortfolio(Portfolio.REVOLUT_CFD);
        List<TradeSellDto.Trade> validDtoTrades =  new ArrayList<>(List.of(new TradeSellDto.Trade(validTrade.getId(), "5")));
        Trade expectedTrade = sell(validTrade, validDate, validPrice, validFees);

        sellAndAssertTrade(company.getId(), validDate, validPrice, validFees, new ArrayList<>(), List.of(copy(validTrade)), List.of(copy(expectedTrade)), IllegalArgumentException.class);

        sellAndAssertTrade(1916L, validDate, validPrice, validFees, validDtoTrades, List.of(copy(validTrade)), List.of(copy(expectedTrade)), InvalidInputException.class);

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
        sellAndAssertTrade(company.getId(), validDate, validPrice, validFees, validDtoTrades, List.of(copy(validTrade)), List.of(copy(expectedTrade)), InvalidInputException.class);

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
        validDtoTrades.get(0).setTradeId(2133L);
        sellAndAssertTrade(company.getId(), validDate, validPrice, validFees, validDtoTrades, List.of(copy(validTrade), copy(validTrade2)), List.of(copy(expectedTrade)), InvalidInputException.class);

        // attempt to sell from different company
        Trade malformed = copy(validTrade);
        malformed.setId(4_294_967_295L);
        sellAndAssertTrade(company.getId(), validDate, validPrice, validFees, validDtoTrades, List.of(malformed), List.of(copy(expectedTrade)), InvalidInputException.class);
    }

    @Test
    void sell_returnsAggregateSummary()
    {
        Company company = Generator.generateCompany();
        when(companyService.findEntity(company.getId())).thenReturn(company);

        Trade firstTrade = trade(company, 101L, "0.1", "100", "1");
        Trade secondTrade = trade(company, 102L, "0.2", "200", "2");
        when(tradeDao.get(firstTrade.getId())).thenReturn(firstTrade);
        when(tradeDao.get(secondTrade.getId())).thenReturn(secondTrade);

        TradeSellDto dto = new TradeSellDto();
        dto.setCompanyId(company.getId());
        dto.setDate("2027-07-24");
        dto.setPrice("300");
        dto.setFees("3");
        dto.setTrades(List.of(
                new TradeSellDto.Trade(firstTrade.getId(), "0.1"),
                new TradeSellDto.Trade(secondTrade.getId(), "0.2")));

        TradeSaleSummary summary = tradeService.sellTrade(dto);

        assertBigDecimals(summary.quantity(), new BigDecimal("0.3"));
        assertBigDecimals(summary.averagePurchasePrice(), new BigDecimal("166.66667"));
        assertBigDecimals(summary.fees(), new BigDecimal("6"));
        assertBigDecimals(summary.profit(), new BigDecimal("34"));
        assertBigDecimals(summary.profitPercentage(), new BigDecimal("64.15"));
    }

    @Test
    void updateTrade_active()
    {
        Company company = Generator.generateCompany();
        Trade trade = trade(company, 101L, "5", "100", "2");
        trade.setPortfolio(Portfolio.PATRIA_STANDARD);
        when(tradeDao.get(trade.getId())).thenReturn(trade);

        TradeUpdateDto dto = updateDto();
        dto.setPortfolio(Portfolio.REVOLUT_STANDARD.toString());

        tradeService.updateTrade(trade.getId(), dto);

        verify(tradeDao).save(trade);
        assertThat(trade.getCompany(), is(company));
        assertThat(trade.getPurchaseDate(), is(Date.valueOf("2027-02-03")));
        assertBigDecimals(trade.getQuantity(), new BigDecimal("6.25"));
        assertBigDecimals(trade.getPurchasePrice(), new BigDecimal("110.5"));
        assertBigDecimals(trade.getPurchaseFees(), new BigDecimal("3.25"));
        assertThat(trade.getPortfolio(), is(Portfolio.REVOLUT_STANDARD));
        assertThat(trade.getSellDate(), is(nullValue()));
        assertThat(trade.getSellPrice(), is(nullValue()));
        assertThat(trade.getSellFees(), is(nullValue()));
    }

    @Test
    void updateTrade_sold()
    {
        Company company = Generator.generateCompany();
        Trade trade = sell(trade(company, 102L, "5", "100", "2"), "2027-03-01", "125", "3");
        trade.setPortfolio(Portfolio.PATRIA_STANDARD);
        when(tradeDao.get(trade.getId())).thenReturn(trade);

        TradeUpdateDto dto = updateDto();
        dto.setSellDate("2027-04-05");
        dto.setSellPrice("130.75");
        dto.setSellFees("4.5");
        dto.setPortfolio(null);

        tradeService.updateTrade(trade.getId(), dto);

        verify(tradeDao).save(trade);
        assertThat(trade.getCompany(), is(company));
        assertThat(trade.getPurchaseDate(), is(Date.valueOf("2027-02-03")));
        assertBigDecimals(trade.getQuantity(), new BigDecimal("6.25"));
        assertBigDecimals(trade.getPurchasePrice(), new BigDecimal("110.5"));
        assertBigDecimals(trade.getPurchaseFees(), new BigDecimal("3.25"));
        assertThat(trade.getPortfolio(), is(nullValue()));
        assertThat(trade.getSellDate(), is(Date.valueOf("2027-04-05")));
        assertBigDecimals(trade.getSellPrice(), new BigDecimal("130.75"));
        assertBigDecimals(trade.getSellFees(), new BigDecimal("4.5"));
    }

    @Test
    void updateTrade_invalidState()
    {
        Company company = Generator.generateCompany();
        Trade activeTrade = trade(company, 103L, "5", "100", "2");
        Trade soldTrade = sell(trade(company, 104L, "5", "100", "2"), "2027-03-01", "125", "3");
        when(tradeDao.get(activeTrade.getId())).thenReturn(activeTrade);
        when(tradeDao.get(soldTrade.getId())).thenReturn(soldTrade);
        doThrow(new NoResultException()).when(tradeDao).get(105L);

        TradeUpdateDto activeDto = updateDto();
        activeDto.setSellDate("2027-04-05");
        activeDto.setSellPrice("130.75");
        activeDto.setSellFees("4.5");
        InvalidInputException activeException = assertThrows(
                InvalidInputException.class,
                () -> tradeService.updateTrade(activeTrade.getId(), activeDto));
        assertThat(activeException.getMessage(), is("sale fields cannot be provided for active trade"));

        TradeUpdateDto soldDto = updateDto();
        InvalidInputException soldException = assertThrows(
                InvalidInputException.class,
                () -> tradeService.updateTrade(soldTrade.getId(), soldDto));
        assertThat(soldException.getMessage(), is("sellDate, sellPrice and sellFees are required for sold trade"));

        soldDto.setSellDate("2027-01-01");
        soldDto.setSellPrice("130.75");
        soldDto.setSellFees("4.5");
        InvalidInputException dateException = assertThrows(
                InvalidInputException.class,
                () -> tradeService.updateTrade(soldTrade.getId(), soldDto));
        assertThat(dateException.getMessage(), is("sellDate cannot be before purchaseDate"));

        InvalidInputException missingException = assertThrows(
                InvalidInputException.class,
                () -> tradeService.updateTrade(105L, updateDto()));
        assertThat(missingException.getMessage(), is("trade with id '105' not found"));
    }

    private TradeUpdateDto updateDto()
    {
        TradeUpdateDto dto = new TradeUpdateDto();
        dto.setPurchaseDate("2027-02-03");
        dto.setQuantity("6.25");
        dto.setPurchasePrice("110.5");
        dto.setPurchaseFees("3.25");
        return dto;
    }

    private Trade trade(Company company, Long id, String quantity, String price, String fees)
    {
        Trade trade = new Trade();
        trade.setId(id);
        trade.setCompany(company);
        trade.setQuantity(new BigDecimal(quantity));
        trade.setPurchaseDate(Date.valueOf("2027-01-01"));
        trade.setPurchasePrice(new BigDecimal(price));
        trade.setPurchaseFees(new BigDecimal(fees));
        return trade;
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
        copy.setPortfolio(origin.getPortfolio());
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
        when(companyService.findEntity(company.getId())).thenReturn(company);

        TradeCreateDto dto = new TradeCreateDto();
        dto.setCompanyId(company.getId());
        dto.setDate(date);
        dto.setPrice(price);
        dto.setQuantity(q);
        dto.setFees(fees);
        dto.setPortfolio(Portfolio.PATRIA_STANDARD.toString());

        if (expectedException == null) {
            tradeService.createTrade(dto);

            ArgumentCaptor<Trade> captor = ArgumentCaptor.forClass(Trade.class);
            verify(tradeDao).create(captor.capture());

            assertThat(captor.getValue().getCompany().getId(), is(company.getId()));

            assertBigDecimals(captor.getValue().getQuantity(), new BigDecimal(q));
            assertThat(captor.getValue().getPurchaseDate(), is(Date.valueOf(date)));
            assertBigDecimals(captor.getValue().getPurchasePrice(), new BigDecimal(price));
            assertBigDecimals(captor.getValue().getPurchaseFees(), new BigDecimal(fees));
            assertThat(captor.getValue().getPortfolio(), is(Portfolio.PATRIA_STANDARD));

            assertThat(captor.getValue().getSellDate(), is(nullValue()));
            assertThat(captor.getValue().getSellPrice(), is(nullValue()));
            assertThat(captor.getValue().getSellFees(), is(nullValue()));

            clearInvocations(tradeDao);
        } else {
            assertThrows(expectedException, () -> tradeService.createTrade(dto));
        }
    }

    private void sellAndAssertTrade(Long cid, String date, String price, String fees,
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
        doThrow(new NoResultException()).when(tradeDao).get(2133L);

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
        assertThat(actual.getPortfolio(), is(expected.getPortfolio()));
        assertThat(actual.getSellDate(), is(expected.getSellDate()));
        assertBigDecimals(actual.getSellPrice(), expected.getSellPrice());
        assertBigDecimals(actual.getSellFees(), expected.getSellFees());
    }

    private static org.kaleta.model.Company toModelCompany(Company entity)
    {
        org.kaleta.model.Company company = new org.kaleta.model.Company();
        company.setId(entity.getId());
        company.setTicker(entity.getTicker());
        company.setCurrency(entity.getCurrency());
        return company;
    }

    private static Trade soldTrade(Long id, Company company,
                                   String purchaseDate, String purchasePrice, String purchaseFees,
                                   String sellDate, String sellPrice, String sellFees,
                                   String quantity)
    {
        Trade trade = new Trade();
        trade.setId(id);
        trade.setCompany(company);
        trade.setPurchaseDate(Date.valueOf(purchaseDate));
        trade.setPurchasePrice(new BigDecimal(purchasePrice));
        trade.setPurchaseFees(new BigDecimal(purchaseFees));
        trade.setSellDate(Date.valueOf(sellDate));
        trade.setSellPrice(new BigDecimal(sellPrice));
        trade.setSellFees(new BigDecimal(sellFees));
        trade.setQuantity(new BigDecimal(quantity));
        return trade;
    }
}
