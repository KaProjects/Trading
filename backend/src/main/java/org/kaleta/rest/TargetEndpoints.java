package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.rest.dto.TargetCreateDto;
import org.kaleta.rest.validation.ValidId;
import org.kaleta.service.TargetService;

@Path("/target")
@Produces(MediaType.APPLICATION_JSON)
public class TargetEndpoints
{
    @Inject
    TargetService targetService;

    @GET
    @Path("/{periodId}")
    public Response getAll(@NotNull @ValidId @PathParam("periodId") Long periodId)
    {
        return Response.ok(targetService.getAll(periodId)).build();
    }

    @POST
    @Path("/{periodId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(
            @NotNull @ValidId @PathParam("periodId") Long periodId,
            @Valid @NotNull TargetCreateDto dto)
    {
        return Response.status(Response.Status.CREATED)
                .entity(targetService.create(periodId, dto))
                .build();
    }

    @DELETE
    @Path("/{targetId}")
    public Response delete(@NotNull @ValidId @PathParam("targetId") Long targetId)
    {
        targetService.delete(targetId);
        return Response.ok().build();
    }

    @GET
    @Path("/company/{companyId}/sync/counts")
    public Response countImportCandidatesByCompany(
            @NotNull @ValidId @PathParam("companyId") Long companyId)
    {
        return Response.ok(targetService.countImportCandidatesByCompany(companyId)).build();
    }

    @GET
    @Path("/{periodId}/sync/count")
    public Response countImportCandidates(
            @NotNull @ValidId @PathParam("periodId") Long periodId)
    {
        return Response.ok(targetService.countImportCandidates(periodId)).build();
    }

    @POST
    @Path("/{periodId}/sync")
    public Response sync(@NotNull @ValidId @PathParam("periodId") Long periodId)
    {
        return Response.ok(targetService.sync(periodId)).build();
    }
}
