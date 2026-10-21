import dataclasses

import pytest

from conftest import FAST, D, settle, wait_for_state
from trader_service.broker.base import InvalidModification, NotConnected, OrderNotActive
from trader_service.broker.fake import FakeBroker
from trader_service.models import ConnectionState, ModifyOrderRequest, OrderStatus, PlaceOrderRequest, Side

AAPL, MSFT, NVDA, SHOP_TSE, REJECT = 265598, 272093, 4815747, 195014120, 999999


def order(conid=AAPL, side=Side.BUY, qty="10", limit="200", ref="r1") -> PlaceOrderRequest:
    return PlaceOrderRequest(conid=conid, side=side, quantity=D(qty), limit_price=D(limit), order_ref=ref)


# --- connection ---------------------------------------------------------------------------------------


async def test_login_goes_through_2fa_to_connected(broker):
    status = await broker.login()
    assert status.state == ConnectionState.LOGGING_IN
    assert status.message == "Approve on your phone"
    await wait_for_state(broker, ConnectionState.CONNECTED)
    assert broker.status().account_id == "FAKE0001"


async def test_data_requires_connection(broker):
    with pytest.raises(NotConnected):
        broker.account_summary()


async def test_2fa_timeout_fails_login(bus, prices):
    broker = FakeBroker(bus, prices, dataclasses.replace(FAST, auto_approve=False, twofa_timeout=0.02))
    await broker.login()
    await wait_for_state(broker, ConnectionState.LOGIN_FAILED)
    assert broker.status().message == "2FA not approved in time"
    await broker.stop()


async def test_manual_2fa_approval(bus, prices):
    broker = FakeBroker(bus, prices, dataclasses.replace(FAST, auto_approve=False))
    await broker.login()
    broker.approve_2fa()
    await wait_for_state(broker, ConnectionState.CONNECTED)
    await broker.stop()


async def test_dev_fail_2fa(bus, prices):
    broker = FakeBroker(bus, prices, dataclasses.replace(FAST, auto_approve=False))
    await broker.login()
    broker.fail_2fa()
    assert broker.status().state == ConnectionState.LOGIN_FAILED
    await broker.stop()


async def test_drop_connection_reconnects(connected):
    connected.drop_connection()
    assert connected.status().state == ConnectionState.RECONNECTING
    await wait_for_state(connected, ConnectionState.CONNECTED)


async def test_require_login_and_logout(connected):
    connected.require_login()
    assert connected.status().state == ConnectionState.LOGIN_REQUIRED
    assert connected.status().account_id is None
    await connected.logout()
    assert connected.status().state == ConnectionState.DISCONNECTED


# --- account ------------------------------------------------------------------------------------------


async def test_account_summary_from_seed(connected):
    summary = connected.account_summary()
    assert summary.base_currency == "USD"
    assert summary.total_cash == D("33640.00")  # 25000 USD + 8000 EUR * 1.08
    # one seeded open BUY 10 MSFT @ 400 reserves 4000
    assert summary.available_funds == D("29640.00")
    assert {c.currency: c.amount for c in summary.cash} == {"EUR": D("8000.00"), "USD": D("25000.00")}
    assert summary.net_liquidation > summary.total_cash


async def test_positions_orders_fills_and_lookup(connected):
    positions = {p.contract.symbol: p for p in connected.positions()}
    assert positions["AAPL"].quantity == 50 and positions["AAPL"].cost_basis == D("9500.00")
    assert [o.contract.symbol for o in connected.open_orders()] == ["MSFT"]
    fills = connected.fills(7)
    assert len(fills) == 4 and fills[0].time >= fills[-1].time
    assert len(connected.fills(3)) == 2
    assert {c.primary_exchange for c in connected.lookup_contract("shop")} == {"NYSE", "TSE"}


async def test_price_subscriptions_follow_positions_and_orders(connected, prices):
    assert prices._symbols == {"AAPL", "MSFT", "NVDA", "ASML.AEB", "SAP.IBIS"}
    await connected.place_order(order(conid=SHOP_TSE, limit="100", ref="shop"))
    assert "SHOP.TSE" in prices._symbols


# --- orders ---------------------------------------------------------------------------------------------


async def test_limit_buy_waits_then_fills_when_price_crosses(connected, prices):
    placed = await connected.place_order(order(limit="220"))
    assert placed.status == OrderStatus.PENDING_SUBMIT
    await settle()
    assert connected.order(placed.order_id).status == OrderStatus.SUBMITTED  # AAPL at 228.40
    prices.set_price("AAPL", D("219.50"))
    filled = connected.order(placed.order_id)
    assert filled.status == OrderStatus.FILLED and filled.filled == 10
    assert filled.avg_fill_price == D("219.50")
    aapl = next(p for p in connected.positions() if p.contract.symbol == "AAPL")
    assert aapl.quantity == 60
    assert aapl.avg_cost == D("194.92")  # (50*190 + 10*219.50) / 60
    usd = next(c for c in connected.account_summary().cash if c.currency == "USD")
    assert usd.amount == D("25000") - D("2195.00") - D("1.00")


async def test_marketable_limit_fills_on_submit(connected):
    placed = await connected.place_order(order(limit="230"))
    await settle()
    assert connected.order(placed.order_id).status == OrderStatus.FILLED


async def test_large_order_fills_in_partial_chunks(connected, prices):
    placed = await connected.place_order(order(conid=NVDA, qty="600", limit="40", ref="big"))
    await settle()
    assert connected.order(placed.order_id).status == OrderStatus.SUBMITTED
    prices.set_price("NVDA", D("39"))
    partial = connected.order(placed.order_id)
    assert (partial.status, partial.filled, partial.remaining) == (OrderStatus.SUBMITTED, 200, 400)
    prices.set_price("NVDA", D("39"))
    prices.set_price("NVDA", D("39"))
    assert connected.order(placed.order_id).status == OrderStatus.FILLED


async def test_selling_whole_position_removes_it(connected, prices):
    await connected.place_order(order(side=Side.SELL, qty="50", limit="228", ref="sell"))
    await settle()
    assert "AAPL" not in {p.contract.symbol for p in connected.positions()}


async def test_rejections(connected):
    assert (await connected.place_order(order(conid=REJECT, ref="a"))).status == OrderStatus.REJECTED
    poor = await connected.place_order(order(qty="1000", limit="200", ref="b"))
    assert (poor.status, poor.reject_reason) == (OrderStatus.REJECTED, "Insufficient funds")
    assert (await connected.place_order(order(limit="0", ref="c"))).status == OrderStatus.REJECTED


async def test_buy_using_most_of_available_funds_is_accepted(connected):
    # 29 640 available; the new order must not reserve funds against itself
    placed = await connected.place_order(order(conid=NVDA, qty="200", limit="140", ref="most"))  # 28 000
    assert placed.status == OrderStatus.PENDING_SUBMIT


async def test_same_order_ref_is_not_placed_twice(connected):
    first = await connected.place_order(order(ref="dup"))
    second = await connected.place_order(order(ref="dup", qty="99"))
    assert first.order_id == second.order_id
    assert connected.order_by_ref("dup").order_id == first.order_id


async def test_modify_and_cancel(connected):
    placed = await connected.place_order(order(limit="200"))
    await settle()
    modified = await connected.modify_order(placed.order_id, ModifyOrderRequest(limit_price=D("201"), quantity=D("5")))
    assert (modified.limit_price, modified.quantity, modified.remaining) == (201, 5, 5)
    cancelled = await connected.cancel_order(placed.order_id)
    assert cancelled.status == OrderStatus.CANCELLED
    with pytest.raises(OrderNotActive):
        await connected.cancel_order(placed.order_id)


async def test_modify_below_filled_is_invalid(connected, prices):
    placed = await connected.place_order(order(conid=NVDA, qty="600", limit="40", ref="big"))
    await settle()
    prices.set_price("NVDA", D("39"))  # 200 filled
    with pytest.raises(InvalidModification):
        await connected.modify_order(placed.order_id, ModifyOrderRequest(quantity=D("200")))


async def test_cancel_all(connected):
    await connected.place_order(order(limit="200"))
    await settle()
    cancelled = await connected.cancel_all()
    assert len(cancelled) == 2 and connected.open_orders() == []


async def test_events_published(connected, bus, prices):
    queue = bus.subscribe()
    await connected.place_order(order(limit="230"))
    await settle()
    types = []
    while not queue.empty():
        types.append(queue.get_nowait().type)
    assert types[:2] == ["order", "order"]  # PendingSubmit, Submitted
    assert {"fill", "position", "account"} <= set(types)


async def test_reset_restores_seed(connected):
    await connected.place_order(order(limit="230"))
    await settle()
    connected.reset()
    assert next(p for p in connected.positions() if p.contract.symbol == "AAPL").quantity == 50


async def test_cheap_stock_allows_testing_partial_fills(connected, prices):
    placed = await connected.place_order(order(conid=9599491, qty="600", limit="11.50", ref="ford"))
    await settle()
    first = connected.order(placed.order_id)
    assert (first.status, first.filled) == (OrderStatus.SUBMITTED, 200)  # marketable: first chunk at submit
    prices.set_price("F", D("11.21"))
    prices.set_price("F", D("11.22"))
    assert connected.order(placed.order_id).status == OrderStatus.FILLED
