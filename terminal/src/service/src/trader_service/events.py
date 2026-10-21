"""In-process fan-out of events to every WebSocket subscriber."""

import asyncio
from datetime import UTC, datetime

from pydantic import BaseModel

from trader_service.models import Event


class EventBus:
    def __init__(self, queue_size: int = 1000) -> None:
        self._subscribers: set[asyncio.Queue[Event]] = set()
        self._queue_size = queue_size

    def subscribe(self) -> asyncio.Queue[Event]:
        queue: asyncio.Queue[Event] = asyncio.Queue(self._queue_size)
        self._subscribers.add(queue)
        return queue

    def unsubscribe(self, queue: asyncio.Queue[Event]) -> None:
        self._subscribers.discard(queue)

    def publish(self, type_: str, data: BaseModel | dict) -> None:
        payload = data.model_dump(mode="json") if isinstance(data, BaseModel) else data
        event = Event(type=type_, data=payload, time=datetime.now(UTC))
        for queue in list(self._subscribers):
            try:
                queue.put_nowait(event)
            except asyncio.QueueFull:
                # A subscriber that stopped reading is dropped rather than blocking everyone else.
                self._subscribers.discard(queue)
