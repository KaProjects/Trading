package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.model.Company;
import org.kaleta.model.Periods;
import org.kaleta.model.Record;
import org.kaleta.persistence.entity.Latest;
import org.kaleta.model.PeriodEstimates;
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
import org.kaleta.service.PeriodService;
import org.kaleta.service.RecordService;
import org.kaleta.service.TradeService;

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

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{companyId}")
    public Response get(@NotNull @ValidId @PathParam("companyId") Long companyId)
    {
        ResearchDto dto = new ResearchDto();
        Company company = companyService.getCompany(companyId);
        dto.setCompany(company);

        Periods periodsModel = periodService.getBy(companyId);
        dto.setFinancials(periodsModel.getFinancials());
        dto.setTtm(periodsModel.getTtm());
        Map<Long, PeriodEstimates> estimates = estimateService.getLatestByPeriodIds(
                periodsModel.getPeriods().stream()
                        .map(Periods.Period::getId)
                        .toList());
        periodsModel.getPeriods().forEach(period -> {
            PeriodImportDto cachedData = firebaseService.getPeriod(company.getTicker(), period.getName().toString());
            dto.addPeriod(period, cachedData, estimates.get(period.getId()));
        });

        List<Record> records = recordService.getBy(companyId);
        dto.getRecords().addAll(records);

        Latest latest = latestService.getSyncedFor(companyId);

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

        String latestPeriodId = dto.getPeriods().stream().findFirst().map(p -> p.getName().toString()).orElse(null);
        dto.setImportablePeriods(firebaseService.getNewerPeriods(dto.getCompany().getTicker(), latestPeriodId));

        return Response.ok().entity(dto).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{companyId}/import/period/{quarterId}")
    public Response importPeriod(
            @NotNull @ValidId @PathParam("companyId") Long companyId,
            @NotNull @ValidPeriodName @PathParam("quarterId") String quarterId)
    {
        PeriodImportDataDto data = importService.getPeriod(companyId, quarterId);
        return Response.ok().entity(data).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{companyId}/import/estimate/{periodId}")
    public Response importEstimate(
            @NotNull @ValidId @PathParam("companyId") Long companyId,
            @NotNull @ValidId @PathParam("periodId") Long periodId)
    {
        return Response.ok().entity(importService.getEstimate(companyId, periodId)).build();
    }

}
