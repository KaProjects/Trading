package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.dto.CompanyDto;
import org.kaleta.model.Periods;
import org.kaleta.model.PriceIndicators;
import org.kaleta.model.Record;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Latest;
import org.kaleta.rest.dto.ResearchDto;
import org.kaleta.rest.validation.ValidUuid;
import org.kaleta.service.ArithmeticService;
import org.kaleta.service.CompanyService;
import org.kaleta.service.LatestService;
import org.kaleta.service.PeriodService;
import org.kaleta.service.RecordService;
import org.kaleta.service.TradeService;

import java.util.List;

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

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{companyId}")
    public Response get(@NotNull @ValidUuid @PathParam("companyId") String companyId)
    {
        ResearchDto dto = new ResearchDto();
        Company company = companyService.getCompany(companyId);
        dto.setCompany(CompanyDto.from(company));

        dto.setPeriods(periodService.getBy(companyId));

        List<Record> records = recordService.getBy(companyId);
        dto.getRecords().addAll(records);

        Latest latest = latestService.getSyncedFor(company);

        // backup if external service fails
        if (latest == null && !records.isEmpty()) {
            latest = new Latest();
            latest.setDatetime(records.get(0).getDate().toLocalDate().atStartOfDay());
            latest.setPrice(records.get(0).getPrice());
        }

        if (latest != null)
        {
            dto.setLatest(latest);

            PriceIndicators indicators = new PriceIndicators();

            indicators.setDatetime(latest.getDatetime());
            indicators.setPrice(latest.getPrice());

            Periods.Financial ttm = dto.getPeriods().getTtm();

            if (ttm != null && ttm.getShares() != null)
            {
                indicators.setShares(ttm.getShares());
                indicators.setMarketCap(indicators.getPrice().multiply(indicators.getShares()));

                indicators.setTtm(arithmeticService.computeFinancialRatios(indicators.getMarketCap(), ttm));
            }

            dto.setIndicators(indicators);

            dto.setAssets(tradeService.getAssets(companyId, latest.getPrice()));
        } else {
            dto.setAssets(tradeService.getAssets(companyId, null));
        }

        return Response.ok().entity(dto).build();
    }
}
