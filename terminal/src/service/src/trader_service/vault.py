"""Encrypted files (D3.2): AES-256-GCM with the data key the app sends after Touch ID (D3.3).

File format: b"TTE1" | 12-byte nonce | ciphertext+tag. The file name is the associated data, so a file
can't be swapped for another one. Writes are atomic (temp file + rename) with owner-only permissions.
"""

import json
import os
import secrets
from pathlib import Path
from typing import Any

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAGIC = b"TTE1"
NONCE_SIZE = 12


class VaultLocked(Exception):
    code = "locked"


class VaultCorrupt(Exception):
    """Wrong key or damaged file."""


class Vault:
    def __init__(self, folder: Path) -> None:
        self.folder = folder
        self._key: bytearray | None = None

    @property
    def unlocked(self) -> bool:
        return self._key is not None

    def unlock(self, key: bytes) -> None:
        if len(key) != 32:
            raise ValueError("data key must be 32 bytes")
        self.lock()
        self._key = bytearray(key)
        self.folder.mkdir(parents=True, exist_ok=True)
        os.chmod(self.folder, 0o700)

    def lock(self) -> None:
        """Drop the key. Best effort wipe: Python may still hold copies made by the crypto library."""
        if self._key is not None:
            for i in range(len(self._key)):
                self._key[i] = 0
        self._key = None

    def _cipher(self) -> AESGCM:
        if self._key is None:
            raise VaultLocked("vault is locked")
        return AESGCM(bytes(self._key))

    def write(self, name: str, value: Any) -> None:
        cipher = self._cipher()
        nonce = secrets.token_bytes(NONCE_SIZE)
        payload = json.dumps(value, separators=(",", ":")).encode()
        blob = MAGIC + nonce + cipher.encrypt(nonce, payload, name.encode())
        path = self.folder / name
        temp = path.with_name(f".{name}.tmp")
        fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "wb") as handle:
            handle.write(blob)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temp, path)

    def read(self, name: str) -> Any | None:
        cipher = self._cipher()
        path = self.folder / name
        if not path.exists():
            return None
        blob = path.read_bytes()
        if not blob.startswith(MAGIC) or len(blob) < len(MAGIC) + NONCE_SIZE + 16:
            raise VaultCorrupt(name)
        nonce = blob[len(MAGIC):len(MAGIC) + NONCE_SIZE]
        try:
            payload = cipher.decrypt(nonce, blob[len(MAGIC) + NONCE_SIZE:], name.encode())
        except InvalidTag:
            raise VaultCorrupt(name) from None
        return json.loads(payload)

    def names(self, prefix: str) -> list[str]:
        if not self.folder.exists():
            return []
        return sorted(p.name for p in self.folder.glob(f"{prefix}*.enc"))

    def delete(self, name: str) -> None:
        (self.folder / name).unlink(missing_ok=True)

    def quarantine(self, name: str) -> None:
        """Keep an unreadable file (e.g. encrypted with an older key) out of the way instead of deleting it."""
        path = self.folder / name
        if path.exists():
            path.rename(path.with_name(name + ".unreadable"))
