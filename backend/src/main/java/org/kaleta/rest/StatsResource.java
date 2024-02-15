package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.dto.StatsUiByCompanyDto;
import org.kaleta.dto.StatsUiByPeriodDto;
import org.kaleta.model.StatsByCompany;
import org.kaleta.model.StatsByPeriod;
import org.kaleta.service.StatsService;

import java.util.List;

@Path("/stats")
public class StatsResource
{
    @Inject
    StatsService statsService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/company")
    public Response getCompanies(
            @QueryParam("year") String year,
            @QueryParam("sort") String sort,
            @QueryParam("sector") String sector)
    {
        return Endpoint.process(() -> {
            if (year != null) Validator.validateYear(year);
            if (sector != null) Validator.validateSector(sector);
        }, () -> {
            List<StatsByCompany> companyStats = statsService.getByCompany(year, sector);
            if (sort != null && sort.equals("percentage")) {
                companyStats.sort(StatsByCompany::comparePercentageTo);
            } else {
                companyStats.sort(StatsByCompany::compareProfitTo);
            }
            StatsUiByCompanyDto dto = StatsUiByCompanyDto.from(companyStats);
            dto.setSums(statsService.computeCompanySums(companyStats));
            return dto;
        });
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/monthly")
    public Response getMonthly(@QueryParam("companyId") String companyId, @QueryParam("sector") String sector)
    {
        return Endpoint.process(() -> {
            if (companyId != null) Validator.validateUuid(companyId);
            if (sector != null) Validator.validateSector(sector);
        }, () -> {
            List<StatsByPeriod> monthlyStats = statsService.getByPeriod(companyId, true, sector);
            StatsUiByPeriodDto dto = StatsUiByPeriodDto.from(monthlyStats, true);
            dto.setSums(statsService.computePeriodSums(monthlyStats));
            return dto;
        });
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/yearly")
    public Response getYearly(@QueryParam("companyId") String companyId, @QueryParam("sector") String sector)
    {
        return Endpoint.process(() -> {
            if (companyId != null) Validator.validateUuid(companyId);
            if (sector != null) Validator.validateSector(sector);
        }, () -> {
            List<StatsByPeriod> yearlyStats = statsService.getByPeriod(companyId, false, sector);
            StatsUiByPeriodDto dto = StatsUiByPeriodDto.from(yearlyStats, false);
            dto.setSums(statsService.computePeriodSums(yearlyStats));
            return dto;
        });
    }
}
