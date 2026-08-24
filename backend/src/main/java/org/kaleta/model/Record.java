package org.kaleta.model;

import lombok.Data;

import java.math.BigDecimal;
import java.sql.Date;

@Data
public class Record
{
    private Long id;
    private Date date;
    private String title;
    private String content;
    private String review;

    private BigDecimal price;

    private BigDecimal priceToRevenues;
    private BigDecimal priceToGrossProfit;
    private BigDecimal priceToOperatingIncome;
    private BigDecimal priceToNetIncome;
    private BigDecimal priceToFreeCashFlow;
    private BigDecimal dividendYield;

    private String strategy;
    private String retro;
    private String targets;

    private Asset asset;
}
