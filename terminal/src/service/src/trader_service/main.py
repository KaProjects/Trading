"""Entry point: `trader-service --env fake --socket PATH`. Serves the API on an owner-only Unix socket (D2.6)."""

import argparse
import asyncio
import os
import signal
import socket
import stat
import sys
from pathlib import Path

import uvicorn

from trader_service.api import create_app
from trader_service.control import open_stdin
from trader_service.broker.fake import FakeBroker, load_seed, seed_prices
from trader_service.events import EventBus
from trader_service.models import Environment
from trader_service.prices.fake import FakePrices
from trader_service.store import Store
from trader_service.vault import Vault

DEFAULT_SOCKET = Path.home() / "Library/Application Support/org.kaleta.trader-terminal.dev/service.sock"


def bind_unix_socket(path: Path) -> socket.socket:
    """Bind with owner-only permissions: folder 0700, socket 0600. Removes a stale socket, nothing else."""
    path.parent.mkdir(parents=True, exist_ok=True)
    os.chmod(path.parent, 0o700)
    if path.exists() or path.is_symlink():
        if not stat.S_ISSOCK(path.lstat().st_mode):
            raise SystemExit(f"Refusing to replace non-socket file: {path}")
        path.unlink()
    previous = os.umask(0o177)
    try:
        sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        sock.bind(str(path))
    finally:
        os.umask(previous)
    os.chmod(path, 0o600)
    sock.listen(128)  # clients connecting before uvicorn's accept loop starts wait instead of being refused
    return sock


def build(environment: Environment, data_dir: Path, control: bool = False):
    bus = EventBus()
    if environment != Environment.FAKE:
        raise SystemExit(f"Environment '{environment}' is not implemented yet (IBKR adapter comes with T1)")
    seed = load_seed()
    prices = FakePrices(seed_prices(seed))
    broker = FakeBroker(bus, prices, seed=seed)
    store = Store(broker, bus, Vault(data_dir))
    # EOF on the control channel = the app is gone -> SIGTERM ourselves for uvicorn's graceful shutdown.
    return create_app(broker, prices, bus, store, control=open_stdin if control else None,
                      on_control_eof=lambda: signal.raise_signal(signal.SIGTERM))


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="trader-service")
    parser.add_argument("--env", type=Environment, choices=list(Environment), default=Environment.FAKE)
    parser.add_argument("--socket", type=Path, default=DEFAULT_SOCKET)
    parser.add_argument("--log-level", default="info")
    parser.add_argument("--data-dir", type=Path,
                        help="encrypted data folder (default: <socket folder>/<env>)")
    parser.add_argument("--watch-stdin", action="store_true",
                        help="read the app's control channel (unlock/lock) on stdin; exit when it closes")
    args = parser.parse_args(argv)

    app = build(args.env, args.data_dir or args.socket.parent / args.env.value, control=args.watch_stdin)
    sock = bind_unix_socket(args.socket)
    server = uvicorn.Server(uvicorn.Config(app, log_level=args.log_level, ws="auto"))
    # uvicorn re-raises SIGTERM after its graceful shutdown; turn it into a normal exit so `finally` cleans up.
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(0))
    try:
        asyncio.run(server.serve(sockets=[sock]))
    finally:
        sock.close()
        args.socket.unlink(missing_ok=True)
    sys.exit(0)


if __name__ == "__main__":
    main()
