package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.rest.dto.EstimateCreateDto;
import org.kaleta.rest.validation.ValidId;
import org.kaleta.service.EstimateService;

@Path("/estimate")
public class EstimateEndpoints
{
    @Inject
    EstimateService estimateService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{periodId}/latest")
    public Response getLatest(@NotNull @ValidId @PathParam("periodId") Long periodId)
    {
        return estimateService.getLatest(periodId)
                .map(estimate -> Response.ok(estimate).build())
                .orElseGet(() -> Response.noContent().build());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{periodId}")
    public Response create(
            @NotNull @ValidId @PathParam("periodId") Long periodId,
            @Valid @NotNull EstimateCreateDto dto)
    {
        estimateService.create(periodId, dto);
        return Response.status(Response.Status.CREATED).build();
    }
}
