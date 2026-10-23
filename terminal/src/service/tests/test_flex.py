"""T11 (D11.2): trades from IBKR's Flex Web Service, against a stub of the service."""

from datetime import date

import httpx
import pytest

from trader_service.flex import FlexClient, FlexError, _chunks, parse_trades

REPORT = """<FlexQueryResponse queryName="trader-terminal-trades" type="AF"><FlexStatements count="1">
<FlexStatement accountId="DU1234567" fromDate="20260101" toDate="20261231"><Trades>
<Trade accountId="DU1234567" assetCategory="STK" symbol="NVDA" conid="4815747" currency="USD"
  dateTime="20260110;100000" tradeDate="20260110" buySell="BUY" quantity="3" tradePrice="145.00"
  ibCommission="-2.00" ibOrderID="11" ibExecID="0001.01" orderReference="plan-a" levelOfDetail="EXECUTION"/>
<Trade accountId="DU1234567" assetCategory="STK" symbol="NVDA" conid="4815747" currency="USD"
  dateTime="20260110;100100" tradeDate="20260110" buySell="BUY" quantity="2" tradePrice="146.25"
  ibCommission="-2.95" ibOrderID="11" ibExecID="0001.02" orderReference="plan-a" levelOfDetail="EXECUTION"/>
<Trade accountId="DU1234567" assetCategory="STK" symbol="NVDA" conid="4815747" currency="USD"
  dateTime="20260212;110000" tradeDate="20260212" buySell="BUY (Ca.)" quantity="1" tradePrice="150"
  ibCommission="0" ibOrderID="12" ibExecID="0002.01" levelOfDetail="EXECUTION"/>
<Trade accountId="DU1234567" assetCategory="STK" symbol="NVDA" conid="4815747" currency="USD"
  dateTime="20260401;153000" tradeDate="20260401" buySell="SELL" quantity="-8" tradePrice="180"
  ibCommission="-6.95" ibOrderID="13" ibExecID="0003.01" levelOfDetail="EXECUTION"/>
</Trades></FlexStatement></FlexStatements></FlexQueryResponse>"""


def flex_transport(report: str = REPORT, pending: int = 1) -> httpx.MockTransport:
    """SendRequest → reference code; GetStatement → "in progress" `pending` times, then the report."""
    calls = {"get": 0}

    def handle(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/SendRequest"):
            assert request.url.params["t"] == "token" and request.url.params["q"] == "123"
            return httpx.Response(200, text="<FlexStatementResponse><Status>Success</Status>"
                                            "<ReferenceCode>999</ReferenceCode>"
                                            "<Url>https://flex.test/GetStatement</Url></FlexStatementResponse>")
        calls["get"] += 1
        if calls["get"] <= pending:
            return httpx.Response(200, text="<FlexStatementResponse><Status>Warn</Status><ErrorCode>1019</ErrorCode>"
                                            "<ErrorMessage>Statement generation in progress. Please try again"
                                            " shortly.</ErrorMessage></FlexStatementResponse>")
        return httpx.Response(200, text=report)

    return httpx.MockTransport(handle)


def test_parse_trades_skips_cancellations_and_makes_amounts_positive():
    fills = parse_trades(REPORT.encode())
    assert [(f["side"], f["quantity"], f["commission"], f["trade_date"]) for f in fills] == [
        ("BUY", "3", "2.00", "2026-01-10"), ("BUY", "2", "2.95", "2026-01-10"), ("SELL", "8", "6.95", "2026-04-01")]
    assert fills[0]["contract"] == {"conid": 4815747, "symbol": "NVDA"} and fills[0]["order_ref"] == "plan-a"


def test_long_ranges_are_split_into_365_days():
    assert _chunks(date(2025, 1, 1), date(2026, 12, 31)) == [
        (date(2025, 1, 1), date(2025, 12, 31)), (date(2026, 1, 1), date(2026, 12, 31))]


async def test_client_waits_for_the_report_and_filters_the_range():
    flex = FlexClient(base_url="https://flex.test", transport=flex_transport(pending=2), poll_interval=0, request_gap=0)
    flex.set_credentials("token", "123")
    fills = await flex.trades(date(2026, 1, 1), date(2026, 3, 31))
    assert [f["exec_id"] for f in fills] == ["0001.01", "0001.02"]


async def test_retries_without_date_range_when_ibkr_refuses_it():
    inner = flex_transport(pending=0)

    def handle(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/SendRequest") and "fd" in request.url.params:
            return httpx.Response(200, text="<FlexStatementResponse><Status>Fail</Status><ErrorCode>1020</ErrorCode>"
                                            "<ErrorMessage>Invalid request or unable to validate request."
                                            "</ErrorMessage></FlexStatementResponse>")
        return inner.handle_request(request)

    flex = FlexClient(base_url="https://flex.test", transport=httpx.MockTransport(handle), poll_interval=0, request_gap=0)
    flex.set_credentials("token", "123")
    assert len(await flex.trades(date(2026, 1, 1), date(2026, 12, 31))) == 3


async def test_no_statement_yet_means_no_transactions():
    """A new account (or days IBKR has no statement for) answers 1003 — an empty export, not an error."""
    def handle(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, text="<FlexStatementResponse><Status>Fail</Status><ErrorCode>1003</ErrorCode>"
                                        "<ErrorMessage>Statement is not available.</ErrorMessage>"
                                        "</FlexStatementResponse>")

    flex = FlexClient(base_url="https://flex.test", transport=httpx.MockTransport(handle), poll_interval=0, request_gap=0)
    flex.set_credentials("token", "123")
    assert await flex.trades(date(2026, 10, 1), date(2026, 10, 22)) == []


async def test_rate_limit_gets_a_plain_message():
    def handle(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, text="<FlexStatementResponse><Status>Fail</Status><ErrorCode>1018</ErrorCode>"
                                        "<ErrorMessage>Too many requests have been made from this token."
                                        "</ErrorMessage></FlexStatementResponse>")

    flex = FlexClient(base_url="https://flex.test", transport=httpx.MockTransport(handle), poll_interval=0,
                      request_gap=0)
    flex.set_credentials("token", "123")
    with pytest.raises(FlexError, match="try again in a minute"):
        await flex.trades(date(2026, 10, 1), date(2026, 10, 22))
