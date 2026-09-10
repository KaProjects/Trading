package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.kaleta.rest.dto.OutperformersDto;
import org.kaleta.service.OutperformersService;

@Path("/outperformers")
public class OutperformersEndpoints
{
    @Inject
    OutperformersService outperformersService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public OutperformersDto get()
    {
        return outperformersService.get();
    }
}
