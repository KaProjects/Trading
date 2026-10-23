import time

import pytest
from fastapi.testclient import TestClient

from conftest import FAST
from trader_service.api import create_app
from trader_service.broker.fake import FakeBroker, load_seed, seed_prices
from trader_service.events import EventBus
from trader_service.prices.fake import FakePrices
from trader_service.store import Store
from trader_service.vault import Vault

AAPL, MSFT = 265598, 272093


@pytest.fixture
def client(tmp_path):
    bus = EventBus()
    prices = FakePrices(seed_prices(load_seed()), tick_interval=None, rng_seed=1)
    broker = FakeBroker(bus, prices, FAST)
    store = Store(broker, bus, Vault(tmp_path / "data"), snapshot_interval=0.05)
    app = create_app(broker, prices, bus, store)
    app.state.test_store = store
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
    assert account["total_cash"] == "133640.00"
    assert len(logged_in.get("/positions").json()) == 5
    assert len(logged_in.get("/orders").json()) == 1
    assert len(logged_in.get("/fills", params={"days": 7}).json()) == 4
    assert logged_in.get("/fills", params={"days": 8}).status_code == 422
    assert len(logged_in.get("/contracts", params={"symbol": "SHOP"}).json()) == 2


def test_preview_returns_warnings_without_placing(logged_in):
    preview = logged_in.post("/orders/preview", json=body(qty="200", limit="250")).json()
    warnings = {w["code"] for w in preview["warnings"] if w["level"] != "info"}  # info depends on the clock
    assert warnings == {"pct_of_account", "price_deviation"}  # ~27 %, +9.6 %
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


def test_manual_2fa_approval_mode(client):
    assert client.get("/dev/2fa/auto").json() == {"enabled": True}
    client.post("/dev/2fa/auto", json={"enabled": False})
    assert client.get("/dev/2fa/auto").json() == {"enabled": False}
    client.post("/login")
    time.sleep(0.1)  # FAST config would have auto-approved by now
    assert client.get("/status").json()["state"] == "logging_in"
    client.post("/dev/2fa/fail")
    assert client.get("/status").json()["state"] == "login_failed"
    client.post("/login")
    client.post("/dev/2fa/approve")
    wait_connected(client)


def test_persisted_data_needs_unlock(logged_in):
    assert logged_in.get("/vault").json() == {"unlocked": False}
    response = logged_in.get("/snapshot")
    assert response.status_code == 423 and response.json()["error"] == "locked"
    assert logged_in.get("/history").status_code == 423


def test_snapshot_and_history_after_unlock(logged_in):
    logged_in.app.state.test_store.unlock(bytes(32))
    assert logged_in.get("/vault").json() == {"unlocked": True}
    snapshot = logged_in.get("/snapshot").json()
    assert snapshot["account"]["account_id"] == "FAKE0001" and len(snapshot["positions"]) == 5
    assert len(logged_in.get("/history", params={"days": 30}).json()) == 4  # seeded 7-day fills copied


def test_rule_thresholds_are_saved_encrypted_and_used(logged_in):
    defaults = logged_in.get("/settings/rules").json()
    assert (defaults["pct_of_account_warn"], defaults["pct_of_account_strong"], defaults["order_value_warn"]) \
        == ("25", "50", "100000")
    assert defaults["plan_limit_markup"] == "3"
    assert logged_in.put("/settings/rules", json={**defaults, "quantity_warn": "5"}).status_code == 423  # locked
    store = logged_in.app.state.test_store
    store.unlock(bytes(32))
    assert logged_in.put("/settings/rules", json={**defaults, "quantity_warn": "5"}).status_code == 200
    preview = logged_in.post("/orders/preview", json=body(qty="6", limit="228")).json()
    assert "high_quantity" in {w["code"] for w in preview["warnings"]}
    store.lock()
    store.unlock(bytes(32))  # reloaded from settings.enc
    assert logged_in.get("/settings/rules").json()["quantity_warn"] == "5"


def test_invalid_thresholds_rejected(logged_in):
    logged_in.app.state.test_store.unlock(bytes(32))
    bad = {"pct_of_account_warn": "30", "pct_of_account_strong": "20"}
    assert logged_in.put("/settings/rules", json=bad).status_code == 422
    assert logged_in.put("/settings/rules", json={"quantity_warn": "-1"}).status_code == 422


def test_blocked_and_cancelled_attempts_are_logged(logged_in):
    from datetime import UTC, datetime

    store = logged_in.app.state.test_store
    store.unlock(bytes(32))
    logged_in.post("/orders", json=body(side="SELL", qty="51", limit="300", ref="too-many"))
    assert logged_in.post("/activity", json={"kind": "order_dialog_cancelled",
                                             "detail": {"symbol": "AAPL"}}).status_code == 204
    assert logged_in.post("/activity", json={"kind": "anything"}).status_code == 422
    kinds = [e["kind"] for e in store.activity(f"{datetime.now(UTC):%Y-%m}")]
    assert "order_blocked" in kinds and "order_dialog_cancelled" in kinds


def test_quote(logged_in):
    assert logged_in.get("/quote", params={"conid": AAPL}).json()["last"] == "228.40"
    assert logged_in.get("/quote", params={"conid": 1}).status_code == 404


def test_insufficient_funds_blocks_new_buy_but_not_its_own_modification(logged_in):
    order = logged_in.post("/orders", json=body(qty="10", limit="200", ref="b1")).json()  # reserves 2 000
    # available is now 127 640: a new 128 000 buy is blocked ...
    blocked = logged_in.post("/orders", json=body(qty="640", limit="200", ref="b2"))
    assert blocked.status_code == 422 and blocked.json()["error"] == "insufficient_funds"
    # ... but growing the existing order to 128 000 is fine: its own 2 000 count back in
    assert logged_in.patch(f"/orders/{order['order_id']}", json={"quantity": "640"}).status_code == 200
    assert logged_in.patch(f"/orders/{order['order_id']}", json={"quantity": "700"}).status_code == 422


def test_preview_of_a_modification_excludes_the_order_itself(logged_in):
    order = logged_in.post("/orders", json=body(side="SELL", qty="50", limit="300", ref="m1")).json()
    same = {"conid": AAPL, "side": "SELL", "quantity": "50", "limit_price": "301"}
    assert logged_in.post("/orders/preview", json=same).json()["block"]["code"] == "sell_exceeds_position"
    preview = logged_in.post("/orders/preview", json=same, params={"modify_order_id": order["order_id"]}).json()
    assert preview["block"] is None


def test_contracts_carry_feed_symbol_and_prices_snapshot(logged_in):
    shop = {c["primary_exchange"]: c["price_symbol"] for c in logged_in.get("/contracts", params={"symbol": "SHOP"}).json()}
    assert shop == {"NYSE": "SHOP", "TSE": "SHOP.TO"}
    symbols = {p["symbol"] for p in logged_in.get("/prices").json()}
    assert symbols == {"AAPL", "MSFT", "NVDA", "ASML.AS", "SAP.DE"}


def test_watchlist_streams_extra_symbols_and_account_has_fx(logged_in):
    assert logged_in.get("/account").json()["fx_to_base"] == {"USD": "1", "EUR": "1.08", "CAD": "0.73"}
    assert logged_in.put("/watchlist", json={"conids": [9599491, 195014120]}).status_code == 200
    symbols = {p["symbol"] for p in logged_in.get("/prices").json()}
    assert {"F", "SHOP.TO"} <= symbols
    assert logged_in.put("/watchlist", json={"conids": []}).status_code == 200
    assert "F" not in {p["symbol"] for p in logged_in.get("/prices").json()}
    assert logged_in.put("/watchlist", json={"conids": [1]}).status_code == 404


def test_price_stream_only_while_unlocked_and_connected(tmp_path):
    class SpyPrices(FakePrices):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, **kwargs)
            self.active = []

        def set_active(self, active):
            self.active.append(active)

    bus = EventBus()
    prices = SpyPrices(seed_prices(load_seed()), tick_interval=None, rng_seed=1)
    broker = FakeBroker(bus, prices, FAST)
    store = Store(broker, bus, Vault(tmp_path / "data"))
    app = create_app(broker, prices, bus, store)
    with TestClient(app) as client:
        assert prices.active[-1] is False  # locked + disconnected
        client.post("/login")
        wait_connected(client)
        assert prices.active[-1] is False  # connected but still locked
        store.unlock(bytes(32))
        client.post("/dev/drop-connection")  # any status event re-evaluates
        wait_connected(client)
        assert prices.active[-1] is True
        client.post("/logout")
        time.sleep(0.05)
        assert prices.active[-1] is False  # Stop


def test_execution_attributes_plan_and_outside_lots_fifo(logged_in):
    store = logged_in.app.state.test_store
    store.unlock(bytes(32))
    F = 9599491
    plan = {"id": "p1", "committed_at": "2026-10-22T10:00:00Z", "placed_at": "2026-10-22T10:01:00Z",
            "rows": [{"conid": F, "symbol": "F", "currency": "USD", "order_ref": "plan-f",
                      "planned_quantity": "100", "expected_return_pct": "10"}]}
    assert logged_in.post("/plans", json=plan).status_code == 204

    def settle(predicate):
        deadline = time.monotonic() + 3
        while not predicate():
            assert time.monotonic() < deadline
            time.sleep(0.02)

    logged_in.post("/orders", json=body(conid=F, qty="100", limit="12", ref="plan-f"))      # plan lot
    logged_in.post("/orders", json=body(conid=F, qty="50", limit="12", ref="outside-1"))    # outside lot
    settle(lambda: len([f for f in store.all_fills() if f["contract"]["conid"] == F]) == 2)
    report = logged_in.get("/execution").json()
    p1 = report["plans"][0]
    assert (p1["id"], p1["status"], p1["rows"][0]["bought"], p1["rows"][0]["held"]) == ("p1", "open", "100", "100")
    assert p1["rows"][0]["avg_price"] == "11.20" and p1["expected_profit"] == "112.00"  # 1 120 × 10 %
    outside_f = next(r for r in report["outside"]["rows"] if r["symbol"] == "F")
    assert outside_f["held"] == "50"

    logged_in.post("/orders", json=body(conid=F, side="SELL", qty="120", limit="11", ref="sell-1"))
    settle(lambda: len([f for f in store.all_fills() if f["contract"]["conid"] == F]) == 3)
    report = logged_in.get("/execution").json()
    p1 = report["plans"][0]
    assert (p1["status"], p1["rows"][0]["held"]) == ("closed", "0")      # oldest lot (the plan) sold first
    assert next(r for r in report["outside"]["rows"] if r["symbol"] == "F")["held"] == "30"
    assert logged_in.post("/plans", json={**plan, "rows": []}).status_code == 422


def test_plans_need_unlock(logged_in):
    plan = {"id": "p", "committed_at": "2026-10-22T10:00:00Z", "placed_at": "2026-10-22T10:00:00Z",
            "rows": [{"conid": 1, "symbol": "X", "currency": "USD", "order_ref": "r", "planned_quantity": "1"}]}
    assert logged_in.post("/plans", json=plan).status_code == 423


def test_transactions_csv_export_from_flex(logged_in):
    import httpx

    from test_flex import flex_transport

    assert logged_in.get("/transactions.csv?from=2026-01-01&to=2026-12-31").status_code == 423  # locked
    logged_in.app.state.test_store.unlock(bytes(32))
    response = logged_in.get("/transactions.csv?from=2026-01-01&to=2026-12-31")
    assert response.status_code == 409 and response.json()["error"] == "flex_not_set_up"
    flex = logged_in.app.state.flex
    flex.set_credentials("token", "123")
    flex._transport, flex._poll, flex._gap = flex_transport(), 0, 0
    response = logged_in.get("/transactions.csv?from=2026-01-01&to=2026-12-31")
    assert response.status_code == 200 and response.headers["content-type"].startswith("text/csv")
    assert response.text.splitlines() == [
        "date,type,ticker,quantity,price,fees,portfolio",
        "2026-01-10,BUY,NVDA,5,145.50,4.95,FAKE",
        "2026-04-01,SELL,NVDA,8,180.00,6.95,FAKE",
    ]
    assert logged_in.get("/transactions.csv?from=2026-02-01&to=2026-01-01").status_code == 422
    flex._transport = httpx.MockTransport(lambda request: httpx.Response(
        200, text="<FlexStatementResponse><Status>Fail</Status><ErrorCode>1012</ErrorCode>"
                  "<ErrorMessage>Token has expired.</ErrorMessage></FlexStatementResponse>"))
    response = logged_in.get("/transactions.csv?from=2026-01-01&to=2026-12-31")
    assert response.status_code == 502 and "Token has expired" in response.json()["message"]
