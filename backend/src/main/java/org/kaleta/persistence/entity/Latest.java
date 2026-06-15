package org.kaleta.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper=true)
@Entity
@Table(name = "Latest")
public class Latest extends AbstractEntityCompany
{
    @Column(name = "datetime")
    private LocalDateTime datetime;

    @Column(name = "price")
    private BigDecimal price;

    public Latest(){}
    public Latest(Company company, LocalDateTime datetime, BigDecimal price)
    {
        this.setCompany(company);
        this.datetime = datetime;
        this.price = price;
    }
}
