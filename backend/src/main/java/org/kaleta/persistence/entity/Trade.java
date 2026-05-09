package org.kaleta.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.sql.Date;

@Data
@EqualsAndHashCode(callSuper=true)
@Entity
@Table(name = "Trade")
public class Trade extends AbstractEntityCompany
{
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
}
