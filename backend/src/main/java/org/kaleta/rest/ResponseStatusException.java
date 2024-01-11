package org.kaleta.rest;

import jakarta.ws.rs.core.Response;

public class ResponseStatusException extends RuntimeException
{
    private final Response.Status status;

    public ResponseStatusException(Response.Status status, String message)
    {
        super(message);
        this.status = status;
    }

    public Response.Status getStatus()
    {
        return status;
    }
}
