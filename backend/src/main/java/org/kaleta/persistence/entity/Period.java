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
@Table(name = "Period")
public class Period extends AbstractEntityCompany
{
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "ending_month", nullable = false)
    private String endingMonth;

    @Column(name = "report_date")
    private Date reportDate;

    @Column(name = "shares")
    private BigDecimal shares;

    @Column(name = "price_low")
    private BigDecimal priceLow;

    @Column(name = "price_high")
    private BigDecimal priceHigh;

    @Column(name = "research")
    private String research;

    @Column(name = "revenue")
    private BigDecimal revenue;

    @Column(name = "cogs")
    private BigDecimal costGoodsSold;

    @Column(name = "op_exp")
    private BigDecimal operatingExpenses;

    @Column(name = "net_income")
    private BigDecimal netIncome;

    @Column(name = "dividend")
    private BigDecimal dividend;
}
