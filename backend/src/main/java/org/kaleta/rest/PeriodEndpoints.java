package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.rest.dto.PeriodCreateDto;
import org.kaleta.rest.dto.PeriodUpdateDto;
import org.kaleta.service.PeriodService;

@Path("/period")
public class PeriodEndpoints
{
    @Inject
    PeriodService periodService;

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response update(@Valid @NotNull PeriodUpdateDto periodDto)
    {
        return Endpoint.process(
            () -> {},
            () -> {
                periodService.update(periodDto);
                return Response.noContent().build();
            });
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response create(@Valid @NotNull PeriodCreateDto periodCreateDto)
    {
        return Endpoint.process(
            () -> {},
            () -> {
                periodService.create(periodCreateDto);
                return Response.status(Response.Status.CREATED).build();
            });
    }
}
