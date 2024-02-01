package org.kaleta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;

@Data
@Entity
@Table(name = "Trade")
public class Trade extends AbstractEntity
{
    @ManyToOne
    @JoinColumn(name ="companyId", nullable = false)
    private Company company;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "purchase_date", nullable = false)
    private Date purchaseDate;

    @Column(name = "purchase_price", nullable = false)
    private BigDecimal purchasePrice;

    @Column(name = "purchase_fees", nullable = false)
    private BigDecimal purchaseFees;

    @Column(name = "sell_date")
    private Date sellDate;

    @Column(name = "sell_price")
    private BigDecimal sellPrice;

    @Column(name = "sell_fees")
    private BigDecimal sellFees;

    public String getTicker()
    {
        return company.getTicker();
    }

    public Currency getCurrency()
    {
        return Currency.valueOf(company.getCurrency());
    }

    public BigDecimal getPurchaseTotal()
    {
        return purchasePrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP).add(purchaseFees);
    }

    public BigDecimal getSellTotal()
    {
        return sellPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP).subtract(sellFees);
    }

    public BigDecimal getProfit()
    {
        return getSellTotal().subtract(getPurchaseTotal());
    }

    public BigDecimal getProfitPercentage()
    {
        if (purchasePrice.equals(new BigDecimal("0.0000"))) return null;
        return getSellTotal().divide(getPurchaseTotal(), 4, RoundingMode.HALF_UP).subtract(new BigDecimal(1)).multiply(new BigDecimal(100));
    }
}
