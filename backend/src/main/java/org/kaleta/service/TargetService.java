package org.kaleta.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.model.TargetStats;
import org.kaleta.persistence.api.PeriodDao;
import org.kaleta.persistence.api.TargetDao;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodName;
import org.kaleta.persistence.entity.PeriodType;
import org.kaleta.persistence.entity.Target;
import org.kaleta.rest.dto.TargetCreateDto;
import org.kaleta.rest.dto.TargetDto;
import org.kaleta.rest.dto.TargetSyncCountsDto;
import org.kaleta.rest.dto.TargetSyncDto;
import org.kaleta.rest.error.ConflictException;
import org.kaleta.rest.error.InvalidInputException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class TargetService
{
    @Inject
    TargetDao targetDao;
    @Inject
    PeriodDao periodDao;
    @Inject
    PeriodService periodService;
    @Inject
    FirebaseService firebaseService;
    @Inject
    CompanyService companyService;

    public List<TargetDto> getAll(Long periodId)
    {
        periodService.get(periodId);
        return targetDao.list(periodId).stream()
                .map(this::from)
                .toList();
    }

    public TargetDto create(Long periodId, TargetCreateDto dto)
    {
        Period period = periodService.get(periodId);
        LocalDate targetDate = parseDate(dto.getDate());
        validateTargetDate(period, targetDate);

        Target target = new Target();
        target.setPeriod(period);
        target.setDate(Date.valueOf(targetDate));
        target.setInstitution(dto.getInstitution().trim());
        target.setPrice(parsePrice(dto.getPrice()));
        target.setRating(nullIfBlank(dto.getRating()));
        target.setOverview(nullIfBlank(dto.getOverview()));
        target.setTakeaway1(nullIfBlank(dto.getTakeaway1()));
        target.setTakeaway2(nullIfBlank(dto.getTakeaway2()));
        target.setTakeaway3(nullIfBlank(dto.getTakeaway3()));
        target.setTakeaway4(nullIfBlank(dto.getTakeaway4()));

        if (targetDao.findByIdentity(
                periodId,
                target.getDate(),
                target.getInstitution(),
                target.getPrice()).isPresent()) {
            throw new ConflictException("target already exists for period '" + periodId + "'");
        }

        targetDao.create(target);
        return from(target);
    }

    public void delete(Long targetId)
    {
        try {
            targetDao.get(targetId);
        } catch (NoResultException exception) {
            throw new InvalidInputException("target with id '" + targetId + "' not found");
        }
        targetDao.delete(targetId);
    }

    public Map<Long, TargetStats> getStatistics(List<Long> periodIds)
    {
        return targetDao.statistics(periodIds);
    }

    public TargetSyncDto countImportCandidates(Long periodId)
    {
        CandidateResult result = candidates(periodService.get(periodId));
        return new TargetSyncDto(result.targets().size(), result.warnings());
    }

    public TargetSyncCountsDto countImportCandidatesByCompany(Long companyId)
    {
        String ticker = companyService.findEntity(companyId).getTicker();
        List<Period> periods = periodDao.list(companyId);
        Map<Long, Integer> counts = new LinkedHashMap<>();
        Map<Long, DateWindow> windows = new LinkedHashMap<>();
        Set<Long> failedPeriodIds = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();

        for (Period period : periods) {
            counts.put(period.getId(), 0);
            DateWindow window = dateWindow(period, periods, warnings);
            if (window != null) {
                windows.put(period.getId(), window);
            } else {
                failedPeriodIds.add(period.getId());
            }
        }
        if (windows.isEmpty()) {
            return new TargetSyncCountsDto(counts, failedPeriodIds, warnings);
        }

        FirebaseService.TargetsResult firebaseResult = firebaseService.getTargets(ticker);
        if (!firebaseResult.warnings().isEmpty()) {
            failedPeriodIds.addAll(windows.keySet());
        }
        warnings.addAll(firebaseResult.warnings());
        int warningCountBeforeMapping = warnings.size();
        List<TargetData> firebaseTargets = targetData(ticker, firebaseResult.targets(), warnings);
        if (warnings.size() > warningCountBeforeMapping) {
            failedPeriodIds.addAll(windows.keySet());
        }
        if (firebaseTargets.isEmpty()) {
            return new TargetSyncCountsDto(counts, failedPeriodIds, warnings);
        }

        List<Long> periodIds = periods.stream().map(Period::getId).toList();
        Map<Long, Set<TargetIdentity>> persistedByPeriod = new LinkedHashMap<>();
        for (Target target : targetDao.listByPeriodIds(periodIds)) {
            persistedByPeriod
                    .computeIfAbsent(target.getPeriod().getId(), ignored -> new LinkedHashSet<>())
                    .add(TargetIdentity.from(target));
        }

        for (Period period : periods) {
            DateWindow window = windows.get(period.getId());
            if (window == null) continue;

            Set<TargetIdentity> persisted = persistedByPeriod.getOrDefault(period.getId(), Set.of());
            counts.put(period.getId(), candidates(period, window, persisted, firebaseTargets).size());
        }
        return new TargetSyncCountsDto(counts, failedPeriodIds, warnings);
    }

    public TargetSyncDto sync(Long periodId)
    {
        CandidateResult result = candidates(periodService.get(periodId));
        targetDao.createAll(result.targets());
        return new TargetSyncDto(result.targets().size(), result.warnings());
    }

    private CandidateResult candidates(Period period)
    {
        List<String> warnings = new ArrayList<>();
        DateWindow window = dateWindow(
                period,
                periodDao.list(period.getCompany().getId()),
                warnings);
        if (window == null) {
            return new CandidateResult(List.of(), warnings);
        }

        FirebaseService.TargetsResult firebaseResult = firebaseService.getTargets(
                period.getCompany().getTicker());
        warnings.addAll(firebaseResult.warnings());
        Set<TargetIdentity> persisted = targetDao.list(period.getId()).stream()
                .map(TargetIdentity::from)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        List<TargetData> firebaseTargets = targetData(
                period.getCompany().getTicker(),
                firebaseResult.targets(),
                warnings);

        return new CandidateResult(candidates(period, window, persisted, firebaseTargets), warnings);
    }

    private List<Target> candidates(
            Period period,
            DateWindow window,
            Set<TargetIdentity> persisted,
            List<TargetData> firebaseTargets)
    {
        Map<TargetIdentity, Target> candidates = new LinkedHashMap<>();

        for (TargetData firebaseTarget : firebaseTargets) {
            if (firebaseTarget.date().isBefore(window.start()) || !firebaseTarget.date().isBefore(window.end())) {
                continue;
            }

            TargetIdentity identity = TargetIdentity.from(firebaseTarget);
            if (!persisted.contains(identity)) {
                candidates.putIfAbsent(identity, from(period, firebaseTarget));
            }
        }
        return List.copyOf(candidates.values());
    }

    private List<TargetData> targetData(
            String ticker,
            List<FirebaseCompany.Gemini.Target> firebaseTargets,
            List<String> warnings)
    {
        List<TargetData> result = new ArrayList<>();
        for (FirebaseCompany.Gemini.Target firebaseTarget : firebaseTargets) {
            try {
                result.add(from(firebaseTarget));
            } catch (RuntimeException exception) {
                String warning = ExternalWarnings.unavailable(
                        "Firebase target for " + ticker,
                        exception);
                Log.warn(warning, exception);
                warnings.add(warning);
            }
        }
        return List.copyOf(result);
    }

    private DateWindow dateWindow(Period period, List<Period> periods, List<String> warnings)
    {
        DateWindowResolution resolution = resolveDateWindow(period, periods);
        if (resolution.window() != null) {
            return resolution.window();
        }

        warnings.add("Firebase targets for " + period.getCompany().getTicker()
                + " " + period.getName() + " could not be loaded: " + resolution.error());
        return null;
    }

    private DateWindowResolution resolveDateWindow(Period period, List<Period> periods)
    {
        LocalDate currentReportDate = toLocalDate(period.getReportDate());
        LocalDate previousReportDate = previousPeriod(period, periods)
                .map(Period::getReportDate)
                .map(Date::toLocalDate)
                .orElse(null);

        if (currentReportDate == null && previousReportDate == null) {
            return new DateWindowResolution(
                    null,
                    "current and previous report dates are unavailable");
        }

        LocalDate start = previousReportDate != null
                ? previousReportDate
                : currentReportDate.minusMonths(3);
        LocalDate end = currentReportDate != null
                ? currentReportDate
                : previousReportDate.plusMonths(3);

        if (!start.isBefore(end)) {
            return new DateWindowResolution(null, "invalid report-date window");
        }
        return new DateWindowResolution(new DateWindow(start, end), null);
    }

    private void validateTargetDate(Period period, LocalDate targetDate)
    {
        DateWindowResolution resolution = resolveDateWindow(
                period,
                periodDao.list(period.getCompany().getId()));
        if (resolution.window() == null) {
            throw new InvalidInputException(
                    "target date cannot be validated for period '" + period.getName()
                            + "': " + resolution.error());
        }

        DateWindow window = resolution.window();
        if (targetDate.isBefore(window.start()) || !targetDate.isBefore(window.end())) {
            throw new InvalidInputException(
                    "target date '" + targetDate + "' must be on or after '" + window.start()
                            + "' and before '" + window.end() + "' for period '" + period.getName() + "'");
        }
    }

    private java.util.Optional<Period> previousPeriod(Period period, List<Period> periods)
    {
        PeriodName expected = previous(period.getName());
        return periods.stream()
                .filter(candidate -> candidate.getName().equals(expected))
                .findFirst();
    }

    private PeriodName previous(PeriodName current)
    {
        int year = current.getYear().getValue();
        PeriodType type = current.getType();
        return switch (type) {
            case Q1 -> periodName(year - 1, PeriodType.Q4);
            case Q2 -> periodName(year, PeriodType.Q1);
            case Q3 -> periodName(year, PeriodType.Q2);
            case Q4 -> periodName(year, PeriodType.Q3);
            case H1 -> periodName(year - 1, PeriodType.H2);
            case H2 -> periodName(year, PeriodType.H1);
            case FY -> periodName(year - 1, PeriodType.FY);
        };
    }

    private PeriodName periodName(int year, PeriodType type)
    {
        return PeriodName.valueOf(String.format("%02d%s", Math.floorMod(year, 100), type));
    }

    private TargetData from(FirebaseCompany.Gemini.Target source)
    {
        if (source == null) throw new IllegalArgumentException("target is null");

        String institution = required(source.getInstitution(), "institution");
        if (institution.length() > 50) throw new IllegalArgumentException("institution is longer than 50 characters");

        String overview = null;
        String takeaway1 = null;
        String takeaway2 = null;
        String takeaway3 = null;
        String takeaway4 = null;

        FirebaseCompany.Gemini.Target.Report report = source.getReport();
        if (report != null) {
            overview = limited(report.getOverview(), 1000, "overview");
            List<String> takeaways = report.getKey_takeaways() == null
                    ? List.of()
                    : report.getKey_takeaways();
            takeaway1 = takeaway(takeaways, 0);
            takeaway2 = takeaway(takeaways, 1);
            takeaway3 = takeaway(takeaways, 2);
            takeaway4 = takeaway(takeaways, 3);
        }

        return new TargetData(
                parseDate(required(source.getDate(), "date")),
                institution,
                parsePrice(required(source.getPrice(), "price")),
                limited(source.getRating(), 30, "rating"),
                overview,
                takeaway1,
                takeaway2,
                takeaway3,
                takeaway4);
    }

    private Target from(Period period, TargetData source)
    {
        Target target = new Target();
        target.setPeriod(period);
        target.setDate(Date.valueOf(source.date()));
        target.setInstitution(source.institution());
        target.setPrice(source.price());
        target.setRating(source.rating());
        target.setOverview(source.overview());
        target.setTakeaway1(source.takeaway1());
        target.setTakeaway2(source.takeaway2());
        target.setTakeaway3(source.takeaway3());
        target.setTakeaway4(source.takeaway4());
        return target;
    }

    private String takeaway(List<String> takeaways, int index)
    {
        if (takeaways.size() <= index) return null;
        return limited(takeaways.get(index), 500, "takeaway " + (index + 1));
    }

    private String limited(String value, int maxLength, String field)
    {
        String normalized = nullIfBlank(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is longer than " + maxLength + " characters");
        }
        return normalized;
    }

    private String required(String value, String field)
    {
        String normalized = nullIfBlank(value);
        if (normalized == null) throw new IllegalArgumentException(field + " is missing");
        return normalized;
    }

    private LocalDate parseDate(String value)
    {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new InvalidInputException("invalid target date '" + value + "'", exception);
        }
    }

    private BigDecimal parsePrice(String value)
    {
        BigDecimal price;
        try {
            price = new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new InvalidInputException("invalid target price '" + value + "'", exception);
        }

        BigDecimal normalized = price.stripTrailingZeros();
        int decimalDigits = Math.max(normalized.scale(), 0);
        int integerDigits = Math.max(normalized.precision() - normalized.scale(), 0);
        if (price.compareTo(BigDecimal.ZERO) <= 0 || integerDigits > 6 || decimalDigits > 4) {
            throw new InvalidInputException("invalid target price '" + value + "'");
        }
        return price;
    }

    private LocalDate toLocalDate(Date date)
    {
        return date == null ? null : date.toLocalDate();
    }

    private String nullIfBlank(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private TargetDto from(Target entity)
    {
        TargetDto dto = new TargetDto();
        dto.setId(entity.getId());
        dto.setPeriodId(entity.getPeriod().getId());
        dto.setDate(entity.getDate().toString());
        dto.setInstitution(entity.getInstitution());
        dto.setPrice(entity.getPrice());
        dto.setRating(entity.getRating());
        dto.setOverview(entity.getOverview());
        dto.setTakeaway1(entity.getTakeaway1());
        dto.setTakeaway2(entity.getTakeaway2());
        dto.setTakeaway3(entity.getTakeaway3());
        dto.setTakeaway4(entity.getTakeaway4());
        return dto;
    }

    private record DateWindow(LocalDate start, LocalDate end) {}

    private record DateWindowResolution(DateWindow window, String error) {}

    private record CandidateResult(List<Target> targets, List<String> warnings) {}

    private record TargetData(
            LocalDate date,
            String institution,
            BigDecimal price,
            String rating,
            String overview,
            String takeaway1,
            String takeaway2,
            String takeaway3,
            String takeaway4) {}

    private record TargetIdentity(LocalDate date, String institution, BigDecimal price)
    {
        private static TargetIdentity from(Target target)
        {
            return new TargetIdentity(
                    target.getDate().toLocalDate(),
                    target.getInstitution().trim().toLowerCase(Locale.ROOT),
                    target.getPrice().stripTrailingZeros());
        }

        private static TargetIdentity from(TargetData target)
        {
            return new TargetIdentity(
                    target.date(),
                    target.institution().trim().toLowerCase(Locale.ROOT),
                    target.price().stripTrailingZeros());
        }
    }
}
