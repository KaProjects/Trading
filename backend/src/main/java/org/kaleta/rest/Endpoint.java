package org.kaleta.rest;

import jakarta.ws.rs.core.Response;
import org.kaleta.rest.error.InvalidInputException;
import org.kaleta.rest.error.ServiceFailureException;

import java.util.function.Supplier;
import io.quarkus.logging.Log;

public class Endpoint
{
    @Deprecated // TODO use jakarta.validation + return Response.ok().entity(content).build()
    public static Response process(Runnable validators, Supplier<Object> logic) {
        try {
            validators.run();
        } catch (ValidationFailedException e) {
            Log.info(e);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
        try {
            Object content = logic.get();
            if (content instanceof Response) {
                return (Response) content;
            } else {
                return Response.ok().entity(content).build();
            }
        } catch (ServiceFailureException exception){
            Log.error(exception.getMessage(), exception);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(exception.getMessage()).build();
        } catch (InvalidInputException exception) {
            return Response.status(Response.Status.BAD_REQUEST).entity(exception.getMessage()).build();
        }
    }
}
