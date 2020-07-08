package org.kaleta.analyzer.cci;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class TraderCCI {
    private List<TradeCCI> trades = new ArrayList<>();

    public TraderCCI(List<DataCCI> data, ConditionerCCI conditioner) {
        simTrades(data, conditioner);
    }

    private void simTrades(List<DataCCI> data, ConditionerCCI conditioner){
        trades.clear();
        TradeCCI trade = null;
        BigDecimal buyCondition = null;
        BigDecimal sellCondition = null;

        for (DataCCI entry : data) {
            if (trade == null) {
                if (buyCondition == null){
                    if (entry.getCci().compareTo(conditioner.getBuyCondition()) <= 0) {
                        buyCondition = entry.getCci();
                    }
                } else {
                    if (entry.getCci().compareTo(conditioner.getBuySignal()) >= 0) {
                        trade = new TradeCCI();
                        trade.setBuyCondition(buyCondition);
                        trade.setBuySignal(entry.getCci());
                        trade.setBuyValue(entry.getClose());
                        trade.setBuyDate(entry.getTime());
                    }
                }
            } else {
                if (sellCondition == null) {
                    if (entry.getCci().compareTo(conditioner.getSellCondition()) >= 0) {
                        sellCondition = entry.getCci();
                    }
                } else {
                    if (entry.getCci().compareTo(conditioner.getSellSignal()) <= 0) {
//                        if (entry.getClose().compareTo(trade.getBuyValue()) <= 0) {
//                            sellCondition = null;
//                            continue;
//                        }
                        trade.setSellCondition(sellCondition);
                        trade.setSellSignal(entry.getCci());
                        trade.setSellValue(entry.getClose());
                        trade.setSellDate(entry.getTime());

                        trades.add(trade);
                        trade = null;
                        buyCondition = null;
                        sellCondition = null;
                    }
                }
            }
        }
    }

    public void printTrades() {
        for (TradeCCI t : trades){
            System.out.println(t.toString());
        }
    }

    public void printTradesWithCapital(BigDecimal capital) {
        for (TradeCCI t : trades){
            System.out.println(t.toString());
            capital = capital.divide(t.getBuyValue(), 3, BigDecimal.ROUND_CEILING).multiply(t.getSellValue());
            System.out.println(capital);
        }
        System.out.println(capital);
    }

    public BigDecimal getCapitalAfterTrades(BigDecimal capital) {
        for (TradeCCI t : trades){
            capital = capital.divide(t.getBuyValue(), 3, BigDecimal.ROUND_CEILING).multiply(t.getSellValue());
        }
        return capital;
    }
}
