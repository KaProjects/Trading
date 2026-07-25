package org.kaleta.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@EqualsAndHashCode(callSuper=true)
@Entity
@Table(name = "Company")
public class Company extends AbstractEntity
{
    @Column(name = "ticker", nullable = false)
    private String ticker;

    @Column(name = "currency", nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    private Currency currency;

    @Column(name = "watching")
    private boolean watching;

    @Column(name = "sector")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private Sector sector;

    public String getTicker() { return ticker.trim(); }
}
