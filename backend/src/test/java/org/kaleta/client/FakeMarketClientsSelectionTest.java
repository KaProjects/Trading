package org.kaleta.client;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class FakeMarketClientsSelectionTest
{
    @Inject
    PolygonClient polygonClient;
    @Inject
    FinnhubClient finnhubClient;

    @Test
    void fakeModeSelectsInMemoryClients() throws RequestFailureException
    {
        assertThat(polygonClient, instanceOf(InMemoryPolygonClient.class));
        assertThat(finnhubClient, instanceOf(InMemoryFinnhubClient.class));
        assertThat(polygonClient.getFinancials("INTC", "2026", "Q1").isPresent(), is(true));
        assertThat(finnhubClient.quote("AMD").getC(), is("158.25"));
    }
}
