package org.kaleta.service;

final class ExternalWarnings
{
    private ExternalWarnings()
    {
    }

    static String unavailable(String source, Throwable exception)
    {
        String detail = exception == null ? null : exception.getMessage();
        if ((detail == null || detail.isBlank()) && exception != null && exception.getCause() != null) {
            detail = exception.getCause().getMessage();
        }
        return detail == null || detail.isBlank()
                ? source + " could not be loaded"
                : source + " could not be loaded: " + detail;
    }
}
