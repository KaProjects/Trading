package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kaleta.rest.dto.OnboardingLookupDto;
import org.kaleta.rest.dto.OnboardingEstimatesDto;
import org.kaleta.rest.dto.OnboardingFinancialsDto;
import org.kaleta.rest.dto.OnboardingNewsDto;
import org.kaleta.rest.dto.OnboardingTargetsDto;
import org.kaleta.rest.validation.ValidTicker;
import org.kaleta.service.OnboardingService;

@Path("/onboarding")
@Produces(MediaType.APPLICATION_JSON)
public class OnboardingEndpoints
{
    @Inject
    OnboardingService onboardingService;

    @GET
    @Path("/lookup")
    public OnboardingLookupDto lookup(@NotNull @ValidTicker @QueryParam("ticker") String ticker)
    {
        return onboardingService.lookup(ticker);
    }

    @GET
    @Path("/targets")
    public OnboardingTargetsDto targets(@NotNull @ValidTicker @QueryParam("ticker") String ticker)
    {
        return onboardingService.targets(ticker);
    }

    @GET
    @Path("/financials")
    public OnboardingFinancialsDto financials(@NotNull @ValidTicker @QueryParam("ticker") String ticker)
    {
        return onboardingService.financials(ticker);
    }

    @GET
    @Path("/estimates")
    public OnboardingEstimatesDto estimates(@NotNull @ValidTicker @QueryParam("ticker") String ticker)
    {
        return onboardingService.estimates(ticker);
    }

    @GET
    @Path("/news")
    public OnboardingNewsDto news(@NotNull @ValidTicker @QueryParam("ticker") String ticker)
    {
        return onboardingService.news(ticker);
    }

    @POST
    @Path("/firebase")
    public Response pushToFirebase(@NotNull @ValidTicker @QueryParam("ticker") String ticker)
    {
        onboardingService.pushToFirebase(ticker);
        return Response.noContent().build();
    }
}
