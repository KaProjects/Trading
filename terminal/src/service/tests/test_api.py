import time

import pytest
from fastapi.testclient import TestClient

from conftest import FAST
from trader_service.api import create_app
from trader_service.broker.fake import FakeBroker, load_seed, seed_prices
from trader_service.events import EventBus
from trader_service.prices.fake import FakePrices

AAPL, MSFT = 265598, 272093


@pytest.fixture
def client():
    bus = EventBus()
    prices = FakePrices(seed_prices(load_seed()), tick_interval=None, rng_seed=1)
    app = create_app(FakeBroker(bus, prices, FAST), prices, bus)
    with TestClient(app) as client:
        yield client


def wait_connected(client: TestClient) -> None:
    deadline = time.monotonic() + 2
    while client.get("/status").json()["state"] != "connected":
        assert time.monotonic() < deadline
        time.sleep(0.01)


@pytest.fixture
def logged_in(client):
    client.post("/login")
    wait_connected(client)
    return client


def body(conid=AAPL, side="BUY", qty="10", limit="200", ref="r1") -> dict:
    return {"conid": conid, "side": side, "quantity": qty, "limit_price": limit, "order_ref": ref}


def test_status_and_login(client):
    status = client.get("/status").json()
    assert (status["state"], status["environment"]) == ("disconnected", "fake")
    assert client.post("/login").json()["state"] == "logging_in"
    wait_connected(client)
    assert client.get("/status").json()["account_id"] == "FAKE0001"


def test_not_connected_is_409(client):
    response = client.get("/account")
    assert response.status_code == 409 and response.json()["error"] == "not_connected"


def test_account_data_endpoints_use_decimal_strings(logged_in):
    account = logged_in.get("/account").json()
    assert account["total_cash"] == "33640.00"
    assert len(logged_in.get("/positions").json()) == 5
    assert len(logged_in.get("/orders").json()) == 1
    assert len(logged_in.get("/fills", params={"days": 7}).json()) == 4
    assert logged_in.get("/fills", params={"days": 8}).status_code == 422
    assert len(logged_in.get("/contracts", params={"symbol": "SHOP"}).json()) == 2


def test_preview_returns_warnings_without_placing(logged_in):
    preview = logged_in.post("/orders/preview", json=body(qty="100", limit="250")).json()
    assert {w["code"] for w in preview["warnings"]} >= {"pct_of_account", "high_value", "price_deviation"}
    assert preview["block"] is None
    assert len(logged_in.get("/orders").json()) == 1


def test_place_order_and_retry_with_same_ref(logged_in):
    first = logged_in.post("/orders", json=body(side="SELL", qty="50", limit="300", ref="s1")).json()
    retry = logged_in.post("/orders", json=body(side="SELL", qty="50", limit="300", ref="s1")).json()
    assert first["order_id"] == retry["order_id"]  # not blocked by its own pending sell


def test_sell_more_than_owned_is_422(logged_in):
    response = logged_in.post("/orders", json=body(side="SELL", qty="51", limit="300"))
    assert response.status_code == 422
    assert response.json()["error"] == "sell_exceeds_position"
    assert len(logged_in.get("/orders").json()) == 1


def test_modify_checks_sell_block_excluding_itself(logged_in):
    order = logged_in.post("/orders", json=body(side="SELL", qty="40", limit="300", ref="s")).json()
    assert logged_in.patch(f"/orders/{order['order_id']}", json={"quantity": "50"}).status_code == 200
    assert logged_in.patch(f"/orders/{order['order_id']}", json={"quantity": "51"}).status_code == 422


def test_cancel_and_cancel_all(logged_in):
    order = logged_in.post("/orders", json=body()).json()
    assert logged_in.delete(f"/orders/{order['order_id']}").json()["status"] == "Cancelled"
    assert logged_in.delete(f"/orders/{order['order_id']}").status_code == 409
    assert logged_in.delete("/orders/9999").status_code == 404
    assert len(logged_in.delete("/orders").json()) == 1
    assert logged_in.get("/orders").json() == []


def test_events_websocket_streams_status_orders_fills_and_prices(logged_in):
    with logged_in.websocket_connect("/events") as ws:
        assert ws.receive_json()["type"] == "status"
        logged_in.post("/orders", json=body(limit="230", ref="ws"))
        seen = set()
        while not {"order", "fill", "position", "account"} <= seen:
            seen.add(ws.receive_json()["type"])
        logged_in.post("/dev/price", json={"symbol": "AAPL", "price": "231.00"})
        event = ws.receive_json()
        while event["type"] != "price":
            event = ws.receive_json()
        assert event["data"]["last"] == "231.00"


def test_dev_endpoints(logged_in):
    assert logged_in.post("/dev/drop-connection").status_code == 204
    assert logged_in.get("/status").json()["state"] == "reconnecting"
    wait_connected(logged_in)
    logged_in.post("/dev/require-login")
    assert logged_in.get("/status").json()["state"] == "login_required"
    logged_in.post("/login")
    wait_connected(logged_in)
    logged_in.post("/orders", json=body(limit="230", ref="x"))
    logged_in.post("/dev/reset")
    assert len(logged_in.get("/orders").json()) == 1
    logged_in.post("/dev/slow", json={"seconds": 0.2})
    start = time.monotonic()
    logged_in.get("/status")
    assert time.monotonic() - start >= 0.2
