package org.kaleta.client;

public class RequestFailureException extends Exception
{
    public RequestFailureException(String message)
    {
        super(message);
    }

    public RequestFailureException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public RequestFailureException(Throwable cause)
    {
        super(cause);
    }
}
