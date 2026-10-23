"""IBC: starts IB Gateway and types in the username/password, so the only manual step is the phone 2FA (T1).

Credentials go into a short-lived copy of IBC's config.ini (owner-only, D1.3) that is deleted as soon as the
Gateway is logged in, on failure and on stop. The template `~/ibc/config.ini` itself never holds credentials.
The same file also sets the Gateway's API: our port, localhost only, read-only off.
"""

import asyncio
import contextlib
import os
import re
import signal
import subprocess
from dataclasses import dataclass, field
from pathlib import Path

from trader_service.models import Environment

GATEWAY_PORT = {Environment.PAPER: 4002, Environment.PRODUCTION: 4001}
TRADING_MODE = {Environment.PAPER: "paper", Environment.PRODUCTION: "live"}


class IbcError(Exception):
    pass


@dataclass
class IbcConfig:
    ibc_path: Path = field(default_factory=lambda: Path.home() / "ibc")
    tws_path: Path = field(default_factory=lambda: Path.home() / "Applications")
    login_timeout: float = 180.0  # D2.4: 3 minutes for the 2FA approval
    read_only_api: bool = False   # orders are in scope on paper (T5); live decides in T7


def gateway_version(tws_path: Path) -> str:
    """Newest installed `IB Gateway <major.minor>` folder, e.g. `10.50` (what ibcstart.sh calls twsVersion)."""
    versions = []
    for folder in tws_path.glob("IB Gateway *"):
        match = re.fullmatch(r"IB Gateway (\d+)\.(\d+)", folder.name)
        if match:
            versions.append((int(match[1]), int(match[2]), f"{match[1]}.{match[2]}"))
    if not versions:
        raise IbcError(f"IB Gateway not found in {tws_path}")
    return max(versions)[2]


def login_ini(template: str, username: str, password: str, environment: Environment, read_only_api: bool) -> str:
    """IBC config with credentials and API settings filled in; every other line of the template is kept."""
    if any(c in value for value in (username, password) for c in "\r\n"):
        raise IbcError("Username or password contains a line break")
    values = {
        "IbLoginId": username,
        "IbPassword": password,
        "TradingMode": TRADING_MODE[environment],
        "ReadOnlyApi": "yes" if read_only_api else "no",
        "OverrideTwsApiPort": str(GATEWAY_PORT[environment]),
        "AcceptIncomingConnectionAction": "reject",  # only localhost (trusted by default) may connect
        "TrustedTwsApiClientIPs": "",
        "ExistingSessionDetectedAction": "primary",  # this session wins over an older one
        "ReloginAfterSecondFactorAuthenticationTimeout": "no",
        "SecondFactorAuthenticationTimeout": "180",
        "AcceptNonBrokerageAccountWarning": "yes",  # the paper account's "not a brokerage account" dialog
    }
    lines, seen = [], set()
    for line in template.replace("\r\n", "\n").split("\n"):
        key = line.split("=", 1)[0].strip()
        if "=" in line and not line.lstrip().startswith("#") and key in values:
            lines.append(f"{key}={values[key]}")
            seen.add(key)
        else:
            lines.append(line)
    lines += [f"{key}={value}" for key, value in values.items() if key not in seen]
    return "\r\n".join(lines)  # IBC's own files use CRLF


class IbcProcess:
    """One Gateway run: write the login config, start ibcstart.sh in its own session, stop it again.

    Its process ID is kept in `ibc.pid`, so a Gateway left running by an earlier service run (Debug, paper:
    `detach`) can be found again — to connect to it, or to stop it.
    """

    def __init__(self, config: IbcConfig, environment: Environment, work_dir: Path) -> None:
        self._config = config
        self._environment = environment
        self._ini = work_dir / "ibc-login.ini"
        self._pid_file = work_dir / "ibc.pid"
        # Plain Popen, not an asyncio subprocess: asyncio kills its children when the loop closes, and a detached
        # Gateway has to outlive us.
        self._process: subprocess.Popen | None = None

    @property
    def running(self) -> bool:
        if self._process is not None:
            return self._process.poll() is None
        return self._earlier_pid() is not None

    def _earlier_pid(self) -> int | None:
        """The Gateway an earlier run left behind, if that process is still IBC's."""
        try:
            pid = int(self._pid_file.read_text().strip())
        except (OSError, ValueError):
            return None
        try:
            command = subprocess.run(["ps", "-o", "command=", "-p", str(pid)], capture_output=True, text=True,
                                     timeout=5).stdout
        except (OSError, subprocess.SubprocessError):
            return None
        return pid if "ibcstart" in command or "ibc" in command.lower() else None

    async def start(self, username: str, password: str) -> None:
        ibc = self._config.ibc_path
        script = ibc / "scripts" / "ibcstart.sh"
        if not script.exists():
            raise IbcError(f"IBC not found in {ibc}")
        version = gateway_version(self._config.tws_path)
        template = (ibc / "config.ini").read_text()
        self.delete_ini()
        self._ini.parent.mkdir(parents=True, exist_ok=True)
        fd = os.open(self._ini, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w") as out:
            out.write(login_ini(template, username, password, self._environment, self._config.read_only_api))
        logs = ibc / "logs"
        logs.mkdir(mode=0o700, exist_ok=True)
        with open(logs / f"gateway-{self._environment.value}.log", "ab") as log:
            try:
                self._process = subprocess.Popen(
                    [str(script), version, "-g", f"--tws-path={self._config.tws_path}", f"--ibc-path={ibc}",
                     f"--ibc-ini={self._ini}", f"--mode={TRADING_MODE[self._environment]}", "--on2fatimeout=exit"],
                    stdin=subprocess.DEVNULL, stdout=log, stderr=log, start_new_session=True)
            except OSError:
                self.delete_ini()
                raise
        fd = os.open(self._pid_file, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w") as out:
            out.write(str(self._process.pid))

    def delete_ini(self) -> None:
        with contextlib.suppress(FileNotFoundError):
            self._ini.unlink()

    def detach(self) -> None:
        """Leave the Gateway running (its session survives an app restart); the next run finds it by its pid."""
        self.delete_ini()
        self._process = None

    async def stop(self, grace: float = 10.0) -> None:
        """Stop IBC and the Gateway it started (its whole process group) — also one an earlier run left."""
        self.delete_ini()
        process, self._process = self._process, None
        pid = process.pid if process is not None and process.poll() is None else self._earlier_pid()
        with contextlib.suppress(FileNotFoundError):
            self._pid_file.unlink()
        if pid is None:
            return
        with contextlib.suppress(ProcessLookupError, PermissionError):
            os.killpg(pid, signal.SIGTERM)
        deadline = asyncio.get_running_loop().time() + grace
        while _alive(pid, process) and asyncio.get_running_loop().time() < deadline:
            await asyncio.sleep(0.1)
        if _alive(pid, process):
            with contextlib.suppress(ProcessLookupError, PermissionError):
                os.killpg(pid, signal.SIGKILL)
        if process is not None:
            with contextlib.suppress(subprocess.TimeoutExpired):
                process.wait(timeout=2)


def _alive(pid: int, process: subprocess.Popen | None) -> bool:
    if process is not None:
        return process.poll() is None
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True
