package org.kaleta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;


import javax.validation.constraints.NotNull;
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
    @NotNull
    private BigDecimal sellPrice;

    @Column(name = "sell_fees")
    @NotNull
    private BigDecimal sellFees;

    public Currency getCurrency()
    {
        return Currency.valueOf(currency);
    }
}
