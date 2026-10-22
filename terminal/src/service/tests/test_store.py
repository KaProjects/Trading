import base64
import json
import os
import stat
from datetime import UTC, datetime, timedelta

import pytest

from conftest import D, settle
from trader_service.control import handle_message
from trader_service.models import PlaceOrderRequest, Side
from trader_service.store import Store
from trader_service.vault import Vault, VaultCorrupt, VaultLocked

KEY = bytes(range(32))
OTHER_KEY = bytes(32)


# --- vault ----------------------------------------------------------------------------------------------


def test_vault_round_trip_is_encrypted_and_owner_only(tmp_path):
    vault = Vault(tmp_path / "data")
    vault.unlock(KEY)
    vault.write("x.enc", {"account": "FAKE0001", "cash": "123.45"})
    raw = (tmp_path / "data" / "x.enc").read_bytes()
    assert raw.startswith(b"TTE1") and b"FAKE0001" not in raw and b"123.45" not in raw
    assert stat.S_IMODE(os.stat(tmp_path / "data" / "x.enc").st_mode) == 0o600
    assert stat.S_IMODE(os.stat(tmp_path / "data").st_mode) == 0o700
    assert vault.read("x.enc") == {"account": "FAKE0001", "cash": "123.45"}


def test_vault_locked_and_wrong_key(tmp_path):
    vault = Vault(tmp_path)
    with pytest.raises(VaultLocked):
        vault.read("x.enc")
    vault.unlock(KEY)
    vault.write("x.enc", [1])
    vault.unlock(OTHER_KEY)
    with pytest.raises(VaultCorrupt):
        vault.read("x.enc")


def test_vault_file_cannot_be_swapped(tmp_path):
    vault = Vault(tmp_path)
    vault.unlock(KEY)
    vault.write("a.enc", "A")
    (tmp_path / "b.enc").write_bytes((tmp_path / "a.enc").read_bytes())
    with pytest.raises(VaultCorrupt):
        vault.read("b.enc")  # the file name is bound into the ciphertext


def test_vault_rejects_bad_key_length(tmp_path):
    with pytest.raises(ValueError):
        Vault(tmp_path).unlock(b"short")


# --- store ----------------------------------------------------------------------------------------------


@pytest.fixture
async def store(connected, bus, tmp_path):
    store = Store(connected, bus, Vault(tmp_path / "data"), snapshot_interval=0.01)
    await store.start()
    yield store
    await store.stop()


async def test_activity_buffered_while_locked_then_written(store):
    store.record("test", note="while locked")
    assert not (store._vault.folder / f"activity-{datetime.now(UTC):%Y-%m}.enc").exists()
    store.unlock(KEY)
    entries = store.activity(f"{datetime.now(UTC):%Y-%m}")
    assert [e["kind"] for e in entries][:2] == ["test", "vault"]


async def test_unlock_writes_snapshot_and_copies_recent_fills(store):
    store.unlock(KEY)
    snapshot = store.snapshot()
    assert snapshot["account"]["account_id"] == "FAKE0001"
    assert len(snapshot["fills"]) == 4
    assert len(store.history(30)) == 4


async def test_new_fill_lands_in_history_exactly_once(store, connected):
    store.unlock(KEY)
    await connected.place_order(PlaceOrderRequest(conid=265598, side=Side.BUY, quantity=D(1),
                                                  limit_price=D(230), order_ref="h1"))
    await settle()
    for _ in range(20):
        await settle()
    store._sync_recent_fills()  # a reconnect re-copies the 7-day window
    exec_ids = [f["exec_id"] for f in store.history(30)]
    assert len(exec_ids) == 5 and len(set(exec_ids)) == 5


async def test_fill_saved_without_fees_gets_them_later(store):
    """T11: IBKR's commission report can come after the fill was first saved (sync on connect)."""
    store.unlock(KEY)
    fill = dict(store.history(30)[0])
    store._pending_fills.append({**fill, "commission": "0"})
    store._flush_pending()
    store._pending_fills.append({**fill, "exec_id": fill["exec_id"], "commission": "0"})
    store._flush_pending()
    assert next(f for f in store.history(30) if f["exec_id"] == fill["exec_id"])["commission"] == fill["commission"]
    later = {**fill, "exec_id": "NEW-1", "commission": "0"}
    store._pending_fills.append(later)
    store._flush_pending()
    store._pending_fills.append({**later, "commission": "1.23"})
    store._flush_pending()
    saved = [f for f in store.history(30) if f["exec_id"] == "NEW-1"]
    assert len(saved) == 1 and saved[0]["commission"] == "1.23"


async def test_fills_while_locked_are_written_on_unlock(store, connected):
    await connected.place_order(PlaceOrderRequest(conid=265598, side=Side.BUY, quantity=D(1),
                                                  limit_price=D(230), order_ref="h2"))
    for _ in range(20):
        await settle()
    store.unlock(KEY)
    assert len(store.history(30)) == 5


async def test_snapshot_kept_when_disconnected(store, connected):
    store.unlock(KEY)
    before = store.snapshot()["as_of"]
    await connected.logout()
    store._dirty = True
    store.flush_snapshot()
    assert store.snapshot()["as_of"] == before


async def test_lock_drops_key(store):
    store.unlock(KEY)
    store.lock()
    with pytest.raises(VaultLocked):
        store.snapshot()


async def test_unreadable_files_are_moved_aside(store):
    store.unlock(KEY)
    store.lock()
    store.unlock(OTHER_KEY)  # e.g. Keychain item was recreated
    assert store.snapshot() is not None  # fresh snapshot written with the new key
    assert any(p.name.endswith(".unreadable") for p in store._vault.folder.iterdir())


async def test_old_activity_pruned(store):
    store.unlock(KEY)
    store._vault.write("activity-2020-01.enc", [])
    store._prune_activity()
    assert "activity-2020-01.enc" not in store._vault.names("activity-")


async def test_history_respects_days(store):
    store.unlock(KEY)
    now = datetime.now(UTC)
    assert len(store.history(3, now=now)) == 2
    assert len(store.history(7, now=now + timedelta(days=30))) == 0


# --- control channel -----------------------------------------------------------------------------------


async def test_control_messages(store, caplog):
    secret = base64.b64encode(KEY).decode()
    handle_message(json.dumps({"type": "unlock", "data_key": secret}).encode(), store)
    assert store.unlocked
    handle_message(b'{"type": "lock"}', store)
    assert not store.unlocked
    handle_message(b"not json", store)
    handle_message(b'{"type": "unlock", "data_key": "!!!"}', store)
    assert not store.unlocked
    assert secret not in caplog.text


async def test_finnhub_key_message_reaches_feed_and_is_not_logged(store, caplog):
    class Feed:
        key = None

        def set_key(self, key):
            self.key = key

    feed = Feed()
    handle_message(b'{"type": "finnhub_key", "key": "secret-finnhub-key"}', store, feed)
    assert feed.key == "secret-finnhub-key"
    handle_message(b'{"type": "finnhub_key", "key": ""}', store, feed)
    assert feed.key == "secret-finnhub-key"
    assert "secret-finnhub-key" not in caplog.text


async def test_dev_plan_seed_shows_as_executed_plan(bus, tmp_path):
    from trader_service.broker.fake import load_seed, seed_prices, with_dev_plan
    from trader_service.execution import execution_report
    from trader_service.models import PlanRecord
    from trader_service.prices.fake import FakePrices
    from conftest import FAST, wait_for_state
    from trader_service.broker.fake import FakeBroker
    from trader_service.models import ConnectionState

    seed, plans = with_dev_plan(load_seed())
    prices = FakePrices(seed_prices(seed), tick_interval=None)
    broker = FakeBroker(bus, prices, FAST, seed=seed)
    store = Store(broker, bus, Vault(tmp_path / "fake"), seed_plans=plans,
                  seed_fills=[f.model_dump(mode="json") for f in broker.seed_fills()])
    await store.start()
    await broker.login()
    await wait_for_state(broker, ConnectionState.CONNECTED)
    store.unlock(KEY)
    report = execution_report([PlanRecord(**p) for p in store.plans()], store.all_fills(), broker.positions(), "USD",
                              lambda c: prices.last(broker.contract(c).price_symbol).last, broker.fx_rate,
                              {c: (broker.contract(c).symbol, "USD") for c in (9939, 755000001)})
    by_id = {p.id: p for p in report.plans}
    old, dev = by_id["dev-plan-0"], by_id["dev-plan-1"]
    assert dev.status == "partly_sold"
    assert [(r.symbol, r.bought, r.expected_return_pct) for r in dev.rows] == [("SNDK", 10, 20), ("MU", 10, 20)]
    assert old.status == "closed"  # month-old plan, bought and sold again
    assert [(r.symbol, r.bought, r.held, r.realized) for r in old.rows] == [("SNDK", 6, 0, 1440), ("MU", 15, 0, 1620)]
    assert [(l.kind, l.quantity, l.price) for l in old.rows[1].lines] == [("sold", 8, 800), ("sold", 7, 860)]
    assert [(l.kind, l.quantity) for l in dev.rows[0].lines] == [("sold", 5), ("held", 5)]
    assert old.rows[1].difference == old.rows[1].profit - old.rows[1].expected_profit
    await store.stop()
    await broker.stop()
