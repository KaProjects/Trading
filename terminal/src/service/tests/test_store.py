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
