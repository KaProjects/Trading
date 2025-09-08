package org.kaleta.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@EqualsAndHashCode(callSuper=true)
@Entity
@Table(name = "Financial")
public class Financial extends AbstractCompanyEntity
{
    @Column(name = "quarter", nullable = false)
    private String quarter;

    @Column(name = "revenue", nullable = false)
    private BigDecimal revenue;

    @Column(name = "cogs", nullable = false)
    private BigDecimal costGoodsSold;

    @Column(name = "op_exp", nullable = false)
    private BigDecimal operatingExpenses;

    @Column(name = "net_income", nullable = false)
    private BigDecimal netIncome;

    public String getTicker()
    {
        return getCompany().getTicker();
    }

    public BigDecimal getGrossProfit()
    {
        return getRevenue().subtract(getCostGoodsSold());
    }

    public BigDecimal getGrossMargin()
    {
        return getGrossProfit().multiply(new BigDecimal(100)).divide(getRevenue(), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getOperatingIncome()
    {
        return getGrossProfit().subtract(getOperatingExpenses());
    }

    public BigDecimal getOperatingMargin()
    {
        return getOperatingIncome().multiply(new BigDecimal(100)).divide(getRevenue(), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getNetMargin()
    {
        return getNetIncome().multiply(new BigDecimal(100)).divide(getRevenue(), 2, RoundingMode.HALF_UP);
    }

    public int compareTo(Financial other)
    {
        int compareYears = -(Integer.parseInt(this.getQuarter().substring(0, 2)) - Integer.parseInt(other.getQuarter().substring(0, 2)));
        if (compareYears != 0) {
            return compareYears;
        } else {
            if (!this.getQuarter().substring(2, 3).equals(other.getQuarter().substring(2, 3))) return 0;
            return -(Integer.parseInt(this.getQuarter().substring(3, 4)) - Integer.parseInt(other.getQuarter().substring(3, 4)));
        }
    }
}
