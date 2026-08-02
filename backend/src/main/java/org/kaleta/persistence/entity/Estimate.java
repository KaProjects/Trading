package org.kaleta.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "Estimate")
public class Estimate extends AbstractEntity
{
    @Column(name = "datetime", nullable = false)
    private LocalDateTime datetime;

    @Column(name = "ttm", nullable = false)
    private BigDecimal ttm;

    @Column(name = "current", nullable = false)
    private BigDecimal current;

    @Column(name = "next1")
    private BigDecimal next1;

    @Column(name = "next2")
    private BigDecimal next2;

    @Column(name = "next3")
    private BigDecimal next3;

    @ManyToOne
    @JoinColumn(name = "periodId", nullable = false)
    private Period period;
}
