package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.dto.PeriodCreateDto;
import org.kaleta.dto.PeriodDto;
import org.kaleta.service.PeriodService;

@Path("/period")
public class PeriodResource
{
    @Inject
    PeriodService periodService;

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response update(PeriodDto periodDto)
    {
        return Endpoint.process(
            () -> {
                Validator.validatePayload(periodDto);
                Validator.validateUpdatePeriodDto(periodDto);
            },
            () -> {
                periodService.update(periodDto);
                return Response.noContent().build();
            });
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response create(PeriodCreateDto periodCreateDto)
    {
        return Endpoint.process(
            () -> {
                Validator.validatePayload(periodCreateDto);
                Validator.validatePeriodCreateDto(periodCreateDto);
            },
            () -> {
                periodService.create(periodCreateDto);
                return Response.status(Response.Status.CREATED).build();
            });
    }
}
