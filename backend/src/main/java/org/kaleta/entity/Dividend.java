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
@Table(name = "Dividend")
public class Dividend extends AbstractEntity
{
    @ManyToOne
    @JoinColumn(name ="companyId", nullable = false)
    private Company company;

    @Column(name = "date", nullable = false)
    private Date date;

    @Column(name = "dividend", nullable = false)
    private BigDecimal dividend;

    @Column(name = "tax", nullable = false)
    private BigDecimal tax;

    public Currency getCurrency()
    {
        return Currency.valueOf(company.getCurrency());
    }

    public String getTicker()
    {
        return company.getTicker();
    }

    public BigDecimal getTotal()
    {
        return dividend.subtract(tax);
    }
}
