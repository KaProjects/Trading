package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.rest.dto.FirebaseCompanyDiffDto;
import org.kaleta.rest.dto.FirebaseInstitutionsDto;
import org.kaleta.rest.dto.FirebaseStatsDto;
import org.kaleta.rest.dto.InstitutionFlagsDto;
import org.kaleta.rest.dto.InstitutionMergeDto;
import org.kaleta.service.FirebaseDiffService;

@Path("/firebase")
@Produces(MediaType.APPLICATION_JSON)
public class FirebaseDiffEndpoints
{
    @Inject
    FirebaseDiffService firebaseDiffService;

    @GET
    @Path("/companies")
    public FirebaseCompanyDiffDto getCompanyDiff()
    {
        return firebaseDiffService.getCompanyDiff();
    }

    @GET
    @Path("/company/{ticker}")
    public FirebaseCompany getCompany(
            @NotNull @org.kaleta.rest.validation.ValidTicker @PathParam("ticker") String ticker)
    {
        return firebaseDiffService.getCompany(ticker);
    }

    @GET
    @Path("/stats")
    public FirebaseStatsDto getStats()
    {
        return firebaseDiffService.getStats();
    }

    @POST
    @Path("/institutions/merge")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response mergeInstitutions(@Valid @NotNull InstitutionMergeDto dto)
    {
        firebaseDiffService.mergeInstitutions(dto.getSourceKey(), dto.getTargetKey());
        return Response.noContent().build();
    }

    @PUT
    @Path("/institutions/{key}/flags")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateInstitutionFlags(
            @NotNull @PathParam("key") String key,
            @Valid @NotNull InstitutionFlagsDto dto)
    {
        firebaseDiffService.updateInstitutionFlags(key, dto.getEnabled(), dto.getTrusted());
        return Response.noContent().build();
    }

    @GET
    @Path("/institutions")
    public FirebaseInstitutionsDto getInstitutions()
    {
        return firebaseDiffService.getInstitutions();
    }
}
