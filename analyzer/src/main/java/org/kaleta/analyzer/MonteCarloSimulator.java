package org.kaleta.analyzer;

import org.kaleta.analyzer.cci.ConditionerCCI;
import org.kaleta.analyzer.cci.DataCCI;
import org.kaleta.analyzer.cci.TraderCCI;

import java.math.BigDecimal;
import java.util.List;

public class MonteCarloSimulator {

    public static void cci(List<DataCCI> data,
                           int buySignalMin, int buySignalMax, int buyConditionDiff,
                           int sellSignalMin, int sellSignalMax, int sellConditionDiff){
        BigDecimal startingCapital = new BigDecimal("1000");
        BigDecimal biggest = new BigDecimal("0");
        String biggestMsg = "";
        BigDecimal lowest = new BigDecimal("1000000");
        String lowestMsg = "";

        for (int buySignal=buySignalMin; buySignal >= buySignalMax; buySignal--) {
            for (int buyCondition=buySignal; buyCondition >= buySignal-buyConditionDiff; buyCondition--) {
                for (int sellSignal=sellSignalMin; sellSignal <= sellSignalMax; sellSignal++) {
                    for (int sellCondition=sellSignal; sellCondition <= sellSignal+sellConditionDiff; sellCondition++) {
                        ConditionerCCI conditioner = new ConditionerCCI(new BigDecimal(buyCondition),new BigDecimal(buySignal),new BigDecimal(sellCondition),new BigDecimal(sellSignal));
                        TraderCCI trader = new TraderCCI(data, conditioner);
                        BigDecimal finalCapital = trader.getCapitalAfterTrades(startingCapital);
                        String msg = conditioner.getBuyCondition() +"|"+ conditioner.getBuySignal() +"|"+ conditioner.getSellCondition() +"|"+ conditioner.getSellSignal() +"|"+ finalCapital;

//                        System.out.println(msg);

                        if (finalCapital.compareTo(biggest) > 0) {
                            biggest = finalCapital;
                            biggestMsg = msg;
                        }

                        if (finalCapital.compareTo(lowest) < 0) {
                            lowest = finalCapital;
                            lowestMsg = msg;
                        }

                    }
                }
            }
        }
        System.out.println("BIGGEST: " + biggestMsg);
        System.out.println("LOWEST: " + lowestMsg);
    }
}
