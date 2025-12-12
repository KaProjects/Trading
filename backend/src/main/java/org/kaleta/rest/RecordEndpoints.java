package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.rest.dto.RecordCreateDto;
import org.kaleta.rest.dto.RecordUpdateDto;
import org.kaleta.rest.validation.ValidUuid;
import org.kaleta.service.RecordService;

@Path("/record")
public class RecordEndpoints
{
    @Inject
    RecordService recordService;

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response update(@Valid @NotNull RecordUpdateDto recordUpdateDto)
    {
        recordService.update(recordUpdateDto);
        return Response.noContent().build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response create(@Valid @NotNull RecordCreateDto recordCreateDto)
    {
        recordService.create(recordCreateDto);
        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @Path("/{recordId}")
    public Response delete(@NotNull @ValidUuid @PathParam("recordId") String recordId)
    {
        recordService.delete(recordId);
        return Response.status(Response.Status.OK).build();
    }
}
