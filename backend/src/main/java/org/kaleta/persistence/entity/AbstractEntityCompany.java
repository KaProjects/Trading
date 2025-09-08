package org.kaleta.persistence.entity;

import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;

@Data
@MappedSuperclass
public class AbstractEntityCompany extends AbstractEntity
{
    @ManyToOne
    @JoinColumn(name ="companyId", nullable = false)
    private Company company;
}
