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
@Table(name = "Dividend")
public class Dividend extends AbstractCompanyEntity
{
    @Column(name = "date", nullable = false)
    private Date date;

    @Column(name = "dividend", nullable = false)
    private BigDecimal dividend;

    @Column(name = "tax", nullable = false)
    private BigDecimal tax;

    public Currency getCurrency()
    {
        return getCompany().getCurrency();
    }

    public String getTicker()
    {
        return getCompany().getTicker();
    }

    public BigDecimal getTotal()
    {
        return dividend.subtract(tax);
    }
}
