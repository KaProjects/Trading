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
import org.kaleta.model.Company;
import org.kaleta.model.Periods;
import org.kaleta.model.Record;
import org.kaleta.persistence.entity.Latest;
import org.kaleta.model.PeriodEstimates;
import org.kaleta.model.TargetStats;
import org.kaleta.rest.dto.PeriodImportDataDto;
import org.kaleta.rest.dto.ResearchDto;
import org.kaleta.rest.validation.ValidPeriodName;
import org.kaleta.rest.validation.ValidId;
import org.kaleta.service.ArithmeticService;
import org.kaleta.service.CompanyService;
import org.kaleta.service.EstimateService;
import org.kaleta.service.FirebaseService;
import org.kaleta.service.ImportService;
import org.kaleta.service.LatestService;
import org.kaleta.service.PeriodService;
import org.kaleta.service.RecordService;
import org.kaleta.service.TradeService;
import org.kaleta.service.TargetService;

import java.util.List;
import java.util.Map;

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
        List<Long> periodIds = periodsModel.getPeriods().stream()
                .map(Periods.Period::getId)
                .toList();
        Map<Long, PeriodEstimates> estimates = estimateService.getLatestByPeriodIds(periodIds);
        Map<Long, TargetStats> targetStats = targetService.getStatistics(periodIds);
        periodsModel.getPeriods().stream()
                .findFirst()
                .map(Periods.Period::getId)
                .map(estimates::get)
                .map(estimateService::createOverview)
                .ifPresent(dto::setEstimateOverview);
        periodsModel.getPeriods().forEach(period -> {
            dto.addPeriod(
                    period,
                    estimates.get(period.getId()),
                    targetStats.getOrDefault(period.getId(), TargetStats.empty()));
        });

        List<Record> records = recordService.getBy(companyId);
        dto.getRecords().addAll(records);

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

            if (dto.getTtm() != null && dto.getTtm().getShares() != null)
            {
                dto.setIndicators(arithmeticService.computeIndicators(latest, dto.getTtm()));
            }

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
