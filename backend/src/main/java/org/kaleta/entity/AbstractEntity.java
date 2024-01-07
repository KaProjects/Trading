package org.kaleta.entity;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@MappedSuperclass
@EqualsAndHashCode(of = "id")
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
