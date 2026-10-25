package org.kaleta.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.sql.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "Target",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_targetIdentity",
                columnNames = {"periodId", "date", "institution", "price"}))
public class Target extends AbstractEntity
{
    @Column(name = "date", nullable = false)
    private Date date;

    @Column(name = "institution", nullable = false, length = 50)
    private String institution;

    @Column(name = "price", nullable = false, precision = 10, scale = 4)
    private BigDecimal price;

    @Column(name = "rating", length = 30)
    private String rating;

    @Column(name = "overview", length = 1000)
    private String overview;

    @Column(name = "takeaway1", length = 500)
    private String takeaway1;

    @Column(name = "takeaway2", length = 500)
    private String takeaway2;

    @Column(name = "takeaway3", length = 500)
    private String takeaway3;

    @Column(name = "takeaway4", length = 500)
    private String takeaway4;

    @ManyToOne
    @JoinColumn(name = "periodId", nullable = false)
    private Period period;
}
