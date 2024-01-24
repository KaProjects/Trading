package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.dto.CompanyDto;
import org.kaleta.dto.RecordsUiCompanyListsDto;
import org.kaleta.model.CompanyInfo;
import org.kaleta.service.CompanyService;

import java.util.List;

@Path("/company")
public class CompanyResource
{
    @Inject
    CompanyService companyService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response getCompanies()
    {
        return Endpoint.process(() -> {}, () -> CompanyDto.from(companyService.getCompanies()));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/lists")
    public Response getCompanyLists()
    {
        return Endpoint.process(() -> {}, () -> {
            List<CompanyInfo> infos = companyService.getCompaniesInfo();
            RecordsUiCompanyListsDto dto = new RecordsUiCompanyListsDto();
            dto.addWatchingOldestReview(infos);
            dto.addOwnedWithoutStrategy(infos);
            dto.addNotWatching(infos);
            return dto;
        });
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response updateCompany(CompanyDto companyDto)
    {
        return Endpoint.process(
            () -> {
                Validator.validatePayload(companyDto);
                Validator.validateUuid(companyDto.getId());
            },
            () -> {
                companyService.updateCompany(companyDto);
                return Response.noContent().build();
            });
    }
}
