package org.kaleta.rest.error;

import io.quarkus.logging.Log;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ServiceFailureExceptionMapper implements ExceptionMapper<ServiceFailureException>
{
    @Override
    public Response toResponse(ServiceFailureException e)
    {
        Log.error(e.getMessage(), e);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
    }
}
