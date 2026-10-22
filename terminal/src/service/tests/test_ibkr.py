"""T1: IBC launcher and IbkrBroker against a stub Gateway (no IBKR needed)."""

import asyncio
import os
import stat
from datetime import UTC, datetime
from decimal import Decimal

import ib_async
import pytest

from trader_service.broker.ibkr import IbkrBroker
from trader_service.events import EventBus
from trader_service.ibc import IbcConfig, IbcError, IbcProcess, gateway_version, login_ini
from trader_service.models import ConnectionState, Environment, OrderStatus, PlaceOrderRequest, Side
from trader_service.prices.fake import FakePrices

TEMPLATE = "# IBC\r\nIbLoginId=\r\nIbPassword=\r\nTradingMode=paper\r\nReadOnlyApi=\r\nFIX=no\r\n"


def test_login_ini_fills_credentials_and_api_settings():
    ini = login_ini(TEMPLATE, "user", "p=ss word", Environment.PAPER, read_only_api=False)
    lines = ini.split("\r\n")
    assert "IbLoginId=user" in lines and "IbPassword=p=ss word" in lines
    assert "ReadOnlyApi=no" in lines and "OverrideTwsApiPort=4002" in lines and "FIX=no" in lines
    assert "# IBC" in lines
    live = login_ini(TEMPLATE, "user", "pw", Environment.PRODUCTION, read_only_api=True).split("\r\n")
    assert "TradingMode=live" in live and "OverrideTwsApiPort=4001" in live and "ReadOnlyApi=yes" in live
    with pytest.raises(IbcError):
        login_ini(TEMPLATE, "user", "pw\nIbLoginId=other", Environment.PAPER, read_only_api=False)


def test_gateway_version_picks_newest(tmp_path):
    for name in ("IB Gateway 10.37", "IB Gateway 10.50", "IB Gateway 9.99", "Other"):
        (tmp_path / name).mkdir()
    assert gateway_version(tmp_path) == "10.50"
    with pytest.raises(IbcError):
        gateway_version(tmp_path / "Other")


def fake_ibc(tmp_path) -> IbcConfig:
    """An `ibcstart.sh` that just stays up, like IBC waiting for the Gateway."""
    ibc, tws = tmp_path / "ibc", tmp_path / "Applications"
    (ibc / "scripts").mkdir(parents=True)
    (tws / "IB Gateway 10.50").mkdir(parents=True)
    (ibc / "config.ini").write_text(TEMPLATE)
    script = ibc / "scripts" / "ibcstart.sh"
    script.write_text("#!/bin/sh\nsleep 30\n")
    script.chmod(0o700)
    return IbcConfig(ibc_path=ibc, tws_path=tws, login_timeout=2)


async def test_ibc_writes_owner_only_config_and_stops(tmp_path):
    process = IbcProcess(fake_ibc(tmp_path), Environment.PAPER, tmp_path / "work")
    await process.start("user", "secret")
    ini = tmp_path / "work" / "ibc-login.ini"
    assert process.running
    assert stat.S_IMODE(os.stat(ini).st_mode) == 0o600 and "IbPassword=secret" in ini.read_text()
    await process.stop(grace=2)
    assert not process.running and not ini.exists()


class StubGateway(ib_async.IB):
    """Answers on the API port once `logged_in` is set, with one account."""

    def __init__(self, account="DU1234567"):
        super().__init__()
        self.logged_in = False
        self.account = account
        self.connected = False
        self.placed: list[ib_async.Trade] = []

    async def connectAsync(self, *args, **kwargs):
        if not self.logged_in:
            raise ConnectionRefusedError
        self.connected = True
        return self

    def isConnected(self):
        return self.connected

    def disconnect(self):
        self.connected = False

    def managedAccounts(self):
        return [self.account]

    def accountValues(self, account=""):
        v = lambda tag, value, cur: ib_async.AccountValue(self.account, tag, value, cur, "")  # noqa: E731
        return [v("NetLiquidation", "1000000.00", "USD"), v("TotalCashValue", "990000.5", "USD"),
                v("AvailableFunds", "980000", "USD"), v("BuyingPower", "3920000", "USD"),
                v("CashBalance", "990000.5", "USD"), v("CashBalance", "990000.5", "BASE"),
                v("ExchangeRate", "1.00", "USD"), v("ExchangeRate", "1.08", "EUR")]

    def positions(self, account=""):
        return [ib_async.Position(self.account, ib_async.Stock("AAPL", "SMART", "USD", conId=265598,
                                                               primaryExchange="NASDAQ"), 10.0, 190.5)]

    def trades(self):
        return self.placed

    def openTrades(self):
        return [t for t in self.placed if t.orderStatus.status in ib_async.OrderStatus.ActiveStates]

    def fills(self):
        execution = ib_async.Execution(execId="0001.01", time=datetime(2026, 10, 22, 14, 0, tzinfo=UTC),
                                       side="BOT", shares=10.0, price=190.5, orderId=5, orderRef="plan-aapl")
        report = ib_async.CommissionReport(execId="0001.01", commission=1.0, currency="USD")
        return [ib_async.Fill(ib_async.Stock("AAPL", "SMART", "USD", conId=265598), execution, report,
                              execution.time)]

    async def reqContractDetailsAsync(self, contract):
        c = ib_async.Stock("AAPL", "SMART", "USD", conId=265598, primaryExchange="NASDAQ")
        return [ib_async.ContractDetails(contract=c, longName="APPLE INC")]

    def placeOrder(self, contract, order):
        order.orderId = 100 + len(self.placed)
        trade = ib_async.Trade(contract, order, ib_async.OrderStatus(orderId=order.orderId, status="PreSubmitted",
                                                                    remaining=order.totalQuantity))
        self.placed.append(trade)
        return trade


@pytest.fixture
def ibkr(tmp_path):
    gateway = StubGateway()
    broker = IbkrBroker(EventBus(), FakePrices({}, tick_interval=None), Environment.PAPER, tmp_path / "work",
                        fake_ibc(tmp_path), ib=gateway)
    yield broker, gateway, tmp_path / "work" / "ibc-login.ini"


async def wait_for(broker, state):
    for _ in range(100):
        if broker.status().state == state:
            return
        await asyncio.sleep(0.05)
    raise AssertionError(f"{broker.status()} != {state}")


async def test_login_without_credentials_fails(ibkr):
    broker, _, _ = ibkr
    assert (await broker.login()).state == ConnectionState.LOGIN_FAILED
    await broker.stop()


async def test_login_waits_for_2fa_then_connects_and_deletes_credentials(ibkr):
    broker, gateway, ini = ibkr
    broker.set_credentials("user", "secret")
    status = await broker.login()
    assert (status.state, status.message) == (ConnectionState.LOGGING_IN, "Approve on your phone")
    assert ini.exists()
    gateway.logged_in = True  # phone approved
    await wait_for(broker, ConnectionState.CONNECTED)
    assert broker.status().account_id == "DU1234567" and not ini.exists()
    summary = broker.account_summary()
    assert (summary.base_currency, summary.net_liquidation, summary.total_cash) == \
        ("USD", Decimal("1000000.00"), Decimal("990000.50"))
    assert summary.fx_to_base["EUR"] == Decimal("1.08") and broker.fx_rate("EUR") == Decimal("1.08")
    [position] = broker.positions()
    assert (position.contract.name, position.quantity, position.cost_basis) == ("APPLE INC", 10, Decimal("1905.00"))
    [fill] = broker.fills(7)
    assert (fill.exec_id, fill.side, fill.order_ref, fill.commission) == ("0001.01", Side.BUY, "plan-aapl", 1)
    await broker.stop()


async def test_2fa_timeout_fails_login(ibkr):
    broker, _, ini = ibkr
    broker.set_credentials("user", "secret")
    await broker.login()
    await wait_for(broker, ConnectionState.LOGIN_FAILED)
    assert broker.status().message == "2FA not approved in time" and not ini.exists()
    await broker.stop()


async def test_live_account_is_refused_on_paper(ibkr):
    broker, gateway, _ = ibkr
    gateway.account, gateway.logged_in = "U7654321", True
    status = await broker.login()  # a Gateway is already up: connects right away
    assert status.state == ConnectionState.LOGIN_FAILED and "paper" in status.message
    await broker.stop()


async def test_orders_map_to_ours_and_refs_are_not_placed_twice(ibkr):
    broker, gateway, _ = ibkr
    gateway.logged_in = True
    await broker.login()
    await broker.lookup_contract("aapl")
    request = PlaceOrderRequest(conid=265598, side=Side.BUY, quantity=Decimal(5), limit_price=Decimal("191.25"),
                                order_ref="plan-1-aapl")
    order = await broker.place_order(request)
    assert (order.order_id, order.status, order.remaining, order.limit_price) == \
        (100, OrderStatus.SUBMITTED, 5, Decimal("191.25"))
    assert (await broker.place_order(request)).order_id == 100 and len(gateway.placed) == 1
    assert [o.order_id for o in broker.open_orders()] == [100]
    await broker.stop()
