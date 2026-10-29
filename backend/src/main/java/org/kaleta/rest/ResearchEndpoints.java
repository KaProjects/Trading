package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.kaleta.model.PriceIndicators;
import org.kaleta.rest.dto.PeriodContentDto;
import org.kaleta.model.Company;
import org.kaleta.model.Periods;
import org.kaleta.model.Record;
import org.kaleta.persistence.entity.Latest;
import org.kaleta.model.PeriodEstimates;
import org.kaleta.persistence.entity.Estimate;
import org.kaleta.model.TargetStats;
import org.kaleta.rest.dto.PeriodImportDataDto;
import org.kaleta.rest.dto.PeriodImportDto;
import org.kaleta.rest.dto.ResearchDto;
import org.kaleta.rest.validation.ValidPeriodName;
import org.kaleta.rest.validation.ValidId;
import org.kaleta.service.ArithmeticService;
import org.kaleta.service.CompanyService;
import org.kaleta.service.EstimateService;
import org.kaleta.service.FirebaseService;
import org.kaleta.service.ImportService;
import org.kaleta.service.LatestService;
import org.kaleta.service.PeriodContentService;
import org.kaleta.service.PeriodService;
import org.kaleta.service.CaseService;
import org.kaleta.service.RecordService;
import org.kaleta.service.TradeService;
import org.kaleta.service.TargetService;

import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Path("/research")
public class ResearchEndpoints
{
    @Inject
    CompanyService companyService;
    @Inject
    PeriodService periodService;
    @Inject
    RecordService recordService;
    @Inject
    CaseService caseService;
    @Inject
    LatestService latestService;
    @Inject
    ArithmeticService arithmeticService;
    @Inject
    TradeService tradeService;
    @Inject
    FirebaseService firebaseService;
    @Inject
    ImportService importService;
    @Inject
    EstimateService estimateService;
    @Inject
    TargetService targetService;
    @Inject
    PeriodContentService periodContentService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{companyId}")
    public ResearchDto get(@NotNull @ValidId @PathParam("companyId") Long companyId)
    {
        ResearchDto dto = new ResearchDto();
        Company company = companyService.getCompany(companyId);
        dto.setCompany(company);

        Periods periodsModel = periodService.getBy(companyId);
        dto.setFinancials(periodsModel.getFinancials());
        dto.setTtm(periodsModel.getTtm());
        periodsModel.getPeriods().stream()
                .findFirst()
                .filter(period -> period.getFinancial() == null)
                .ifPresent(period -> {
                    PeriodImportDto firebasePeriod =
                            firebaseService.getPeriod(company.getTicker(), period.getName().toString());
                    if (firebasePeriod == null) return;
                    if (firebasePeriod.getReportDate() != null) {
                        period.setExpectedReportDate(Date.valueOf(firebasePeriod.getReportDate()));
                    }
                    period.setReportedInFirebase(firebasePeriod.getIsReported());
                });
        List<Long> periodIds = periodsModel.getPeriods().stream()
                .map(Periods.Period::getId)
                .toList();
        Map<Long, List<PeriodEstimates>> estimates = estimateService.getAllByPeriodIds(periodIds, Estimate.EPS);
        Map<Long, List<PeriodEstimates>> revenueEstimates =
                estimateService.getAllByPeriodIds(periodIds, Estimate.REVENUE);
        Map<Long, TargetStats> targetStats = targetService.getStatistics(periodIds);
        Optional<Long> latestId = periodsModel.getPeriods().stream()
                .findFirst()
                .map(Periods.Period::getId);
        // The overview speaks for the company as it stands, so it is drawn from the newest
        // estimate of the newest period - the first of the list, which is ordered that way.
        latestId
                .map(estimates::get)
                .map(List::getFirst)
                .map(estimateService::createOverview)
                .ifPresent(dto::setEstimateOverview);
        latestId
                .map(revenueEstimates::get)
                .map(List::getFirst)
                .map(estimateService::createOverview)
                .ifPresent(dto::setRevenueEstimateOverview);
        Map<Long, List<PeriodContentDto>> contents = periodContentService.getByPeriodIds(periodIds);
        periodsModel.getPeriods().forEach(period -> {
            dto.addPeriod(
                    period,
                    estimates.getOrDefault(period.getId(), List.of()),
                    revenueEstimates.getOrDefault(period.getId(), List.of()),
                    targetStats.getOrDefault(period.getId(), TargetStats.empty()),
                    contents.getOrDefault(period.getId(), List.of()));
        });

        List<Record> records = recordService.getBy(companyId);
        dto.getRecords().addAll(records);
        dto.getCases().addAll(caseService.coveringCompany(companyId));

        LatestService.SyncResult latestResult = latestService.getSyncedForWithWarnings(companyId);
        Latest latest = latestResult.latest();
        dto.getWarnings().addAll(latestResult.warnings());

        // backup if external service fails
        if (latest == null && !records.isEmpty()) {
            latest = new Latest();
            latest.setDatetime(records.get(0).getDate().toLocalDate().atStartOfDay());
            latest.setPrice(records.get(0).getPrice());
        }

        if (latest != null)
        {
            dto.setLatest(latest);

            PriceIndicators indicators = arithmeticService.computeIndicators(latest, dto.getTtm());
            indicators.setBeta(firebaseService.getBeta(company.getTicker()));
            dto.setIndicators(indicators);

            dto.setAssets(tradeService.getAssets(companyId, latest.getPrice()));
        } else {
            dto.setAssets(tradeService.getAssets(companyId, null));
        }

        String latestPeriodId = dto.getPeriods().stream()
                .findFirst()
                .map(period -> period.getName().toString())
                .orElse(null);
        FirebaseService.ImportCandidatesResult importCandidates =
                firebaseService.getNewerPeriods(company.getTicker(), latestPeriodId);
        dto.setImportablePeriods(importCandidates.periods());
        dto.getWarnings().addAll(importCandidates.warnings());

        return dto;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{companyId}/import/period/{quarterId}")
    public PeriodImportDataDto importPeriod(
            @NotNull @ValidId @PathParam("companyId") Long companyId,
            @NotNull @ValidPeriodName @PathParam("quarterId") String quarterId,
            @Pattern(regexp = "^\\d\\d\\d\\d-\\d\\d$", message = "must match YYYY-MM")
            @QueryParam("endingMonth") String endingMonth)
    {
        return importService.getPeriod(companyId, quarterId, endingMonth);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{companyId}/import/estimate/{periodId}")
    public org.kaleta.rest.dto.EstimateImportDto importEstimate(
            @NotNull @ValidId @PathParam("companyId") Long companyId,
            @NotNull @ValidId @PathParam("periodId") Long periodId)
    {
        return importService.getEstimate(companyId, periodId);
    }

}
