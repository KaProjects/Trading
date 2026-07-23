import json
import logging
import os
import tempfile
from pathlib import Path

import firebase_admin
from firebase_admin import credentials, db

import utils
from config import AppConfig

logger = logging.getLogger(__name__)

DEFAULT_SNAPSHOT_PATH = Path(__file__).with_name("firebase_companies.json")
DEFAULT_CERTIFICATE_PATH = Path("cert.json")
FIREBASE_APP_NAME = "dev-firebase-snapshot"


def ensure_firebase_snapshot(
    *,
    config_path: str | Path,
    certificate_path: str | Path = DEFAULT_CERTIFICATE_PATH,
    snapshot_path: str | Path = DEFAULT_SNAPSHOT_PATH,
) -> dict[str, object]:
    destination = Path(snapshot_path)
    if destination.is_file():
        logger.info("Using cached Firebase dev data from %s", destination)
        return load_firebase_snapshot(destination)

    config = AppConfig.model_validate(utils.parse(str(config_path)))
    snapshot = download_firebase_snapshot(
        database_url=config.firebase,
        certificate_path=certificate_path,
    )
    write_firebase_snapshot(snapshot, destination)
    logger.info(
        "Downloaded %s Firebase companies to %s",
        len(snapshot),
        destination,
    )
    return snapshot


def download_firebase_snapshot(
    *,
    database_url: str,
    certificate_path: str | Path,
) -> dict[str, object]:
    credential = credentials.Certificate(str(certificate_path))
    app = firebase_admin.initialize_app(
        credential,
        {"databaseURL": database_url},
        name=FIREBASE_APP_NAME,
    )
    try:
        snapshot = db.reference("company", app=app).get()
    finally:
        firebase_admin.delete_app(app)

    if snapshot is None:
        return {}
    if not isinstance(snapshot, dict):
        raise TypeError("Firebase /company snapshot must be a mapping")
    return snapshot


def load_firebase_snapshot(
    snapshot_path: str | Path = DEFAULT_SNAPSHOT_PATH,
) -> dict[str, object]:
    with Path(snapshot_path).open(encoding="utf-8") as content:
        snapshot = json.load(content)
    if not isinstance(snapshot, dict):
        raise TypeError("Cached Firebase /company snapshot must be a mapping")
    return snapshot


def write_firebase_snapshot(
    snapshot: dict[str, object],
    snapshot_path: str | Path = DEFAULT_SNAPSHOT_PATH,
) -> None:
    destination = Path(snapshot_path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    file_descriptor, temporary_name = tempfile.mkstemp(
        dir=destination.parent,
        prefix=".firebase_companies.",
        suffix=".tmp",
        text=True,
    )
    temporary_path = Path(temporary_name)
    try:
        with os.fdopen(
            file_descriptor,
            "w",
            encoding="utf-8",
        ) as content:
            json.dump(
                snapshot,
                content,
                indent=2,
                sort_keys=True,
                ensure_ascii=False,
            )
            content.write("\n")
        temporary_path.chmod(0o600)
        temporary_path.replace(destination)
    finally:
        temporary_path.unlink(missing_ok=True)
