package org.kaleta.model;

import lombok.Data;
import org.kaleta.entity.Financial;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class FinancialsModel
{
    private final List<Financial> financials;

    public FinancialsModel(List<Financial> financials) {this.financials = financials;}

    public List<Financial> getSortedFinancials()
    {
        financials.sort(Financial::compareTo);
        return financials;
    }

    public Financial getTtmFinancials()
    {
        if (financials.size() > 0){
            financials.sort(Financial::compareTo);
            List<Financial> financialsPerQuarter = new ArrayList<>();
            for (Financial financial : financials){
                switch (financial.getQuarter().substring(2,3)){
                    case "F":
                        Financial finFromYear = new Financial();
                        finFromYear.setRevenue(financial.getRevenue().divide(new BigDecimal(4), 2, RoundingMode.HALF_UP));
                        finFromYear.setCostGoodsSold(financial.getCostGoodsSold().divide(new BigDecimal(4), 2, RoundingMode.HALF_UP));
                        finFromYear.setOperatingExpenses(financial.getOperatingExpenses().divide(new BigDecimal(4), 2, RoundingMode.HALF_UP));
                        finFromYear.setNetIncome(financial.getNetIncome().divide(new BigDecimal(4), 2, RoundingMode.HALF_UP));
                        financialsPerQuarter.add(finFromYear);
                        financialsPerQuarter.add(finFromYear);
                        financialsPerQuarter.add(finFromYear);
                        financialsPerQuarter.add(finFromYear);
                        break;
                    case "H":
                        Financial finFromHalfYear = new Financial();
                        finFromHalfYear.setRevenue(financial.getRevenue().divide(new BigDecimal(2), 2, RoundingMode.HALF_UP));
                        finFromHalfYear.setCostGoodsSold(financial.getCostGoodsSold().divide(new BigDecimal(2), 2, RoundingMode.HALF_UP));
                        finFromHalfYear.setOperatingExpenses(financial.getOperatingExpenses().divide(new BigDecimal(2), 2, RoundingMode.HALF_UP));
                        finFromHalfYear.setNetIncome(financial.getNetIncome().divide(new BigDecimal(2), 2, RoundingMode.HALF_UP));
                        financialsPerQuarter.add(finFromHalfYear);
                        financialsPerQuarter.add(finFromHalfYear);
                        break;
                    case "Q":
                        financialsPerQuarter.add(financial);
                        break;
                    default: throw new IllegalArgumentException(financial.getQuarter() + " invalid type");
                }
            }

            BigDecimal revenue = new BigDecimal(0);
            BigDecimal cogs = new BigDecimal(0);
            BigDecimal opExpenses = new BigDecimal(0);
            BigDecimal netIncome = new BigDecimal(0);

            for (int i=0; i<4; i++){
                if (financialsPerQuarter.size() > i){
                    revenue = revenue.add(financialsPerQuarter.get(i).getRevenue());
                    cogs = cogs.add(financialsPerQuarter.get(i).getCostGoodsSold());
                    opExpenses = opExpenses.add(financialsPerQuarter.get(i).getOperatingExpenses());
                    netIncome = netIncome.add(financialsPerQuarter.get(i).getNetIncome());

                } else {
                    BigDecimal multiplier = new BigDecimal(4).divide(new BigDecimal(i), 4, RoundingMode.HALF_UP);
                    revenue = revenue.multiply(multiplier);
                    cogs = cogs.multiply(multiplier);
                    opExpenses = opExpenses.multiply(multiplier);
                    netIncome = netIncome.multiply(multiplier);

                    break;
                }
            }
            Financial ttm = new Financial();
            ttm.setRevenue(revenue.setScale(0, RoundingMode.HALF_UP));
            ttm.setCostGoodsSold(cogs.setScale(0, RoundingMode.HALF_UP));
            ttm.setOperatingExpenses(opExpenses.setScale(0, RoundingMode.HALF_UP));
            ttm.setNetIncome(netIncome.setScale(0, RoundingMode.HALF_UP));
            return ttm;
        } else {
            return null;
        }
    }
}
