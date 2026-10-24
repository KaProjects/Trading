package org.kaleta.persistence.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.sql.Date;
import java.time.YearMonth;

@Data
@EqualsAndHashCode(callSuper=true)
public class CompanyWithStats extends Company
{
    private YearMonth latestPeriodEndingMonth;
    private int periodCount;
    /** Periods whose research is more than the editor's empty document. */
    private int periodsWithContentCount;
    private Date latestRecordDate;
    private Date latestPurchaseDate;
}
