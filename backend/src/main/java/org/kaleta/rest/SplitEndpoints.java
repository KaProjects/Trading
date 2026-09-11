package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.rest.dto.SplitApplyDto;
import org.kaleta.service.SplitService;

@Path("/split")
@Produces(MediaType.APPLICATION_JSON)
public class SplitEndpoints
{
    @Inject
    SplitService splitService;

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response applySplit(@Valid @NotNull SplitApplyDto dto)
    {
        return Response.ok(splitService.apply(dto)).build();
    }
}
