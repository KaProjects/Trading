package org.kaleta.service;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.kaleta.persistence.api.RecordDao;
import org.kaleta.persistence.api.TradeDao;
import org.kaleta.persistence.entity.Portfolio;
import org.kaleta.persistence.entity.Trade;
import org.kaleta.rest.dto.TradeImportDto;
import org.kaleta.rest.dto.TradeImportPreviewDto;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kaleta.framework.Assert.assertBigDecimals;

@QuarkusTest
class TradeImportPersistenceTest
{
    @Inject
    TradeImportService tradeImportService;
    @Inject
    TradeDao tradeDao;
    @Inject
    RecordDao recordDao;

    @Test
    @TestTransaction
    void importsBuyThenPartialSaleAndRollsBackAfterTest()
    {
        Long companyId = 2287L;
        int initialRecords = recordDao.list(companyId).size();
        TradeImportDto dto = new TradeImportDto();
        dto.setRows(List.of(
                row(2, "2026-01-10", "BUY", "2", "100", "4"),
                row(3, "2026-02-10", "SELL", "1.5", "120", "2")));

        TradeImportPreviewDto result = tradeImportService.importTrades(dto);

        assertThat(result.isValid(), is(true));
        List<Trade> trades = tradeDao.list(companyId).stream()
                .sorted(Comparator.comparing(trade -> trade.getSellDate() == null))
                .toList();
        assertThat(trades.size(), is(2));

        Trade sold = trades.getFirst();
        assertBigDecimals(sold.getQuantity(), new BigDecimal("1.5"));
        assertBigDecimals(sold.getPurchaseFees(), new BigDecimal("3.00"));
        assertBigDecimals(sold.getSellFees(), new BigDecimal("2.00"));

        Trade residual = trades.getLast();
        assertBigDecimals(residual.getQuantity(), new BigDecimal("0.5"));
        assertBigDecimals(residual.getPurchaseFees(), new BigDecimal("1.00"));
        assertThat(residual.getSellDate(), is((java.sql.Date) null));
        assertThat(recordDao.list(companyId).size(), is(initialRecords + 2));
    }

    private TradeImportDto.Row row(int rowNumber, String date, String type, String quantity,
                                   String price, String fees)
    {
        TradeImportDto.Row row = new TradeImportDto.Row();
        row.setRowNumber(rowNumber);
        row.setDate(date);
        row.setType(type);
        row.setTicker("CINV");
        row.setQuantity(quantity);
        row.setPrice(price);
        row.setFees(fees);
        row.setPortfolio(Portfolio.PATRIA_STANDARD.name());
        return row;
    }
}
