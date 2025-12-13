package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.Utils;
import org.kaleta.rest.dto.TradeCreateDto;
import org.kaleta.rest.dto.TradeSellDto;
import org.kaleta.model.Asset;
import org.kaleta.model.Assets;
import org.kaleta.persistence.api.TradeDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.kaleta.Utils.format;

@ApplicationScoped
public class TradeService
{
    @Inject
    TradeDao tradeDao;
    @Inject
    CompanyService companyService;
    @Inject
    ArithmeticService arithmeticService;

    public List<Trade> getTrades(Boolean active, String company, String currency, String purchaseYear, String sellYear, String sector)
    {
        return tradeDao.list(active, company, currency, purchaseYear, sellYear, sector);
    }

    public String[] computeSums(List<Trade> trades)
    {
        Set<String> companies = new HashSet<>();
        Set<Currency> currencies = new HashSet<>();
        BigDecimal purchaseFeesSum = new BigDecimal("0.00");
        BigDecimal purchaseTotalSum = new BigDecimal("0.00");
        BigDecimal purchaseTotalSumSold = new BigDecimal("0.00");
        BigDecimal sellFeesSum = new BigDecimal("0.00");
        BigDecimal sellTotalSum = new BigDecimal("0.00");
        for (Trade trade : trades)
        {
            companies.add(trade.getTicker());
            currencies.add(trade.getCurrency());
            purchaseFeesSum = purchaseFeesSum.add(trade.getPurchaseFees());
            purchaseTotalSum = purchaseTotalSum.add(trade.getPurchaseTotal());
            if (trade.getSellDate() != null)
            {
                purchaseTotalSumSold = purchaseTotalSumSold.add(trade.getPurchaseTotal());
                sellFeesSum = sellFeesSum.add(trade.getSellFees());
                sellTotalSum = sellTotalSum.add(trade.getSellTotal());
            }
        }
        String profit = "";
        String profitPercentage = "";
        if (!purchaseTotalSumSold.equals(new BigDecimal("0.00"))){
            profit = format(sellTotalSum.subtract(purchaseTotalSumSold));
            profitPercentage = format(sellTotalSum.divide(purchaseTotalSumSold, 4, RoundingMode.HALF_UP).subtract(new BigDecimal(1)).multiply(new BigDecimal(100)));
        }
        return new String[]{String.valueOf(companies.size()), String.valueOf(currencies.size()), "", "", "",
                format(purchaseFeesSum), format(purchaseTotalSum), "", "", "",
                format(sellFeesSum), format(sellTotalSum), profit, profitPercentage};
    }

    public Trade createTrade(TradeCreateDto dto)
    {
        Trade newTrade = new Trade();

        newTrade.setCompany(companyService.findEntity(dto.getCompanyId()));
        newTrade.setPurchaseDate(Date.valueOf(dto.getDate()));
        newTrade.setQuantity(new BigDecimal(dto.getQuantity()));
        newTrade.setPurchasePrice(new BigDecimal(dto.getPrice()));
        newTrade.setPurchaseFees(new BigDecimal(dto.getFees()));

        tradeDao.create(newTrade);

        return tradeDao.get(newTrade.getId());
    }

    public void sellTrade(TradeSellDto dto)
    {
        if (dto.getTrades().isEmpty()) throw new IllegalArgumentException("no trades specified");
        Company company = companyService.findEntity(dto.getCompanyId());

        List<Trade> trades = new ArrayList<>();
        BigDecimal totalSellQuantity = new BigDecimal(0);
        for (TradeSellDto.Trade tradeDto : dto.getTrades())
        {
            try {
                Trade trade = tradeDao.get(tradeDto.getTradeId());
                if (!trade.getCompany().getId().equals(company.getId())) {
                    throw new ServiceFailureException("provided companyId and trade='" + trade.getId() + "' companyId doesn't match");
                }
                BigDecimal sellQuantity = new BigDecimal(tradeDto.getQuantity());
                if (trade.getQuantity().compareTo(sellQuantity) < 0){
                    throw new ServiceFailureException("unable to sell more than owned for tradeId='" + tradeDto.getTradeId() + "'");
                } else {
                    totalSellQuantity = totalSellQuantity.add(sellQuantity);
                }
                trades.add(trade);
            } catch (NoResultException e){
                throw new ServiceFailureException("trade with id '" + tradeDto.getTradeId() + "' not found");
            }
        }

        Date date = Date.valueOf(dto.getDate());

        for (TradeSellDto.Trade tradeDto : dto.getTrades())
        {
            Trade trade = trades.stream().filter(t -> t.getId().equals(tradeDto.getTradeId())).findFirst().get();

            BigDecimal sellQuantity = new BigDecimal(tradeDto.getQuantity());

            if (trade.getQuantity().compareTo(sellQuantity) > 0)
            {
                BigDecimal residualQuantity = trade.getQuantity().subtract(sellQuantity);
                BigDecimal residualFees = trade.getPurchaseFees().multiply(residualQuantity).divide(trade.getQuantity(), 2, RoundingMode.HALF_UP);

                Trade residualTrade = new Trade();
                residualTrade.setCompany(trade.getCompany());
                residualTrade.setQuantity(residualQuantity);
                residualTrade.setPurchaseDate(trade.getPurchaseDate());
                residualTrade.setPurchasePrice(trade.getPurchasePrice());
                residualTrade.setPurchaseFees(residualFees);

                trades.add(residualTrade);

                trade.setQuantity(sellQuantity);
                trade.setPurchaseFees(trade.getPurchaseFees().subtract(residualFees));
            }
            trade.setSellDate(date);
            trade.setSellPrice(new BigDecimal(dto.getPrice()));
            trade.setSellFees(new BigDecimal(dto.getFees()).multiply(sellQuantity).divide(totalSellQuantity, 2, RoundingMode.HALF_UP));
        }

        tradeDao.saveAll(trades);
    }

    /**
     * @return aggregates map <companyId, [all trades count, active trades count, closed trades count]>
     */
    public Map<String, int[]> getCompanyAggregates()
    {
        Map<String, int[]> map = new HashMap<>();
        for (Trade trade : tradeDao.list(null, null, null, null, null, null))
        {
            String companyId = trade.getCompany().getId();
            int[] aggregates = map.containsKey(companyId) ? map.get(companyId) : new int[]{0,0,0};
            aggregates[0] = aggregates[0] + 1;
            if (trade.getSellDate() == null){
                aggregates[1] = aggregates[1] + 1;
            } else {
                aggregates[2] = aggregates[2] + 1;
            }
            map.put(companyId, aggregates);
        }
        return map;
    }

    public Assets getAssets(String companyId, BigDecimal currentPrice)
    {
        List<Asset> assets = new ArrayList<>();
        for (Trade trade : getTrades(true, companyId, null, null, null, null))
        {
            assets.add(arithmeticService.computeAsset(currentPrice, trade.getQuantity(), trade.getPurchasePrice()));
        }

        Assets model = new Assets();
        model.getAssets().addAll(assets);
        model.setAggregate(computeAssetAggregate(assets));

        return model;
    }

    private Asset computeAssetAggregate(List<Asset> assets)
    {
        if (assets == null || assets.isEmpty()) return null;

        BigDecimal currentPrice = assets.get(0).getCurrentPrice();
        BigDecimal sumQuantity = BigDecimal.ZERO;
        BigDecimal purchaseCosts = BigDecimal.ZERO;

        for (Asset asset : assets)
        {
            if (!Utils.equalsNullableBigDecimal(asset.getCurrentPrice(), currentPrice))
                throw new ServiceFailureException("Corrupt data - all current prices should be the same: " + asset.getCurrentPrice() + " != " + currentPrice);

            sumQuantity = sumQuantity.add(asset.getQuantity());
            purchaseCosts = purchaseCosts.add(asset.getPurchasePrice().multiply(asset.getQuantity()));
        }

        BigDecimal avgPurchasePrice = purchaseCosts.divide(sumQuantity, 2, RoundingMode.HALF_UP);

        return arithmeticService.computeAsset(currentPrice, sumQuantity,  avgPurchasePrice);
    }
}
