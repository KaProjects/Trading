package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.dto.StatsUiByCompanyDto;
import org.kaleta.dto.StatsUiByMonthDto;
import org.kaleta.model.StatsByCompany;
import org.kaleta.model.StatsByMonth;
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
    public Response getCompanies()
    {
        return Endpoint.process(() -> {}, () -> {
            List<StatsByCompany> companyStats = statsService.getByCompany();
            StatsUiByCompanyDto dto = StatsUiByCompanyDto.from(companyStats);
            dto.setSums(statsService.computeCompanySums(companyStats));
            return dto;
        });
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/monthly")
    public Response getMonthly()
    {
        return Endpoint.process(() -> {}, () -> {
            List<StatsByMonth> monthlyStats = statsService.getByMonth();
            StatsUiByMonthDto dto = StatsUiByMonthDto.from(monthlyStats);
            dto.setSums(statsService.computeMonthlySums(monthlyStats));
            return dto;
        });
    }
}
