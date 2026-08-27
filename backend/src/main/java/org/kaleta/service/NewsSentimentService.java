package org.kaleta.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.persistence.api.PeriodDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Period;
import org.kaleta.rest.dto.NewsSentimentDto;
import org.kaleta.rest.dto.NewsSentimentLatestDto;
import org.kaleta.rest.dto.NewsSentimentPeriodDto;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class NewsSentimentService
{
    private static final int MAX_TAKEAWAYS = 5;
    private static final int MAX_TAKEAWAY_LENGTH = 500;

    @Inject
    CompanyService companyService;
    @Inject
    PeriodService periodService;
    @Inject
    PeriodDao periodDao;
    @Inject
    FirebaseService firebaseService;
    @Inject
    PeriodDateWindowService periodDateWindowService;

    public NewsSentimentLatestDto getLatest(Long companyId)
    {
        Company company = companyService.findEntity(companyId);
        List<String> warnings = new ArrayList<>();
        FirebaseService.NewsSentimentsResult firebaseResult =
                firebaseService.getLatestNewsSentiments(company.getTicker());
        warnings.addAll(firebaseResult.warnings());

        NewsSentimentDto latest = map(company.getTicker(), firebaseResult.records(), warnings).stream()
                .max(Comparator.comparing(NewsSentimentDto::date))
                .orElse(null);
        return new NewsSentimentLatestDto(latest, warnings);
    }

    public NewsSentimentPeriodDto getByPeriod(Long periodId)
    {
        Period period = periodService.get(periodId);
        String ticker = period.getCompany().getTicker();
        List<String> warnings = new ArrayList<>();
        PeriodDateWindowService.Resolution resolution = periodDateWindowService.resolve(
                period,
                periodDao.list(period.getCompany().getId()));

        if (resolution.window() == null) {
            String warning = "Firebase news sentiment for " + ticker + " " + period.getName()
                    + " could not be loaded: " + resolution.error();
            Log.warn(warning);
            return new NewsSentimentPeriodDto(List.of(), null, List.of(warning));
        }

        PeriodDateWindowService.DateWindow window = resolution.window();
        FirebaseService.NewsSentimentsResult firebaseResult = firebaseService.getNewsSentiments(
                ticker,
                window.start(),
                window.end());
        warnings.addAll(firebaseResult.warnings());

        List<NewsSentimentDto> records = map(ticker, firebaseResult.records(), warnings).stream()
                .filter(record -> window.contains(record.date()))
                .sorted(Comparator.comparing(NewsSentimentDto::date).reversed())
                .toList();
        return new NewsSentimentPeriodDto(
                records,
                new NewsSentimentPeriodDto.Window(window.start(), window.end()),
                warnings);
    }

    private List<NewsSentimentDto> map(
            String ticker,
            Map<String, FirebaseCompany.NewsSentiment> records,
            List<String> warnings)
    {
        List<NewsSentimentDto> result = new ArrayList<>();
        for (Map.Entry<String, FirebaseCompany.NewsSentiment> entry : records.entrySet()) {
            try {
                result.add(from(entry.getKey(), entry.getValue()));
            } catch (RuntimeException exception) {
                String warning = ExternalWarnings.unavailable(
                        "Firebase news sentiment record '" + entry.getKey() + "' for " + ticker,
                        exception);
                Log.warn(warning, exception);
                warnings.add(warning);
            }
        }
        return List.copyOf(result);
    }

    private NewsSentimentDto from(String id, FirebaseCompany.NewsSentiment source)
    {
        if (id == null || id.length() < 10) {
            throw new IllegalArgumentException("record key does not start with YYYY-MM-DD");
        }
        if (source == null) {
            throw new IllegalArgumentException("record is null");
        }
        Map<String, Integer> stats = source.getStats();
        if (stats == null) {
            throw new IllegalArgumentException("sentiment statistics are missing");
        }

        LocalDate date;
        try {
            date = LocalDate.parse(id.substring(0, 10));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("record key does not start with YYYY-MM-DD", exception);
        }

        Map<String, Integer> validatedStats = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            String label = entry.getKey() == null ? null : entry.getKey().trim();
            Integer count = entry.getValue();
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("sentiment label is blank");
            }
            if (count == null || count < 0) {
                throw new IllegalArgumentException("sentiment count for '" + label + "' is invalid");
            }
            total = Math.addExact(total, count);
            validatedStats.put(label, count);
        }

        List<String> keyTakeaways = source.getKey_takeaways() == null
                ? List.of()
                : source.getKey_takeaways().stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .toList();
        if (keyTakeaways.size() > MAX_TAKEAWAYS) {
            throw new IllegalArgumentException("more than " + MAX_TAKEAWAYS + " key takeaways");
        }
        if (keyTakeaways.stream().anyMatch(value -> value.length() > MAX_TAKEAWAY_LENGTH)) {
            throw new IllegalArgumentException(
                    "key takeaway is longer than " + MAX_TAKEAWAY_LENGTH + " characters");
        }

        return new NewsSentimentDto(id, date, total, validatedStats, keyTakeaways);
    }
}
