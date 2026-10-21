"""What the service persists (D3.1), always through the encrypted Vault:

- `snapshot.enc`            last account snapshot (funds, positions, orders, fills), overwritten on change
- `activity-YYYY-MM.enc`    activity log (logins, disconnects, orders, fills), kept ~90 days
- `trades-YYYY-MM.enc`      own trade history: every fill seen, de-duplicated by execution ID, kept forever

While locked, activity entries and fills are kept in memory and written on the next unlock.
"""

import asyncio
import contextlib
import logging
from datetime import UTC, datetime, timedelta
from typing import Any

from trader_service.broker.base import Broker, BrokerError
from trader_service.events import EventBus
from trader_service.models import ConnectionState, Event, RuleThresholds
from trader_service.vault import Vault, VaultCorrupt

log = logging.getLogger(__name__)

SNAPSHOT = "snapshot.enc"
SETTINGS = "settings.enc"
SNAPSHOT_EVENTS = {"account", "position", "order", "fill", "status"}


def _month(time: datetime) -> str:
    return f"{time.year:04d}-{time.month:02d}"


def _parse(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


class Store:
    def __init__(self, broker: Broker, bus: EventBus, vault: Vault, snapshot_interval: float = 1.0,
                 activity_retention: timedelta = timedelta(days=90)) -> None:
        self._broker = broker
        self._bus = bus
        self._vault = vault
        self._snapshot_interval = snapshot_interval
        self._activity_retention = activity_retention
        self._pending_activity: list[dict] = []
        self._pending_fills: list[dict] = []
        self._dirty = False
        self._tasks: list[asyncio.Task] = []
        self._thresholds = RuleThresholds()

    # --- lifecycle -------------------------------------------------------------------------------

    async def start(self) -> None:
        queue = self._bus.subscribe()
        self._tasks = [asyncio.create_task(self._consume(queue)), asyncio.create_task(self._snapshot_loop())]

    async def stop(self) -> None:
        for task in self._tasks:
            task.cancel()
        for task in self._tasks:
            with contextlib.suppress(asyncio.CancelledError):
                await task
        self.flush_snapshot()

    @property
    def unlocked(self) -> bool:
        return self._vault.unlocked

    def unlock(self, key: bytes) -> None:
        self._vault.unlock(key)
        self.record("vault", action="unlocked")
        saved = self._read(SETTINGS) or {}
        self._thresholds = RuleThresholds(**saved.get("rules", {}))
        self._flush_pending()
        self._prune_activity()
        self._sync_recent_fills()
        self._dirty = True
        self.flush_snapshot()

    def lock(self) -> None:
        self.flush_snapshot()
        self._flush_pending()
        self._vault.lock()
        self.record("vault", action="locked")

    # --- reads (require unlock) ------------------------------------------------------------------

    def snapshot(self) -> dict | None:
        return self._read(SNAPSHOT)

    def history(self, days: int, now: datetime | None = None) -> list[dict]:
        now = now or datetime.now(UTC)
        since = now - timedelta(days=days)
        months = {_month(since + timedelta(days=d)) for d in range(days + 1)} | {_month(now)}
        fills = []
        for month in sorted(months):
            fills.extend(f for f in self._read(f"trades-{month}.enc") or [] if _parse(f["time"]) >= since)
        return sorted(fills, key=lambda f: f["time"], reverse=True)

    def activity(self, month: str) -> list[dict]:
        return self._read(f"activity-{month}.enc") or []

    def rule_thresholds(self) -> RuleThresholds:
        """Defaults until unlocked; the saved values after."""
        return self._thresholds

    # --- writes ------------------------------------------------------------------------------------

    def save_rule_thresholds(self, thresholds: RuleThresholds) -> None:
        settings = self._read(SETTINGS) or {}
        settings["rules"] = thresholds.model_dump(mode="json")
        self._vault.write(SETTINGS, settings)
        self._thresholds = thresholds
        self.record("settings", rules=settings["rules"])

    def record(self, kind: str, **detail: Any) -> None:
        entry = {"time": datetime.now(UTC).isoformat(), "kind": kind, **detail}
        self._pending_activity.append(entry)
        if self._vault.unlocked:
            self._flush_pending()

    def add_fill(self, fill: dict) -> None:
        self._pending_fills.append(fill)
        if self._vault.unlocked:
            self._flush_pending()

    def flush_snapshot(self) -> None:
        if not (self._dirty and self._vault.unlocked):
            return
        if self._broker.status().state != ConnectionState.CONNECTED:
            return  # keep the last good snapshot; nothing fresher to write
        try:
            snapshot = {
                "as_of": datetime.now(UTC).isoformat(),
                "account": self._broker.account_summary().model_dump(mode="json"),
                "positions": [p.model_dump(mode="json") for p in self._broker.positions()],
                "orders": [o.model_dump(mode="json") for o in self._broker.open_orders()],
                "fills": [f.model_dump(mode="json") for f in self._broker.fills(7)],
            }
        except BrokerError:
            return
        self._vault.write(SNAPSHOT, snapshot)
        self._dirty = False

    def _flush_pending(self) -> None:
        if not self._vault.unlocked:
            return
        activity, self._pending_activity = self._pending_activity, []
        by_month: dict[str, list[dict]] = {}
        for entry in activity:
            by_month.setdefault(_month(_parse(entry["time"])), []).append(entry)
        for month, entries in by_month.items():
            name = f"activity-{month}.enc"
            self._vault.write(name, (self._read(name) or []) + entries)

        fills, self._pending_fills = self._pending_fills, []
        by_month = {}
        for fill in fills:
            by_month.setdefault(_month(_parse(fill["time"])), []).append(fill)
        for month, new in by_month.items():
            name = f"trades-{month}.enc"
            existing = self._read(name) or []
            known = {f["exec_id"] for f in existing}
            added = []
            for fill in new:
                if fill["exec_id"] not in known:
                    known.add(fill["exec_id"])
                    added.append(fill)
            if added:
                self._vault.write(name, existing + added)

    def _sync_recent_fills(self) -> None:
        """IBKR only returns ~7 days of executions; copy them into our own history on every connect/unlock."""
        if self._broker.status().state != ConnectionState.CONNECTED:
            return
        try:
            for fill in self._broker.fills(7):
                self._pending_fills.append(fill.model_dump(mode="json"))
        except BrokerError:
            return
        self._flush_pending()

    def _prune_activity(self, now: datetime | None = None) -> None:
        cutoff = _month((now or datetime.now(UTC)) - self._activity_retention)
        for name in self._vault.names("activity-"):
            if name[len("activity-"):-len(".enc")] < cutoff:
                self._vault.delete(name)

    def _read(self, name: str) -> Any:
        try:
            return self._vault.read(name)
        except VaultCorrupt:
            log.warning("unreadable encrypted file %s (wrong key?), moved aside", name)
            self._vault.quarantine(name)
            return None

    # --- event handling ------------------------------------------------------------------------------

    async def _consume(self, queue: asyncio.Queue[Event]) -> None:
        while True:
            event = await queue.get()
            try:
                self._handle(event)
            except Exception:  # persistence must never take the service down
                log.exception("failed to persist %s event", event.type)

    def _handle(self, event: Event) -> None:
        data = event.data
        if event.type in SNAPSHOT_EVENTS:
            self._dirty = True
        if event.type == "status":
            self.record("status", state=data.get("state"), message=data.get("message"))
            if data.get("state") == ConnectionState.CONNECTED:
                self._sync_recent_fills()
        elif event.type == "order":
            self.record("order", order_id=data.get("order_id"), order_ref=data.get("order_ref"),
                        symbol=data.get("contract", {}).get("symbol"), side=data.get("side"),
                        quantity=data.get("quantity"), limit_price=data.get("limit_price"),
                        status=data.get("status"), filled=data.get("filled"), reason=data.get("reject_reason"))
        elif event.type == "fill":
            self.add_fill(data)
            self.record("fill", exec_id=data.get("exec_id"), symbol=data.get("contract", {}).get("symbol"),
                        side=data.get("side"), quantity=data.get("quantity"), price=data.get("price"))

    async def _snapshot_loop(self) -> None:
        while True:
            await asyncio.sleep(self._snapshot_interval)
            try:
                self.flush_snapshot()
            except Exception:
                log.exception("failed to write snapshot")
