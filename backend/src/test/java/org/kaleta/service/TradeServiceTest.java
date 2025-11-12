package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.kaleta.framework.Generator;
import org.kaleta.model.Asset;
import org.kaleta.model.Assets;
import org.kaleta.persistence.api.TradeDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Trade;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

@QuarkusTest
public class TradeServiceTest
{
    @InjectMock
    TradeDao tradeDao;
    @InjectMock
    CompanyService companyService;
    @InjectMock
    ConvertService convertService;
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
        assertThat(assets.getAssets().get(0).getQuantity(), comparesEqualTo(trade1.getQuantity()));
        assertThat(assets.getAssets().get(0).getPurchasePrice(), comparesEqualTo(trade1.getPurchasePrice()));
        assertThat(assets.getAssets().get(0).getCurrentPrice(), comparesEqualTo(currentPrice));
        assertThat(assets.getAssets().get(0).getProfitValue(), comparesEqualTo(new BigDecimal("6987.5")));
        assertThat(assets.getAssets().get(0).getProfitPercent(), comparesEqualTo(new BigDecimal("12.24")));
        assertThat(assets.getAssets().get(1).getQuantity(), comparesEqualTo(trade2.getQuantity()));
        assertThat(assets.getAssets().get(1).getPurchasePrice(), comparesEqualTo(trade2.getPurchasePrice()));
        assertThat(assets.getAssets().get(1).getCurrentPrice(), comparesEqualTo(currentPrice));
        assertThat(assets.getAssets().get(1).getProfitValue(), comparesEqualTo(new BigDecimal("-1023")));
        assertThat(assets.getAssets().get(1).getProfitPercent(), comparesEqualTo(new BigDecimal("-16.63")));

        assertThat(assets.getAggregate(), is(notNullValue()));
        assertThat(assets.getAggregate().getQuantity(), comparesEqualTo(new BigDecimal("1350")));
        assertThat(assets.getAggregate().getCurrentPrice(), comparesEqualTo(currentPrice));
        assertThat(assets.getAggregate().getPurchasePrice(), comparesEqualTo(new BigDecimal("46.85")));
        assertThat(assets.getAggregate().getProfitValue(), comparesEqualTo(new BigDecimal("5967")));
        assertThat(assets.getAggregate().getProfitPercent(), comparesEqualTo(new BigDecimal("9.43")));
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
        assertThat(assets.getAssets().get(0).getQuantity(), comparesEqualTo(trade1.getQuantity()));
        assertThat(assets.getAssets().get(0).getPurchasePrice(), comparesEqualTo(trade1.getPurchasePrice()));
        assertThat(assets.getAssets().get(0).getCurrentPrice(), is(nullValue()));
        assertThat(assets.getAssets().get(0).getProfitValue(), is(nullValue()));
        assertThat(assets.getAssets().get(0).getProfitPercent(), is(nullValue()));
        assertThat(assets.getAssets().get(1).getQuantity(), comparesEqualTo(trade2.getQuantity()));
        assertThat(assets.getAssets().get(1).getPurchasePrice(), comparesEqualTo(trade2.getPurchasePrice()));
        assertThat(assets.getAssets().get(1).getCurrentPrice(), is(nullValue()));
        assertThat(assets.getAssets().get(1).getProfitValue(), is(nullValue()));
        assertThat(assets.getAssets().get(1).getProfitPercent(), is(nullValue()));

        assertThat(assets.getAggregate(), is(notNullValue()));
        assertThat(assets.getAggregate().getQuantity(), comparesEqualTo(new BigDecimal("1350")));
        assertThat(assets.getAggregate().getPurchasePrice(), comparesEqualTo(new BigDecimal("46.85")));
        assertThat(assets.getAggregate().getCurrentPrice(), is(nullValue()));
        assertThat(assets.getAggregate().getProfitValue(), is(nullValue()));
        assertThat(assets.getAggregate().getProfitPercent(), is(nullValue()));
    }
}
