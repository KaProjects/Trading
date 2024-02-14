package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.dao.TradeDao;
import org.kaleta.dto.TradeCreateDto;
import org.kaleta.dto.TradeSellDto;
import org.kaleta.entity.Currency;
import org.kaleta.entity.Trade;

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
    CommonService commonService;

    public List<Trade> getTrades(Boolean active, String company, String currency, String purchaseYear, String sellYear)
    {
        return tradeDao.list(active, company, currency, purchaseYear, sellYear);
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

        newTrade.setCompany(companyService.getCompany(dto.getCompanyId()));
        newTrade.setPurchaseDate(commonService.getDbDate(dto.getDate()));
        newTrade.setQuantity(new BigDecimal(dto.getQuantity()));
        newTrade.setPurchasePrice(new BigDecimal(dto.getPrice()));
        newTrade.setPurchaseFees(new BigDecimal(dto.getFees()));

        tradeDao.create(newTrade);

        return tradeDao.get(newTrade.getId());
    }

    public void sellTrade(TradeSellDto dto)
    {
        List<Trade> trades = new ArrayList<>();
        BigDecimal totalSellQuantity = new BigDecimal(0);
        for (TradeSellDto.Trade tradeDto : dto.getTrades())
        {
            try {
                Trade trade = tradeDao.get(tradeDto.getTradeId());
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

        Date date = commonService.getDbDate(dto.getDate());

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
        for (Trade trade : tradeDao.list(null, null, null, null, null))
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
}
