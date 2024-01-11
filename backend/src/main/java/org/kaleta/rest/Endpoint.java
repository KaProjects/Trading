package org.kaleta.rest;

import jakarta.ws.rs.core.Response;

import java.util.function.Supplier;

public class Endpoint
{
    public static Response process(Runnable validators, Supplier<Object> logic) {
        try {
            validators.run();
        } catch (ResponseStatusException e) {
            return Response.status(e.getStatus()).entity(e.getMessage()).build();
        }
        Object content = logic.get();
        if (content == null) {
            return Response.noContent().build();
        } else {
            return Response.ok().entity(content).build();
        }
    }
}
