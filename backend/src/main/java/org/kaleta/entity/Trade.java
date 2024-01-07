package org.kaleta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;

@Data
@Entity
@Table(name = "Trade")
public class Trade extends AbstractEntity
{
    @Column(name = "ticker")
    @NotNull
    private String ticker;

    @Column(name = "currency")
    @NotNull
    private String currency;

    @Column(name = "quantity")
    @NotNull
    private BigDecimal quantity;

    @Column(name = "purchase_date")
    @NotNull
    private Date purchaseDate;

    @Column(name = "purchase_price")
    @NotNull
    private BigDecimal purchasePrice;

    @Column(name = "purchase_fees")
    @NotNull
    private BigDecimal purchaseFees;

    @Column(name = "sell_date")
    private Date sellDate;

    @Column(name = "sell_price")
    private BigDecimal sellPrice;

    @Column(name = "sell_fees")
    private BigDecimal sellFees;

    public Currency getCurrency()
    {
        return Currency.valueOf(currency);
    }

    public BigDecimal getPurchaseTotal()
    {
        return purchasePrice.multiply(quantity).add(purchaseFees);
    }

    public BigDecimal getSellTotal()
    {
        return sellPrice.multiply(quantity).add(sellFees);
    }

    public BigDecimal getProfit()
    {
        return getSellTotal().subtract(getPurchaseTotal());
    }

    public BigDecimal getProfitPercentage()
    {
        return getSellTotal().divide(getPurchaseTotal(), 4, RoundingMode.HALF_UP).subtract(new BigDecimal(1)).multiply(new BigDecimal(100));
    }
}
