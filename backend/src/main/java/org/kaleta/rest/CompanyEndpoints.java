package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.dto.RecordsUiCompanyListsDto;
import org.kaleta.model.CompanyAggregates;
import org.kaleta.model.CompanyInfo;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Sector;
import org.kaleta.rest.dto.CompanyCreateDto;
import org.kaleta.rest.dto.CompanyUpdateDto;
import org.kaleta.rest.dto.CompanyValuesDto;
import org.kaleta.rest.validation.ValueOfEnum;
import org.kaleta.service.CompanyService;

import java.util.List;

@Path("/company")
public class CompanyEndpoints
{
    @Inject
    CompanyService companyService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/values")
    public Response getCompanyValues()
    {
        return Response.ok(new CompanyValuesDto(companyService.getCompanies())).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response getCompaniesWithAggregates(
            @ValueOfEnum(enumClass = CompanyAggregates.Sort.class)
            @QueryParam("sort")
            String sort,
            @ValueOfEnum(enumClass = Currency.class)
            @QueryParam("currency")
            String currency,
            @ValueOfEnum(enumClass = Sector.class)
            @QueryParam("sector")
            String sector
    ) {
        CompanyAggregates dto = companyService.getCompaniesWithAggregates(currency, sector);
        dto.sort((sort == null) ? CompanyAggregates.Sort.TICKER : CompanyAggregates.Sort.valueOf(sort));
        return Response.ok().entity(dto).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/lists")
    public Response getCompanyLists()
    {
        List<CompanyInfo> infos = companyService.getCompaniesInfo();
        RecordsUiCompanyListsDto dto = new RecordsUiCompanyListsDto();
        dto.addWatchingOldestReview(infos);
        dto.addOwnedWithoutStrategy(infos);
        dto.addNotWatching(infos);
        dto.addSectors(infos);
        return Response.ok(dto).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response updateCompany(@NotNull @Valid CompanyUpdateDto dto)
    {
        companyService.update(dto);
        return Response.noContent().build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response createCompany(@NotNull @Valid CompanyCreateDto dto)
    {
        companyService.create(dto);
        return Response.status(Response.Status.CREATED).build();
    }
}
