import asyncio
from decimal import Decimal

import pytest

from trader_service.broker.fake import FakeBroker, FakeConfig, load_seed, seed_prices
from trader_service.events import EventBus
from trader_service.models import ConnectionState
from trader_service.prices.fake import FakePrices

FAST = FakeConfig(twofa_delay=0.01, twofa_timeout=0.2, submit_delay=0.0, reconnect_delay=0.01)


@pytest.fixture
def bus() -> EventBus:
    return EventBus()


@pytest.fixture
def prices() -> FakePrices:
    return FakePrices(seed_prices(load_seed()), tick_interval=None, rng_seed=1)


@pytest.fixture
async def broker(bus, prices):
    broker = FakeBroker(bus, prices, FAST)
    await broker.start()
    yield broker
    await broker.stop()


@pytest.fixture
async def connected(broker):
    await broker.login()
    await wait_for_state(broker, ConnectionState.CONNECTED)
    return broker


async def wait_for_state(broker, state: ConnectionState, timeout: float = 1.0) -> None:
    async with asyncio.timeout(timeout):
        while broker.status().state != state:
            await asyncio.sleep(0.005)


async def settle() -> None:
    """Let PendingSubmit -> Submitted tasks run."""
    for _ in range(5):
        await asyncio.sleep(0)


def D(value) -> Decimal:
    return Decimal(str(value))
