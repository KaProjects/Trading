package org.kaleta.persistence.entity;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class CurrencyTest
{
    @Test
    void exposesIsoCode()
    {
        assertThat(Currency.$.getIsoCode(), is("USD"));
        assertThat(Currency.€.getIsoCode(), is("EUR"));
        assertThat(Currency.£.getIsoCode(), is("GBP"));
        assertThat(Currency.K.getIsoCode(), is("CZK"));
        assertThat(Currency.F.getIsoCode(), is("CHF"));
    }
}
