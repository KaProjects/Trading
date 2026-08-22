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
import org.kaleta.rest.dto.TodoCreateDto;
import org.kaleta.rest.validation.ValidId;
import org.kaleta.service.TodoService;

@Path("/todo")
@Produces(MediaType.APPLICATION_JSON)
public class TodoEndpoints
{
    @Inject
    TodoService todoService;

    @GET
    public Response getAll()
    {
        return Response.ok(todoService.getAll()).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Valid @NotNull TodoCreateDto dto)
    {
        return Response.status(Response.Status.CREATED).entity(todoService.create(dto)).build();
    }

    @PUT
    @Path("/{todoId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(
            @NotNull @ValidId @PathParam("todoId") Long todoId,
            @Valid @NotNull TodoCreateDto dto)
    {
        return Response.ok(todoService.update(todoId, dto)).build();
    }

    @DELETE
    @Path("/{todoId}")
    public Response delete(@NotNull @ValidId @PathParam("todoId") Long todoId)
    {
        todoService.delete(todoId);
        return Response.ok().build();
    }
}
