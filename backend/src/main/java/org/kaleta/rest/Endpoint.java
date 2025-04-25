package org.kaleta.rest;

import jakarta.ws.rs.core.Response;
import org.kaleta.service.ServiceFailureException;

import java.util.function.Supplier;
import io.quarkus.logging.Log;

public class Endpoint
{
    public static Response process(Runnable validators, Supplier<Object> logic) {
        try {
            validators.run();
        } catch (ResponseStatusException e) {
            Log.error(e);
            return Response.status(e.getStatus()).entity(e.getMessage()).build();
        }
        try {
            Object content = logic.get();
            if (content instanceof Response) {
                return (Response) content;
            } else {
                return Response.ok().entity(content).build();
            }
        } catch (ServiceFailureException e){
            Log.error(e);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
