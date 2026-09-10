package org.kaleta.rest;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.kaleta.rest.dto.EpsOutperformerDto;
import org.kaleta.rest.dto.OutperformersDto;
import org.kaleta.service.OutperformersService;

import java.math.BigDecimal;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

@QuarkusTest
class OutperformersEndpointsTest
{
    @InjectMock
    OutperformersService outperformersService;

    @Test
    void get_returnsDtoFromService()
    {
        OutperformersDto dto = new OutperformersDto();
        EpsOutperformerDto eps = new EpsOutperformerDto();
        eps.setTicker("NVDA");
        eps.setQuarter4Change(new BigDecimal("18.75"));
        dto.setEpsEstimates(List.of(eps));
        when(outperformersService.get()).thenReturn(dto);

        OutperformersDto result = given().when()
                .get("/outperformers")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().as(OutperformersDto.class);

        assertThat(result.getEpsEstimates().size(), is(1));
        assertThat(result.getEpsEstimates().getFirst().getTicker(), is("NVDA"));
    }
}
