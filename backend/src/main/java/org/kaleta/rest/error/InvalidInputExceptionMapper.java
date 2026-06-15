package org.kaleta.rest.error;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidInputExceptionMapper implements ExceptionMapper<InvalidInputException>
{
    @Override
    public Response toResponse(InvalidInputException e)
    {
        return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
    }
}
