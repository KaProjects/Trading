package org.kaleta.rest.dto;

import java.math.BigDecimal;
import java.util.List;

public record OnboardingLookupDto(
        String ticker,
        boolean found,
        boolean inDatabase,
        boolean inFirebase,
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
        Integer employees,
        List<String> warnings)
{
}
