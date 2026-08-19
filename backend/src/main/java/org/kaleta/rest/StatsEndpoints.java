package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.kaleta.model.CompanyStats;
import org.kaleta.model.PeriodFrequency;
import org.kaleta.model.PeriodStats;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.rest.validation.ValidId;
import org.kaleta.rest.validation.ValueOfEnum;
import org.kaleta.service.StatsService;

@Path("/stats")
public class StatsEndpoints
{
    @Inject
    StatsService statsService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/company")
    public CompanyStats getCompanies(
            @Pattern(regexp = "^\\d\\d\\d\\d$", message = "must match YYYY")
            @QueryParam("year")
            String year,
            @ValueOfEnum(enumClass = CompanyStats.Sort.class)
            @QueryParam("sort")
            String sort,
            @ValueOfEnum(enumClass = Sector.class)
            @QueryParam("sector")
            String sector
    ) {
        CompanyStats model = statsService.getByCompany(year, sector);
        model.sort(sort == null ? CompanyStats.Sort.PROFIT_USD : CompanyStats.Sort.valueOf(sort));
        return model;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/monthly")
    public PeriodStats getMonthly(
            @ValidId
            @QueryParam("companyId")
            Long companyId,
            @ValueOfEnum(enumClass = Sector.class)
            @QueryParam("sector")
            String sector
    ) {
        PeriodStats model = statsService.getByPeriod(PeriodFrequency.MONTHLY, companyId, sector);
        return model;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/quarterly")
    public PeriodStats getQuarterly(
            @ValidId
            @QueryParam("companyId")
            Long companyId,
            @ValueOfEnum(enumClass = Sector.class)
            @QueryParam("sector")
            String sector
    ) {
        PeriodStats model = statsService.getByPeriod(PeriodFrequency.QUARTERLY, companyId, sector);
        return model;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/yearly")
    public PeriodStats getYearly(
            @ValidId
            @QueryParam("companyId")
            Long companyId,
            @ValueOfEnum(enumClass = Sector.class)
            @QueryParam("sector")
            String sector
    ) {
        PeriodStats model = statsService.getByPeriod(PeriodFrequency.YEARLY, companyId, sector);
        return model;
    }
}
