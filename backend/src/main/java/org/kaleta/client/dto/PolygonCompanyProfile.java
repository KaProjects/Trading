package org.kaleta.client.dto;

import java.math.BigDecimal;

public record PolygonCompanyProfile(
        String name,
        String description,
        String website,
        String exchange,
        String currency,
        String locale,
        String type,
        Boolean active,
        String industry,
        String listDate,
        BigDecimal marketCap,
        BigDecimal sharesOutstanding,
        Integer employees)
{
}
