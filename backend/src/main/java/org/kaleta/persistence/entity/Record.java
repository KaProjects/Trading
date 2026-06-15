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
@Table(name = "Record")
public class Record extends AbstractEntityCompany
{
    @Column(name = "date", nullable = false)
    private Date date;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content")
    private String content;

    @Column(name = "strategy")
    private String strategy;

    @Column(name = "targets")
    private String targets;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "p_rev")
    private BigDecimal priceToRevenues;

    @Column(name = "p_gross")
    private BigDecimal priceToGrossProfit;

    @Column(name = "p_oper")
    private BigDecimal priceToOperatingIncome;

    @Column(name = "p_net")
    private BigDecimal priceToNetIncome;

    @Column(name = "dy")
    private BigDecimal dividendYield;

    @Column(name = "asset_quantity")
    private BigDecimal sumAssetQuantity;

    @Column(name = "asset_price")
    private BigDecimal avgAssetPrice;
}
