package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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
    public Response getCompanies(@QueryParam("currency") String currency)
    {
        return Endpoint.process(() -> {
            if (currency != null) Validator.validateCurrency(currency);
        }, () -> {
            List<StatsByCompany> companyStats = statsService.getByCompany(currency);
            StatsUiByCompanyDto dto = StatsUiByCompanyDto.from(companyStats);
            dto.setSums(statsService.computeCompanySums(companyStats));
            return dto;
        });
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/monthly")
    public Response getMonthly(@QueryParam("currency") String currency)
    {
        return Endpoint.process(() -> {
            if (currency != null) Validator.validateCurrency(currency);
        }, () -> {
            List<StatsByMonth> monthlyStats = statsService.getByMonth(currency);
            StatsUiByMonthDto dto = StatsUiByMonthDto.from(monthlyStats);
            dto.setSums(statsService.computeMonthlySums(monthlyStats));
            return dto;
        });
    }
}
