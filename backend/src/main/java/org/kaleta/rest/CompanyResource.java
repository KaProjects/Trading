package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.dto.CompanyDto;
import org.kaleta.service.CompanyService;

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
}
