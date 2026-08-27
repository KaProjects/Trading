package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.persistence.entity.PeriodType;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PeriodDateWindowService
{
    public Resolution resolve(Period period, List<Period> periods)
    {
        LocalDate currentReportDate = toLocalDate(period.getReportDate());
        LocalDate previousReportDate = previousPeriod(period, periods)
                .map(Period::getReportDate)
                .map(Date::toLocalDate)
                .orElse(null);

        if (currentReportDate == null && previousReportDate == null) {
            return new Resolution(
                    null,
                    "current and previous report dates are unavailable");
        }

        int fallbackMonths = fallbackMonths(period.getName().getType());
        LocalDate start = previousReportDate != null
                ? previousReportDate
                : currentReportDate.minusMonths(fallbackMonths);
        LocalDate end = currentReportDate != null
                ? currentReportDate
                : previousReportDate.plusMonths(fallbackMonths);

        if (!start.isBefore(end)) {
            return new Resolution(null, "invalid report-date window");
        }
        return new Resolution(new DateWindow(start, end), null);
    }

    private Optional<Period> previousPeriod(Period period, List<Period> periods)
    {
        PeriodName expected = previous(period.getName());
        return periods.stream()
                .filter(candidate -> candidate.getName().equals(expected))
                .findFirst();
    }

    private PeriodName previous(PeriodName current)
    {
        int year = current.getYear().getValue();
        return switch (current.getType()) {
            case Q1 -> periodName(year - 1, PeriodType.Q4);
            case Q2 -> periodName(year, PeriodType.Q1);
            case Q3 -> periodName(year, PeriodType.Q2);
            case Q4 -> periodName(year, PeriodType.Q3);
            case H1 -> periodName(year - 1, PeriodType.H2);
            case H2 -> periodName(year, PeriodType.H1);
            case FY -> periodName(year - 1, PeriodType.FY);
        };
    }

    private int fallbackMonths(PeriodType type)
    {
        return switch (type) {
            case Q1, Q2, Q3, Q4 -> 3;
            case H1, H2 -> 6;
            case FY -> 12;
        };
    }

    private PeriodName periodName(int year, PeriodType type)
    {
        return PeriodName.valueOf(String.format("%02d%s", Math.floorMod(year, 100), type));
    }

    private LocalDate toLocalDate(Date date)
    {
        return date == null ? null : date.toLocalDate();
    }

    public record DateWindow(LocalDate start, LocalDate end)
    {
        public boolean contains(LocalDate date)
        {
            return !date.isBefore(start) && date.isBefore(end);
        }
    }

    public record Resolution(DateWindow window, String error) {}
}
