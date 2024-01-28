package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.Utils;
import org.kaleta.dao.CompanyDao;
import org.kaleta.dao.TradeDao;
import org.kaleta.dto.TradeCreateDto;
import org.kaleta.dto.TradeSellDto;
import org.kaleta.entity.Company;
import org.kaleta.entity.Currency;
import org.kaleta.entity.Trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.kaleta.Utils.format;

@ApplicationScoped
public class TradeService
{
    @Inject
    TradeDao tradeDao;
    @Inject
    CompanyDao companyDao;

    public List<Trade> getTrades(Boolean active, String company, String currency, String year)
    {
        return tradeDao.list(active, company, currency, year);
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

    public Trade createTrade(TradeCreateDto tradeCreateDto)
    {
        Trade newTrade = new Trade();
        try {
            Company company = companyDao.get(tradeCreateDto.getCompanyId());
            newTrade.setCompany(company);
        } catch (NoResultException e){
            throw new ServiceFailureException("company with id '" + tradeCreateDto.getCompanyId() + "' not found");
        }
        if (Utils.isValidDbDate(tradeCreateDto.getDate())){
            newTrade.setPurchaseDate(Date.valueOf(tradeCreateDto.getDate()));
        } else {
            throw new ServiceFailureException("invalid date format '" + tradeCreateDto.getDate() + "' not YYYY-MM-DD");
        }
        newTrade.setQuantity(new BigDecimal(tradeCreateDto.getQuantity()));
        newTrade.setPurchasePrice(new BigDecimal(tradeCreateDto.getPrice()));
        newTrade.setPurchaseFees(new BigDecimal(tradeCreateDto.getFees()));

        tradeDao.create(newTrade);

        return tradeDao.get(newTrade.getId());
    }

    public void sellTrade(TradeSellDto tradeSellDto)
    {
        List<Trade> trades = new ArrayList<>();
        BigDecimal totalSellQuantity = new BigDecimal(0);
        for (TradeSellDto.Trade tradeDto : tradeSellDto.getTrades())
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

        Date date;
        if (Utils.isValidDbDate(tradeSellDto.getDate())){
            date = Date.valueOf(tradeSellDto.getDate());
        } else {
            throw new ServiceFailureException("invalid date format '" + tradeSellDto.getDate() + "' not YYYY-MM-DD");
        }

        for (TradeSellDto.Trade tradeDto : tradeSellDto.getTrades())
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
            trade.setSellPrice(new BigDecimal(tradeSellDto.getPrice()));
            trade.setSellFees(new BigDecimal(tradeSellDto.getFees()).multiply(sellQuantity).divide(totalSellQuantity, 2, RoundingMode.HALF_UP));
        }

        tradeDao.saveAll(trades);
    }
}
