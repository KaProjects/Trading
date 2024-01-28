package org.kaleta.service;

public class ServiceFailureException extends RuntimeException
{
    public ServiceFailureException(String message)
    {
        super(message);
    }

    public ServiceFailureException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public ServiceFailureException(Throwable cause)
    {
        super(cause);
    }
}
