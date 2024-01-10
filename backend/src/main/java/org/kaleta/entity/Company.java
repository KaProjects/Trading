package org.kaleta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Entity
@Table(name = "Company")
public class Company extends AbstractEntity
{
    @Column(name = "ticker")
    @NotNull
    private String ticker;

    @Column(name = "currency")
    @NotNull
    private String currency;

    public String getTicker()
    {
        return ticker.trim();
    }
}
