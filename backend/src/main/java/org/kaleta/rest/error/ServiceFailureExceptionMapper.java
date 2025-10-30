package org.kaleta.rest.error;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.kaleta.service.ServiceFailureException;

@Provider
public class ServiceFailureExceptionMapper implements ExceptionMapper<ServiceFailureException>
{
    @Override
    public Response toResponse(ServiceFailureException e)
    {
        return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
    }
}
