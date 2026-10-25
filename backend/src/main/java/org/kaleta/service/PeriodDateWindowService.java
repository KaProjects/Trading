package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.persistence.entity.PeriodType;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@ApplicationScoped
public class PeriodDateWindowService
{
    public Resolution resolve(Period period, List<Period> periods)
    {
        return resolve(period, periods, () -> null);
    }

    /**
     * Resolves the window a period's targets and news belong to.
     * <p>
     * The report dates come from this app's own periods. A quarter that has not been reported yet
     * has no date here, and neither does a quarter whose predecessor was never imported, so the
     * window falls back to the dates Firebase holds for that quarter - the same ones the research
     * page already displays as the expected report date. The fallback is a {@link Supplier} so a
     * period with its own dates costs no Firebase read.
     *
     * @param expected the dates Firebase holds for this quarter, or null when it has none
     */
    public Resolution resolve(Period period, List<Period> periods, Supplier<ExpectedDates> expected)
    {
        LocalDate currentReportDate = toLocalDate(period.getReportDate());
        LocalDate previousReportDate = previousPeriod(period, periods)
                .map(Period::getReportDate)
                .map(Date::toLocalDate)
                .orElse(null);

        if (currentReportDate == null || previousReportDate == null) {
            ExpectedDates fallback = expected == null ? null : expected.get();
            if (fallback != null) {
                if (currentReportDate == null) currentReportDate = fallback.current();
                if (previousReportDate == null) previousReportDate = fallback.previous();
            }
        }

        if (currentReportDate == null && previousReportDate == null) {
            return new Resolution(
                    null,
                    "the period has no report date in this app and Firebase holds none for it");
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

        /**
         * Research written on a report day belongs to the quarter that was reported, so for
         * content both report dates count as inside. A date landing on one therefore fits the
         * periods on either side of it, and is filed under whichever it was written for.
         */
        public boolean includes(LocalDate date)
        {
            return !date.isBefore(start) && !date.isAfter(end);
        }
    }

    /** The report dates Firebase holds for a quarter: this quarter's, and its predecessor's. */
    public record ExpectedDates(LocalDate current, LocalDate previous) {}

    public record Resolution(DateWindow window, String error) {}
}
