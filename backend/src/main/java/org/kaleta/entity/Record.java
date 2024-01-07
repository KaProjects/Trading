package org.kaleta.entity;

import io.smallrye.common.constraint.NotNull;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.sql.Date;

@Data
@Entity
@Table(name = "Record")
public class Record extends AbstractEntity
{
    @Column(name = "ticker")
    @NotNull
    private String ticker;

    @Column(name = "date")
    @NotNull
    private Date date;

    @Column(name = "title")
    @NotNull
    private String title;

    @Column(name = "price")
    @NotNull
    private BigDecimal price;

    @Column(name = "text")
    private String text;

    @Column(name = "pe")
    private BigDecimal pe;

    @Column(name = "dy")
    private BigDecimal dy;

    @Column(name = "targets")
    private String targets;

    @Column(name = "strategy")
    private String strategy;
}
