package org.kaleta.entity;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;

import java.util.UUID;

@Data
@MappedSuperclass
public abstract class AbstractEntity
{
    @Id
    protected String id = UUID.randomUUID().toString();

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "@" + id;
    }
}
