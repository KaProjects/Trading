package org.kaleta.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.ConfigProvider;
import org.kaleta.dto.TradeCreateDto;
import org.kaleta.dto.TradeDto;
import org.kaleta.dto.TradeSellDto;
import org.kaleta.dto.TradesUiDto;
import org.kaleta.entity.Trade;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.service.FirebaseService;
import org.kaleta.service.TradeService;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;

@Path("/trade")
public class TradeResource
{
    @Inject
    TradeService tradeService;
    @Inject
    FirebaseService firebaseService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response getTrades(
            @QueryParam("active") Boolean active,
            @QueryParam("year") String year,
            @QueryParam("companyId") String companyId,
            @QueryParam("currency") String currency)
    {
        return Endpoint.process(() -> {
            if (companyId != null) Validator.validateUuid(companyId);
            if (currency != null) Validator.validateCurrency(currency);
            if (year != null) Validator.validateYear(year);
        }, () -> {
            List<Trade> trades = tradeService.getTrades(active, companyId, currency, year, year);

            if (active != null && active) {
                for (Trade trade : trades) {
                    if (firebaseService.hasCompany(trade.getTicker())) {
                        FirebaseCompany firebaseCompany = firebaseService.getCompany(trade.getTicker());
                        trade.setSellDate(Date.valueOf(firebaseCompany.getTime().split("T")[0]));
                        trade.setSellPrice(new BigDecimal(firebaseCompany.getPrice()));
                        trade.setSellFees(trade.getPurchaseFees());
                    }
                }
            }
            TradesUiDto dto = TradesUiDto.from(trades);
            dto.setSums(tradeService.computeSums(trades));
            return dto;
        });
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response createTrade(TradeCreateDto tradeCreateDto)
    {
        return Endpoint.process(
            () -> {
                Validator.validatePayload(tradeCreateDto);
                Validator.validateCreateTradeDto(tradeCreateDto);
            },
            () -> {
                Trade trade = tradeService.createTrade(tradeCreateDto);

                if (ConfigProvider.getConfig().getValue("environment", String.class).equals("PRODUCTION")){
                    firebaseService.pushAssets(tradeService.getTrades(true, null, null, null, null));
                }

                return Response.status(Response.Status.CREATED).entity(TradeDto.from(trade)).build();
            });
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response sellTrade(TradeSellDto tradeSellDto)
    {
        return Endpoint.process(
            () -> {
                Validator.validatePayload(tradeSellDto);
                Validator.validateSellTradeDto(tradeSellDto);
            },
            () -> {
                tradeService.sellTrade(tradeSellDto);

                if (ConfigProvider.getConfig().getValue("environment", String.class).equals("PRODUCTION")){
                    firebaseService.pushAssets(tradeService.getTrades(true, null, null, null, null));
                }

                return Response.status(Response.Status.NO_CONTENT).build();
            });
    }
}
