"""T10: positions as stacks of lots, sells reduced FIFO (D10.1).

Every buy fill opens a lot — from a plan (its order_ref belongs to a saved plan), or "outside" (any other buy).
Shares held before the recorded history form an "initial" lot. Sells consume lots oldest first. If the lots
don't add up to the broker's position (missed fills), a "correction" keeps them matching the broker.
Pure functions: no I/O, easy to test.
"""

from dataclasses import dataclass, field
from datetime import UTC, datetime
from decimal import Decimal

EPOCH = datetime(1970, 1, 1, tzinfo=UTC)


@dataclass
class Lot:
    conid: int
    source: str                 # "initial" | "outside" | "plan" | "correction"
    plan_id: str | None
    quantity: Decimal           # shares bought into this lot
    price: Decimal              # per share, contract currency
    time: datetime
    remaining: Decimal = field(default=Decimal(0))
    realized: Decimal = field(default=Decimal(0))  # profit from shares of this lot already sold
    sales: list["Sale"] = field(default_factory=list)  # shares of this lot sold, per sell fill

    def __post_init__(self) -> None:
        if self.remaining == 0:
            self.remaining = self.quantity


@dataclass
class Sale:
    quantity: Decimal
    price: Decimal              # sell price, contract currency
    time: datetime


def base_ref(ref: str) -> str:
    """A plan row's order reference without the attempt suffix: `abc.2` (placed again after IBKR cancelled
    `abc`) belongs to the same row as `abc`."""
    head, _, attempt = ref.rpartition(".")
    return head if head and attempt.isdigit() else ref


def _time(value) -> datetime:
    return value if isinstance(value, datetime) else datetime.fromisoformat(str(value).replace("Z", "+00:00"))


def build_lots(fills: list[dict], positions: dict[int, tuple[Decimal, Decimal]],
               plan_of_ref: dict[str, str]) -> dict[int, list[Lot]]:
    """fills: trade-history fills (dicts as stored); positions: conid → (quantity, average cost) from the broker;
    plan_of_ref: order_ref → plan id. Returns conid → lots in FIFO order (closed lots included)."""
    by_conid: dict[int, list[dict]] = {}
    for fill in fills:
        by_conid.setdefault(int(fill["contract"]["conid"]), []).append(fill)
    result: dict[int, list[Lot]] = {}
    for conid in set(by_conid) | set(positions):
        history = sorted(by_conid.get(conid, []), key=lambda f: (_time(f["time"]), f["exec_id"]))
        held, avg_cost = positions.get(conid, (Decimal(0), Decimal(0)))
        net = sum((Decimal(str(f["quantity"])) * (1 if f["side"] == "BUY" else -1) for f in history), Decimal(0))
        lots: list[Lot] = []
        # Shares held before the first recorded fill.
        initial = held - net
        if initial > 0:
            lots.append(Lot(conid, "initial", None, initial, avg_cost, EPOCH))
        for fill in history:
            quantity, price = Decimal(str(fill["quantity"])), Decimal(str(fill["price"]))
            if fill["side"] == "BUY":
                plan_id = plan_of_ref.get(base_ref(fill.get("order_ref") or ""))
                lots.append(Lot(conid, "plan" if plan_id else "outside", plan_id, quantity, price, _time(fill["time"])))
            else:
                _sell_fifo(lots, quantity, price, _time(fill["time"]))
        # Keep the lots equal to the broker's position (missed fills, history starting mid-way).
        remaining = sum((lot.remaining for lot in lots), Decimal(0))
        if remaining > held:
            _sell_fifo(lots, remaining - held, None, datetime.now(UTC))
        elif remaining < held:
            lots.append(Lot(conid, "correction", None, held - remaining, avg_cost, datetime.now(UTC)))
        result[conid] = lots
    return result


def _sell_fifo(lots: list[Lot], quantity: Decimal, price: Decimal | None, time: datetime) -> None:
    """Reduce the oldest open lots first. `price` None = correction (no realized profit recorded)."""
    left = quantity
    for lot in lots:
        if left <= 0:
            break
        if lot.remaining <= 0:
            continue
        taken = min(lot.remaining, left)
        lot.remaining -= taken
        if price is not None:
            lot.realized += taken * (price - lot.price)
            lot.sales.append(Sale(taken, price, time))
        left -= taken
