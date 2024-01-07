package org.kaleta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
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
}
