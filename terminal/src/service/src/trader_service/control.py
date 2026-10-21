"""Private control channel from the app over our stdin (D3.3): JSON lines, never logged.

    {"type": "unlock", "data_key": "<base64, 32 bytes>"}   after Touch ID
    {"type": "lock"}                                       sleep / screen lock / window closed
    EOF                                                    the app quit or crashed -> shut down
"""

import asyncio
import base64
import binascii
import json
import logging
import sys
from collections.abc import Callable

from trader_service.store import Store

log = logging.getLogger(__name__)


async def open_stdin() -> asyncio.StreamReader:
    loop = asyncio.get_running_loop()
    reader = asyncio.StreamReader()
    await loop.connect_read_pipe(lambda: asyncio.StreamReaderProtocol(reader), sys.stdin)
    return reader


def handle_message(line: bytes, store: Store) -> None:
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
    else:
        log.warning("ignoring unknown control message type")


async def run_control(reader: asyncio.StreamReader, store: Store, on_eof: Callable[[], None]) -> None:
    while line := await reader.readline():
        handle_message(line, store)
    store.lock()
    on_eof()
