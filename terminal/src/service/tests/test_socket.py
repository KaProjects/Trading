"""End to end: the real `trader-service` process on an owner-only Unix socket."""

import os
import shutil
import stat
import subprocess
import sys
import tempfile
import time
from pathlib import Path

import httpx
import pytest


@pytest.fixture
def service():
    folder = Path(tempfile.mkdtemp(prefix="tt-")) / "app"  # short path: AF_UNIX paths max ~104 bytes on macOS
    sock = folder / "service.sock"
    proc = subprocess.Popen([sys.executable, "-m", "trader_service.main", "--env", "fake", "--socket", str(sock),
                             "--log-level", "warning"])
    deadline = time.monotonic() + 10
    while not sock.exists():
        assert proc.poll() is None, "service exited"
        assert time.monotonic() < deadline, "socket not created"
        time.sleep(0.05)
    yield sock, proc
    if proc.poll() is None:
        proc.terminate()
        proc.wait(timeout=10)
    shutil.rmtree(folder.parent)


def test_socket_is_owner_only_and_serves_api(service):
    service, _ = service
    assert stat.S_ISSOCK(service.lstat().st_mode)
    assert stat.S_IMODE(service.lstat().st_mode) == 0o600
    assert stat.S_IMODE(os.stat(service.parent).st_mode) == 0o700

    with httpx.Client(transport=httpx.HTTPTransport(uds=str(service)), base_url="http://service") as client:
        assert client.get("/status").json()["environment"] == "fake"
        client.post("/login")
        deadline = time.monotonic() + 10  # fake 2FA takes 1 s by default
        while client.get("/status").json()["state"] != "connected":
            assert time.monotonic() < deadline
            time.sleep(0.1)
        assert client.get("/account").json()["account_id"] == "FAKE0001"


def test_socket_removed_on_shutdown(service):
    sock, proc = service
    with httpx.Client(transport=httpx.HTTPTransport(uds=str(sock)), base_url="http://service") as client:
        client.get("/status").raise_for_status()  # serving -> uvicorn's SIGTERM handler is installed
    proc.terminate()
    assert proc.wait(timeout=10) == 0
    assert not sock.exists()


def test_service_exits_when_stdin_closes():
    folder = Path(tempfile.mkdtemp(prefix="tt-")) / "app"
    sock = folder / "service.sock"
    proc = subprocess.Popen([sys.executable, "-m", "trader_service.main", "--env", "fake", "--socket", str(sock),
                             "--log-level", "warning", "--watch-stdin"], stdin=subprocess.PIPE)
    try:
        deadline = time.monotonic() + 10
        while True:
            assert time.monotonic() < deadline, "service not serving"
            try:
                with httpx.Client(transport=httpx.HTTPTransport(uds=str(sock)), base_url="http://service") as c:
                    c.get("/status").raise_for_status()
                break
            except httpx.TransportError:
                time.sleep(0.05)
        proc.stdin.close()  # the app went away
        assert proc.wait(timeout=10) == 0
        assert not sock.exists()
    finally:
        if proc.poll() is None:
            proc.kill()
        shutil.rmtree(folder.parent)


def test_unlock_over_stdin_writes_only_encrypted_files():
    import base64
    import json

    folder = Path(tempfile.mkdtemp(prefix="tt-")) / "app"
    sock = folder / "service.sock"
    proc = subprocess.Popen([sys.executable, "-m", "trader_service.main", "--env", "fake", "--socket", str(sock),
                             "--log-level", "warning", "--watch-stdin"], stdin=subprocess.PIPE)
    try:
        transport = httpx.HTTPTransport(uds=str(sock))
        deadline = time.monotonic() + 10
        while True:
            assert time.monotonic() < deadline, "service not serving"
            try:
                with httpx.Client(transport=transport, base_url="http://service") as c:
                    c.post("/login").raise_for_status()
                break
            except httpx.TransportError:
                time.sleep(0.05)
        with httpx.Client(transport=transport, base_url="http://service") as c:
            while c.get("/status").json()["state"] != "connected":
                assert time.monotonic() < deadline
                time.sleep(0.05)
            assert c.get("/snapshot").status_code == 423
            proc.stdin.write(json.dumps({"type": "unlock", "data_key": base64.b64encode(bytes(range(32))).decode()})
                             .encode() + b"\n")
            proc.stdin.flush()
            while not c.get("/vault").json()["unlocked"]:
                assert time.monotonic() < deadline
                time.sleep(0.05)
            assert c.get("/snapshot").json()["account"]["account_id"] == "FAKE0001"

        files = list((folder / "fake").iterdir())
        assert {f.name.split("-")[0] for f in files} >= {"snapshot.enc", "activity", "trades"}
        for f in files:
            raw = f.read_bytes()
            assert raw.startswith(b"TTE1"), f.name
            for secret in (b"FAKE0001", b"AAPL", b"25000"):
                assert secret not in raw, (f.name, secret)
        proc.stdin.close()
        assert proc.wait(timeout=10) == 0
    finally:
        if proc.poll() is None:
            proc.kill()
        shutil.rmtree(folder.parent)
