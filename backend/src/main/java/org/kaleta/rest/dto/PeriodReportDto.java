package org.kaleta.rest.dto;

import org.kaleta.model.Periods;
import org.kaleta.persistence.entity.Currency;

/**
 * One period as it is read away from its company's page: what it reported and the currency to
 * read it in. The case page opens this beside the day the company reported.
 */
public record PeriodReportDto(Periods.Period period, String ticker, Currency currency)
{
}
