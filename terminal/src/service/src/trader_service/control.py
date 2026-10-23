"""Private control channel from the app over our stdin (D3.3): JSON lines, never logged.

    {"type": "unlock", "data_key": "<base64, 32 bytes>"}   after Touch ID
    {"type": "lock"}                                       sleep / screen lock / window closed
    {"type": "finnhub_key", "key": "<api key>"}            price feed key from the Keychain (T4)
    {"type": "ibkr_credentials", "username": "…", "password": "…"}   right before Log in (T1, D1.3)
    {"type": "flex", "token": "…", "query_id": "…"}        IBKR Flex Web Service, after unlock (T11)
    EOF                                                    the app quit or crashed -> shut down
"""

import asyncio
import base64
import binascii
import json
import logging
import sys
from collections.abc import Callable

from trader_service.prices.base import PriceFeed
from trader_service.store import Store

log = logging.getLogger(__name__)


async def open_stdin() -> asyncio.StreamReader:
    loop = asyncio.get_running_loop()
    reader = asyncio.StreamReader()
    await loop.connect_read_pipe(lambda: asyncio.StreamReaderProtocol(reader), sys.stdin)
    return reader


def handle_message(line: bytes, store: Store, prices: PriceFeed | None = None, broker=None, flex=None) -> None:
    try:
        message = json.loads(line)
        kind = message["type"]
    except (ValueError, KeyError, TypeError):
        log.warning("ignoring malformed control message")  # content deliberately not logged
        return
    if kind == "unlock":
        try:
            key = base64.b64decode(message["data_key"], validate=True)
            store.unlock(key)
        except (KeyError, binascii.Error, ValueError):
            log.warning("ignoring invalid unlock message")
    elif kind == "lock":
        store.lock()
    elif kind == "finnhub_key":
        key = message.get("key")
        if isinstance(key, str) and key and prices is not None and hasattr(prices, "set_key"):
            prices.set_key(key)
        else:
            log.warning("ignoring finnhub_key message")
    elif kind == "ibkr_credentials":
        username, password = message.get("username"), message.get("password")
        if isinstance(username, str) and isinstance(password, str) and username and password \
                and broker is not None and hasattr(broker, "set_credentials"):
            broker.set_credentials(username, password)
        else:
            log.warning("ignoring ibkr_credentials message")
    elif kind == "flex":
        token, query_id = message.get("token"), message.get("query_id")
        if isinstance(token, str) and isinstance(query_id, str) and token and query_id and flex is not None:
            flex.set_credentials(token, query_id)
        else:
            log.warning("ignoring flex message")
    else:
        log.warning("ignoring unknown control message type")


async def run_control(reader: asyncio.StreamReader, store: Store, on_eof: Callable[[], None],
                      prices: PriceFeed | None = None, on_change: Callable[[], None] = lambda: None,
                      broker=None, flex=None) -> None:
    while line := await reader.readline():
        handle_message(line, store, prices, broker, flex)
        on_change()
    store.lock()
    on_eof()
