package org.kaleta.client.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;

@RegisterForReflection
public record PolygonSplit(BigDecimal splitFrom, BigDecimal splitTo, String executionDate)
{
}
