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
        /** {@code exchange} as an Exchange constant, or null when it is not one this app supports. */
        String appExchange,
        /** {@code currency} as a Currency constant, or null when no supported currency uses that ISO code. */
        String appCurrency,
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
