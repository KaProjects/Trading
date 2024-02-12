package org.kaleta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@EqualsAndHashCode(callSuper=true)
@Entity
@Table(name = "Financial")
public class Financial extends AbstractEntity
{
    @ManyToOne
    @JoinColumn(name ="companyId", nullable = false)
    private Company company;

    @Column(name = "quarter", nullable = false)
    private String quarter;

    @Column(name = "revenue", nullable = false)
    private BigDecimal revenue;

    @Column(name = "net_income", nullable = false)
    private BigDecimal netIncome;

    @Column(name = "eps", nullable = false)
    private BigDecimal eps;

    public String getTicker()
    {
        return company.getTicker();
    }

    public BigDecimal getNetMargin()
    {
        return getNetIncome().divide(getRevenue(), 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100));
    }

    public int compareTo(Financial other)
    {
        int compareYears = -(Integer.parseInt(this.getQuarter().substring(0, 2)) - Integer.parseInt(other.getQuarter().substring(0, 2)));
        if (compareYears != 0) {
            return compareYears;
        } else {
            return -(Integer.parseInt(this.getQuarter().substring(3, 4)) - Integer.parseInt(other.getQuarter().substring(3, 4)));
        }
    }
}
