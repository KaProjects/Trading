package org.kaleta.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

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

    @Column(name = "sector")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private Sector sector;

    @ElementCollection
    @CollectionTable(name = "Tag", joinColumns = @JoinColumn(name = "companyId"))
    @Column(name = "value", nullable = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<String> tags = new ArrayList<>();

    public String getTicker() { return ticker.trim(); }
}
