package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.kaleta.framework.Assert;
import org.kaleta.model.Trades;
import org.kaleta.persistence.api.TradeDao;
import org.kaleta.persistence.entity.Portfolio;
import org.kaleta.persistence.entity.Trade;
import org.kaleta.rest.dto.PortfolioAssignmentDto;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.kaleta.framework.Assert.ExpectedViolation.NOT_NULL;
import static org.kaleta.framework.Assert.ExpectedViolation.VALID_ID;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminPortfolioEndpointsTest
{
    private static final String PATH = "/admin/portfolio";

    @Inject
    TradeDao tradeDao;

    @Test
    @Order(1)
    void validatesRequests()
    {
        Assert.getValidationError(PATH + "/trades?companyId=0", VALID_ID);
        Assert.putValidationError(PATH, null, NOT_NULL);

        PortfolioAssignmentDto dto = new PortfolioAssignmentDto();
        dto.setPortfolio(Portfolio.PATRIA_STANDARD.toString());
        Assert.putValidationError(PATH, dto, "size must be between 1 and 2147483647");

        dto.setTradeIds(List.of(2095L));
        dto.setPortfolio(null);
        Assert.putValidationError(PATH, dto, NOT_NULL);

        dto.setPortfolio("INVALID");
        Assert.putValidationError(PATH, dto, "must be any of Portfolio");

        dto.setPortfolio(Portfolio.PATRIA_STANDARD.toString());
        dto.setTradeIds(List.of(0L));
        Assert.putValidationError(PATH, dto, VALID_ID);

        dto.setTradeIds(List.of(4_294_967_295L));
        Assert.put400(PATH, dto, "trade with id '4294967295' is not available for portfolio assignment");
    }

    @Test
    @Order(2)
    void listsUnassignedTradesByCompany()
    {
        List<Trades.Trade> trades = given().when()
                .get(PATH + "/trades?companyId=2281")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().response().jsonPath().getList("", Trades.Trade.class);

        assertThat(trades.size(), is(2));
        assertThat(trades.stream().map(Trades.Trade::getId).toList(), containsInAnyOrder(2095L, 2177L));
        assertThat(trades.get(0).getPortfolio(), is(nullValue()));
        assertThat(trades.get(1).getPortfolio(), is(nullValue()));
    }

    @Test
    @Order(3)
    void assignsPortfolioAndRemovesTradeFromUnassignedList()
    {
        PortfolioAssignmentDto dto = new PortfolioAssignmentDto();
        dto.setTradeIds(List.of(2095L));
        dto.setPortfolio(Portfolio.REVOLUT_CFD.toString());

        Assert.put204(PATH, dto);

        Trade updated = tradeDao.get(2095L);
        assertThat(updated.getPortfolio(), is(Portfolio.REVOLUT_CFD));

        List<Trades.Trade> remaining = given().when()
                .get(PATH + "/trades?companyId=2281")
                .then()
                .statusCode(200)
                .extract().response().jsonPath().getList("", Trades.Trade.class);
        assertThat(remaining.size(), is(1));
        assertThat(remaining.get(0).getId(), is(2177L));
    }
}
