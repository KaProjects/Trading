package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.rest.dto.CaseCreateDto;
import org.kaleta.rest.dto.CaseUpdateDto;
import org.kaleta.rest.validation.ValidId;
import org.kaleta.service.CaseService;

@Path("/case")
@Produces(MediaType.APPLICATION_JSON)
public class CaseEndpoints
{
    @Inject
    CaseService caseService;

    @GET
    public Response getAll()
    {
        return Response.ok(caseService.getAll()).build();
    }

    @GET
    @Path("/{caseId}")
    public Response get(@NotNull @ValidId @PathParam("caseId") Long caseId)
    {
        return Response.ok(caseService.get(caseId)).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Valid @NotNull CaseCreateDto dto)
    {
        return Response.status(Response.Status.CREATED).entity(caseService.create(dto)).build();
    }

    @PUT
    @Path("/{caseId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(
            @NotNull @ValidId @PathParam("caseId") Long caseId,
            @Valid @NotNull CaseUpdateDto dto)
    {
        return Response.ok(caseService.update(caseId, dto)).build();
    }

    @DELETE
    @Path("/{caseId}")
    public Response delete(@NotNull @ValidId @PathParam("caseId") Long caseId)
    {
        caseService.delete(caseId);
        return Response.ok().build();
    }
}
