package org.kaleta.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kaleta.firebase.FirebaseStore;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.model.FirebaseInstitution;
import org.kaleta.persistence.entity.CompanyWithStats;
import org.kaleta.rest.dto.FirebaseCompanyDiffDto;
import org.kaleta.rest.error.InvalidInputException;
import org.kaleta.rest.dto.FirebaseInstitutionsDto;
import org.kaleta.rest.dto.FirebaseStatsDto;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class FirebaseDiffService
{
    @Inject
    FirebaseService firebaseService;
    @Inject
    FirebaseStore firebaseStore;
    @Inject
    CompanyService companyService;

    public FirebaseCompanyDiffDto getCompanyDiff()
    {
        FirebaseService.AllCompaniesResult result = firebaseService.getAllCompanies();
        Set<String> known = databaseTickers();

        List<String> onlyInFirebase = result.companies().keySet().stream()
                .filter(ticker -> !known.contains(normalize(ticker)))
                .sorted()
                .toList();

        return new FirebaseCompanyDiffDto(onlyInFirebase, result.warnings());
    }

    public FirebaseStatsDto getStats()
    {
        FirebaseService.AllCompaniesResult result = firebaseService.getAllCompanies();
        Set<String> known = databaseTickers();

        List<FirebaseStatsDto.CompanyStats> companies = result.companies().entrySet().stream()
                .map(entry -> toStats(entry.getKey(), entry.getValue(), known))
                .sorted(Comparator.comparing(FirebaseStatsDto.CompanyStats::ticker))
                .toList();

        return new FirebaseStatsDto(companies, result.warnings());
    }

    public FirebaseCompany getCompany(String ticker)
    {
        return firebaseStore.findCompany(ticker)
                .orElseThrow(() -> new InvalidInputException(
                        "Firebase has no data for ticker '" + ticker + "'"));
    }

    public FirebaseInstitutionsDto getInstitutions()
    {
        Map<String, FirebaseInstitution> institutions;
        try {
            institutions = firebaseStore.findAllInstitutions();
        } catch (RuntimeException exception) {
            String warning = ExternalWarnings.unavailable("Firebase institutions", exception);
            Log.warn(warning, exception);
            return new FirebaseInstitutionsDto(List.of(), List.of(warning));
        }

        List<FirebaseInstitutionsDto.Institution> sorted = institutions.entrySet().stream()
                .map(entry -> toInstitution(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(
                        FirebaseInstitutionsDto.Institution::name,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new FirebaseInstitutionsDto(sorted, List.of());
    }

    public void mergeInstitutions(String sourceKey, String targetKey)
    {
        if (sourceKey.equals(targetKey)) {
            throw new InvalidInputException("an institution cannot be merged into itself");
        }

        Map<String, FirebaseInstitution> institutions = firebaseStore.findAllInstitutions();
        FirebaseInstitution source = institutions.get(sourceKey);
        FirebaseInstitution target = institutions.get(targetKey);
        if (source == null) {
            throw new InvalidInputException("institution '" + sourceKey + "' was not found");
        }
        if (target == null) {
            throw new InvalidInputException("institution '" + targetKey + "' was not found");
        }

        Map<String, String> aliases = new LinkedHashMap<>();
        if (source.getAliases() != null) aliases.putAll(source.getAliases());
        aliases.putIfAbsent(sourceKey, source.getName() == null ? sourceKey : source.getName());

        firebaseStore.mergeInstitutions(sourceKey, targetKey, aliases);
    }

    public void updateInstitutionFlags(String key, boolean enabled, boolean trusted)
    {
        if (!firebaseStore.findAllInstitutions().containsKey(key)) {
            throw new InvalidInputException("institution '" + key + "' was not found");
        }
        firebaseStore.updateInstitutionFlags(key, enabled, trusted);
    }

    private FirebaseInstitutionsDto.Institution toInstitution(String key, FirebaseInstitution institution)
    {
        List<String> aliases = institution.getAliases() == null
                ? List.of()
                : institution.getAliases().values().stream().sorted().toList();
        String name = institution.getName() == null ? key : institution.getName();

        return new FirebaseInstitutionsDto.Institution(
                key,
                name,
                institution.isEnabled(),
                institution.isTrusted(),
                aliases);
    }

    private FirebaseStatsDto.CompanyStats toStats(String ticker, FirebaseCompany company, Set<String> known)
    {
        int quarters = 0;
        int targets = 0;
        if (company != null && company.getGemini() != null) {
            quarters = size(company.getGemini().getQuarters());
            targets = size(company.getGemini().getTargets());
        }

        int earnings = 0;
        if (company != null && company.getFhe() != null) {
            earnings = company.getFhe().values().stream()
                    .mapToInt(FirebaseDiffService::size)
                    .sum();
        }

        int newsSentiments = company == null ? 0 : size(company.getPgn());

        return new FirebaseStatsDto.CompanyStats(
                ticker,
                known.contains(normalize(ticker)),
                quarters,
                targets,
                earnings,
                newsSentiments);
    }

    private Set<String> databaseTickers()
    {
        return companyService.getAllWithStats().stream()
                .map(CompanyWithStats::getTicker)
                .map(FirebaseDiffService::normalize)
                .collect(Collectors.toSet());
    }

    private static String normalize(String ticker)
    {
        return ticker == null ? "" : ticker.trim().toUpperCase(Locale.ROOT).replace(".", "-");
    }

    private static int size(Map<String, ?> values)
    {
        return values == null ? 0 : values.size();
    }
}
