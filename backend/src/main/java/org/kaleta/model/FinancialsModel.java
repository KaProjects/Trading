package org.kaleta.model;

import lombok.Data;
import org.kaleta.entity.Financial;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class FinancialsModel
{
    private final List<Financial> financials;

    public FinancialsModel(List<Financial> financials) {this.financials = financials;}

    @Data
    public static class Ttm
    {
        private BigDecimal revenue;
        private BigDecimal netIncome;
        private BigDecimal netMargin;
        private BigDecimal eps;
        private BigDecimal pe;
    }

    public List<Financial> getSortedFinancials()
    {
        financials.sort(Financial::compareTo);
        return financials;
    }

    public FinancialsModel.Ttm getTtmFinancials(BigDecimal price)
    {
        if (financials.size() > 0){
            BigDecimal revenue = new BigDecimal(0);
            BigDecimal netIncome = new BigDecimal(0);
            BigDecimal eps = new BigDecimal(0);
            financials.sort(Financial::compareTo);
            for (int i=0; i<4; i++){
                if (financials.size() > i){
                    revenue = revenue.add(financials.get(i).getRevenue());
                    netIncome = netIncome.add(financials.get(i).getNetIncome());
                    eps = eps.add(financials.get(i).getEps());
                } else {
                    BigDecimal multiplier = new BigDecimal(4).divide(new BigDecimal(i), 4, RoundingMode.HALF_UP);
                    revenue = revenue.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
                    netIncome = netIncome.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
                    eps = eps.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
                    break;
                }
            }
            Ttm ttm = new Ttm();
            ttm.setRevenue(revenue);
            ttm.setNetIncome(netIncome);
            ttm.setNetMargin(netIncome.divide(revenue, 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100)));
            ttm.setEps(eps);
            ttm.setPe(price.divide(eps, 2, RoundingMode.HALF_UP));
            return ttm;
        } else {
            return null;
        }
    }

    public BigDecimal getForwardPe(BigDecimal price)
    {
        financials.sort(Financial::compareTo);
        return price.divide(financials.get(0).getEps().multiply(new BigDecimal(4)), 2, RoundingMode.HALF_UP);
    }
}
