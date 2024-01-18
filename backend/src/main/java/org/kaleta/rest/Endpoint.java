package org.kaleta.rest;

import jakarta.ws.rs.core.Response;
import org.kaleta.service.ServiceException;

import java.util.function.Supplier;

public class Endpoint
{
    public static Response process(Runnable validators, Supplier<Object> logic) {
        try {
            validators.run();
        } catch (ResponseStatusException e) {
            return Response.status(e.getStatus()).entity(e.getMessage()).build();
        }
        try {
            Object content = logic.get();
            if (content instanceof Response) {
                return (Response) content;
            } else {
                return Response.ok().entity(content).build();
            }
        } catch (ServiceException e){
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
