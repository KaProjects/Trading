package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.model.Asset;
import org.kaleta.model.Assets;
import org.kaleta.model.PeriodFrequency;
import org.kaleta.model.Trades;
import org.kaleta.persistence.api.TradeDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Trade;
import org.kaleta.rest.dto.TradeCreateDto;
import org.kaleta.rest.dto.TradeSellDto;
import org.kaleta.rest.error.InvalidInputException;
import org.kaleta.rest.error.ServiceFailureException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class TradeService
{
    @Inject
    TradeDao tradeDao;
    @Inject
    CompanyService companyService;
    @Inject
    ArithmeticService arithmeticService;

    public void createTrade(TradeCreateDto dto)
    {
        Trade newTrade = new Trade();

        newTrade.setCompany(companyService.findEntity(dto.getCompanyId()));
        newTrade.setPurchaseDate(Date.valueOf(dto.getDate()));
        newTrade.setQuantity(new BigDecimal(dto.getQuantity()));
        newTrade.setPurchasePrice(new BigDecimal(dto.getPrice()));
        newTrade.setPurchaseFees(new BigDecimal(dto.getFees()));

        tradeDao.create(newTrade);
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
                    throw new InvalidInputException("provided companyId and trade='" + trade.getId() + "' companyId doesn't match");
                }
                BigDecimal sellQuantity = new BigDecimal(tradeDto.getQuantity());
                if (trade.getQuantity().compareTo(sellQuantity) < 0){
                    throw new InvalidInputException("unable to sell more than owned for tradeId='" + tradeDto.getTradeId() + "'");
                } else {
                    totalSellQuantity = totalSellQuantity.add(sellQuantity);
                }
                trades.add(trade);
            } catch (NoResultException e){
                throw new InvalidInputException("trade with id '" + tradeDto.getTradeId() + "' not found");
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
        for (Trade trade : tradeDao.list(true, companyId, null, null, null, null))
        {
            assets.add(arithmeticService.computeAsset(currentPrice, trade.getQuantity(), trade.getPurchasePrice()));
        }

        Assets model = new Assets();
        model.getAssets().addAll(assets);
        model.setAggregate(computeAssetAggregate(assets));

        return model;
    }

    public Trades getBy(Boolean active, String company, String currency, String purchaseYear, String sellYear, String sector){
        List<Trade> trades = tradeDao.list(active, company, currency, purchaseYear, sellYear, sector);

        Trades model = new Trades();

        model.setTrades(trades.stream().map(this::from).sorted(Trades.Trade::compareTo).collect(Collectors.toList()));

        model.setAggregates(computeAggregates(model.getTrades()));

        return model;
    }

    public Map<org.kaleta.model.Company, List<Trades.Trade>> getByCompany(String currency, String purchaseYear, String sellYear, String sector) {
        Trades trades = getBy(false, null, currency, purchaseYear, sellYear, sector);
        Map<org.kaleta.model.Company, List<Trades.Trade>> map = new HashMap<>();
        for (Trades.Trade trade : trades.getTrades()) {
            if (!map.containsKey(trade.getCompany())) {
                map.put(trade.getCompany(), new ArrayList<>());
            }
            map.get(trade.getCompany()).add(trade);
        }
        return map;
    }

    public Map<String, List<Trades.Trade>> getByPeriod(PeriodFrequency frequency, String companyId, String currency, String sector) {
        Trades trades = getBy(false, companyId, currency, null, null, sector);
        Map<String, List<Trades.Trade>> map = new HashMap<>();
        for (Trades.Trade trade : trades.getTrades()) {
            String period = frequency.parseDate(trade.getSellDate().toString());
            if (!map.containsKey(period)) {
                for (String key : frequency.getAllYearKeys(trade.getSellDate().toString())) {
                    map.put(key, new ArrayList<>());
                }
            }
            map.get(period).add(trade);
        }
        return map;
    }

    private Trades.Trade from(Trade entity)
    {
        Trades.Trade trade = new Trades.Trade();
        trade.setId(entity.getId());
        trade.setCompany(companyService.from(entity.getCompany()));
        trade.setPurchaseDate(entity.getPurchaseDate());
        trade.setPurchaseQuantity(entity.getQuantity());
        trade.setPurchasePrice(entity.getPurchasePrice());
        trade.setPurchaseFees(entity.getPurchaseFees());

        BigDecimal purchaseTotal = entity.getPurchasePrice()
                .multiply(entity.getQuantity())
                .setScale(2, RoundingMode.HALF_UP)
                .add(entity.getPurchaseFees());
        trade.setPurchaseTotal(purchaseTotal);

        if (entity.getSellDate() != null){
            trade.setSellDate(entity.getSellDate());
            trade.setSellQuantity(entity.getQuantity());
            trade.setSellPrice(entity.getSellPrice());
            trade.setSellFees(entity.getSellFees());

            BigDecimal sellTotal = entity.getSellPrice()
                    .multiply(entity.getQuantity())
                    .setScale(2, RoundingMode.HALF_UP)
                    .subtract(entity.getSellFees());
            trade.setSellTotal(sellTotal);

            trade.setProfit(sellTotal.subtract(purchaseTotal));

            if (!arithmeticService.equalsBigDecimal(purchaseTotal, BigDecimal.ZERO)) {
                trade.setProfitPercentage(sellTotal
                        .divide(purchaseTotal, 4, RoundingMode.HALF_UP)
                        .subtract(new BigDecimal(1))
                        .multiply(new BigDecimal(100)));
            }
        }
        return trade;
    }

    private Trades.Aggregates computeAggregates(List<Trades.Trade> trades)
    {
        Set<String> companies = new HashSet<>();
        Set<Currency> currencies = new HashSet<>();
        BigDecimal purchaseFeesSum = BigDecimal.ZERO;
        BigDecimal purchaseTotalSum = BigDecimal.ZERO;
        BigDecimal purchaseSoldTotalSum = BigDecimal.ZERO;
        BigDecimal sellFeesSum = BigDecimal.ZERO;
        BigDecimal sellTotalSum = BigDecimal.ZERO;
        for (Trades.Trade trade : trades)
        {
            companies.add(trade.getTicker());
            currencies.add(trade.getCurrency());
            purchaseFeesSum = purchaseFeesSum.add(trade.getPurchaseFees());
            purchaseTotalSum = purchaseTotalSum.add(trade.getPurchaseTotal());
            if (trade.getSellDate() != null)
            {
                purchaseSoldTotalSum = purchaseSoldTotalSum.add(trade.getPurchaseTotal());
                sellFeesSum = sellFeesSum.add(trade.getSellFees());
                sellTotalSum = sellTotalSum.add(trade.getSellTotal());
            }
        }
        Trades.Aggregates aggregates = new Trades.Aggregates();
        aggregates.setCompanies(companies.size());
        aggregates.setCurrencies(currencies.size());
        aggregates.setPurchaseFees(purchaseFeesSum);
        aggregates.setPurchaseTotal(purchaseTotalSum);
        aggregates.setSellFees(sellFeesSum);
        aggregates.setSellTotal(sellTotalSum);

        if (!arithmeticService.equalsBigDecimal(purchaseSoldTotalSum, BigDecimal.ZERO)){
            aggregates.setProfit(sellTotalSum.subtract(purchaseSoldTotalSum));
            aggregates.setProfitPercentage(sellTotalSum
                    .divide(purchaseSoldTotalSum, 4, RoundingMode.HALF_UP)
                    .subtract(new BigDecimal(1))
                    .multiply(new BigDecimal(100)));
        }
        return aggregates;
    }

    private Asset computeAssetAggregate(List<Asset> assets)
    {
        if (assets == null || assets.isEmpty()) return null;

        BigDecimal currentPrice = assets.get(0).getCurrentPrice();
        BigDecimal sumQuantity = BigDecimal.ZERO;
        BigDecimal purchaseCosts = BigDecimal.ZERO;

        for (Asset asset : assets)
        {
            if (!arithmeticService.equalsBigDecimal(asset.getCurrentPrice(), currentPrice))
                throw new ServiceFailureException("Corrupt data - all current prices should be the same: " + asset.getCurrentPrice() + " != " + currentPrice);

            sumQuantity = sumQuantity.add(asset.getQuantity());
            purchaseCosts = purchaseCosts.add(asset.getPurchasePrice().multiply(asset.getQuantity()));
        }

        BigDecimal avgPurchasePrice = purchaseCosts.divide(sumQuantity, 2, RoundingMode.HALF_UP);

        return arithmeticService.computeAsset(currentPrice, sumQuantity,  avgPurchasePrice);
    }
}
