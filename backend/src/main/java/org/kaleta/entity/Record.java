package org.kaleta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.sql.Date;

@Data
@EqualsAndHashCode(callSuper=true)
@Entity
@Table(name = "Record")
public class Record extends AbstractEntity
{
    @ManyToOne
    @JoinColumn(name ="companyId", nullable = false)
    private Company company;

    @Column(name = "date", nullable = false)
    private Date date;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "pe")
    private BigDecimal pe;

    @Column(name = "ps")
    private BigDecimal ps;

    @Column(name = "dy")
    private BigDecimal dy;

    @Column(name = "targets")
    private String targets;

    @Column(name = "content")
    private String content;

    @Column(name = "strategy")
    private String strategy;
}
