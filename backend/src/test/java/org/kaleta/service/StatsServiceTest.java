package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kaleta.model.CompanyStats;
import org.kaleta.model.Dividends;
import org.kaleta.model.PeriodFrequency;
import org.kaleta.model.PeriodStats;
import org.kaleta.model.Trades;
import org.kaleta.persistence.entity.Currency;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.kaleta.framework.Assert.assertBigDecimals;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@QuarkusTest
class StatsServiceTest
{
    @InjectMock
    TradeService tradeService;
    @InjectMock
    DividendService dividendService;

    @Inject
    StatsService statsService;

    @BeforeEach
    void beforeEach()
    {
        reset(tradeService, dividendService);
    }

    @Test
    void getByCompany()
    {
        org.kaleta.model.Company company1 = company("company-1", "NVDA", Currency.$);
        org.kaleta.model.Company company2 = company("company-2", "SHELL", Currency.€);

        when(tradeService.getByCompany(null, null, null, null)).thenReturn(Map.of(
                company1, List.of(
                        trade(company1, "2024-01-05", "100", "150"),
                        trade(company1, "2023-12-15", "50", "40")
                )
        ));
        when(dividendService.getByCompany(null, null, null)).thenReturn(Map.of(
                company1, List.of(dividend(company1, "2022-06-01", "10")),
                company2, List.of(dividend(company2, "2021-03-01", "90"))
        ));

        CompanyStats stats = statsService.getByCompany(null, null);

        assertThat(stats.getCompanies().size(), is(2));
        assertThat(stats.getYears(), hasItems("2024", "2023", "2022", "2021"));

        CompanyStats.Company nvda = findCompany(stats, "NVDA");
        assertThat(nvda.getCurrency(), is(Currency.$));
        assertBigDecimals(nvda.getPurchaseSum(), new BigDecimal("150"));
        assertBigDecimals(nvda.getSellSum(), new BigDecimal("190"));
        assertBigDecimals(nvda.getDividendSum(), new BigDecimal("10"));
        assertBigDecimals(nvda.getProfitSum(), new BigDecimal("50"));
        assertBigDecimals(nvda.getProfitUsdSum(), new BigDecimal("50"));
        assertBigDecimals(nvda.getProfitPercentage(), new BigDecimal("33.33"));

        CompanyStats.Company shell = findCompany(stats, "SHELL");
        assertThat(shell.getCurrency(), is(Currency.€));
        assertBigDecimals(shell.getPurchaseSum(), new BigDecimal("0"));
        assertBigDecimals(shell.getSellSum(), new BigDecimal("0"));
        assertBigDecimals(shell.getDividendSum(), new BigDecimal("90"));
        assertBigDecimals(shell.getProfitSum(), new BigDecimal("90"));
        assertBigDecimals(shell.getProfitUsdSum(), new BigDecimal("99.00"));
        assertThat(shell.getProfitPercentage(), is(nullValue()));

        assertThat(stats.getAggregates().getCompanies(), is(2));
        assertThat(stats.getAggregates().getCurrencies(), is(2));
        assertBigDecimals(stats.getAggregates().getPurchaseSum(), new BigDecimal("150"));
        assertBigDecimals(stats.getAggregates().getSellSum(), new BigDecimal("190"));
        assertBigDecimals(stats.getAggregates().getDividendSum(), new BigDecimal("100"));
        assertBigDecimals(stats.getAggregates().getProfitSum(), new BigDecimal("140"));
        assertBigDecimals(stats.getAggregates().getProfitSumUsd(), new BigDecimal("149.00"));
        assertBigDecimals(stats.getAggregates().getProfitPercentage(), new BigDecimal("93.33"));
    }

    @Test
    void getByPeriod()
    {
        org.kaleta.model.Company company = company("company-1", "NVDA", Currency.$);

        when(tradeService.getByPeriod(PeriodFrequency.MONTHLY, company.getId(), null, null)).thenReturn(Map.of(
                "2024-01", List.of(trade(company, "2024-01-05", "100", "120")),
                "2024-02", List.<Trades.Trade>of(),
                "2024-03", List.<Trades.Trade>of()
        ));
        when(dividendService.getByPeriod(PeriodFrequency.MONTHLY, company.getId(), null, null)).thenReturn(Map.of(
                "2024-01", List.<Dividends.Dividend>of(),
                "2024-02", List.of(dividend(company, "2024-02-20", "5")),
                "2024-03", List.<Dividends.Dividend>of()
        ));

        PeriodStats stats = statsService.getByPeriod(PeriodFrequency.MONTHLY, company.getId(), null);

        assertThat(stats.getPeriods().size(), is(3));
        assertThat(stats.getPeriods().get(0).getPeriod(), is("2024-03"));
        assertThat(stats.getPeriods().get(1).getPeriod(), is("2024-02"));
        assertThat(stats.getPeriods().get(2).getPeriod(), is("2024-01"));

        PeriodStats.Period january = findPeriod(stats, "2024-01");
        assertThat(january.getTradesCount(), is(1));
        assertBigDecimals(january.getTradesPurchaseSum(), new BigDecimal("100"));
        assertBigDecimals(january.getTradesSellSum(), new BigDecimal("120"));
        assertBigDecimals(january.getTradesProfitSum(), new BigDecimal("20"));
        assertBigDecimals(january.getTradesProfitPercentage(), new BigDecimal("20"));
        assertBigDecimals(january.getDividendSum(), new BigDecimal("0"));

        PeriodStats.Period february = findPeriod(stats, "2024-02");
        assertThat(february.getTradesCount(), is(0));
        assertBigDecimals(february.getTradesPurchaseSum(), new BigDecimal("0"));
        assertBigDecimals(february.getTradesSellSum(), new BigDecimal("0"));
        assertBigDecimals(february.getTradesProfitSum(), new BigDecimal("0"));
        assertThat(february.getTradesProfitPercentage(), is(nullValue()));
        assertBigDecimals(february.getDividendSum(), new BigDecimal("5"));

        PeriodStats.Period march = findPeriod(stats, "2024-03");
        assertThat(march.getTradesCount(), is(0));
        assertBigDecimals(march.getTradesPurchaseSum(), new BigDecimal("0"));
        assertBigDecimals(march.getTradesSellSum(), new BigDecimal("0"));
        assertBigDecimals(march.getTradesProfitSum(), new BigDecimal("0"));
        assertThat(march.getTradesProfitPercentage(), is(nullValue()));
        assertBigDecimals(march.getDividendSum(), new BigDecimal("0"));

        assertThat(stats.getAggregates().getPeriods(), is(3));
        assertThat(stats.getAggregates().getTradesCount(), is(1));
        assertBigDecimals(stats.getAggregates().getTradesProfitSum(), new BigDecimal("20"));
        assertBigDecimals(stats.getAggregates().getTradesProfitPercentage(), new BigDecimal("20"));
        assertBigDecimals(stats.getAggregates().getDividendSum(), new BigDecimal("5"));
    }

    @Test
    void getByCompany_onlyDividends_keepsProfitPercentagesNull()
    {
        org.kaleta.model.Company company1 = company("company-1", "NVDA", Currency.$);
        org.kaleta.model.Company company2 = company("company-2", "SHELL", Currency.€);

        when(tradeService.getByCompany(null, null, null, null)).thenReturn(Map.of());
        when(dividendService.getByCompany(null, null, null)).thenReturn(Map.of(
                company1, List.of(dividend(company1, "2022-06-01", "10")),
                company2, List.of(dividend(company2, "2021-03-01", "90"))
        ));

        CompanyStats stats = statsService.getByCompany(null, null);

        CompanyStats.Company nvda = findCompany(stats, "NVDA");
        assertThat(nvda.getProfitPercentage(), is(nullValue()));

        CompanyStats.Company shell = findCompany(stats, "SHELL");
        assertThat(shell.getProfitPercentage(), is(nullValue()));

        assertBigDecimals(stats.getAggregates().getPurchaseSum(), BigDecimal.ZERO);
        assertBigDecimals(stats.getAggregates().getSellSum(), BigDecimal.ZERO);
        assertBigDecimals(stats.getAggregates().getDividendSum(), new BigDecimal("100"));
        assertBigDecimals(stats.getAggregates().getProfitSum(), new BigDecimal("100"));
        assertBigDecimals(stats.getAggregates().getProfitSumUsd(), new BigDecimal("109.00"));
        assertThat(stats.getAggregates().getProfitPercentage(), is(nullValue()));
    }

    private static org.kaleta.model.Company company(String id, String ticker, Currency currency)
    {
        org.kaleta.model.Company company = new org.kaleta.model.Company();
        company.setId(id);
        company.setTicker(ticker);
        company.setCurrency(currency);
        return company;
    }

    private static Trades.Trade trade(org.kaleta.model.Company company, String sellDate, String purchaseTotal, String sellTotal)
    {
        Trades.Trade trade = new Trades.Trade();
        trade.setCompany(company);
        trade.setSellDate(Date.valueOf(sellDate));
        trade.setPurchaseTotal(new BigDecimal(purchaseTotal));
        trade.setSellTotal(new BigDecimal(sellTotal));
        return trade;
    }

    private static Dividends.Dividend dividend(org.kaleta.model.Company company, String date, String net)
    {
        Dividends.Dividend dividend = new Dividends.Dividend();
        dividend.setCompany(company);
        dividend.setDate(Date.valueOf(date));
        dividend.setNet(new BigDecimal(net));
        return dividend;
    }

    private static CompanyStats.Company findCompany(CompanyStats stats, String ticker)
    {
        return stats.getCompanies().stream()
                .filter(company -> company.getTicker().equals(ticker))
                .findFirst()
                .orElseThrow();
    }

    private static PeriodStats.Period findPeriod(PeriodStats stats, String period)
    {
        return stats.getPeriods().stream()
                .filter(value -> value.getPeriod().equals(period))
                .findFirst()
                .orElseThrow();
    }
}
