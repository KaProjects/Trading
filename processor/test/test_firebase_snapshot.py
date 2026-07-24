import json
import stat
from unittest.mock import Mock, patch

from dev.firebase_snapshot import (
    download_firebase_snapshot,
    ensure_firebase_snapshot,
    load_firebase_snapshot,
)


def make_config():
    return {
        "firebase": "https://example.firebaseio.com",
        "cmc_api_key": "cmc",
        "discord_bot_token": "discord-token",
        "discord_guild_id": "guild-id",
        "discord_errorlog_channel_id": "error-channel-id",
        "finnhub_api_key": "finnhub",
        "gemini_api_key": "gemini",
    }


def test_existing_snapshot_is_reused_without_credentials(tmp_path):
    snapshot = {"AAPL": {"enabled": True}}
    snapshot_path = tmp_path / "firebase_companies.json"
    snapshot_path.write_text(json.dumps(snapshot), encoding="utf-8")

    with (
        patch(
            "dev.firebase_snapshot.download_firebase_snapshot",
            autospec=True,
        ) as download,
        patch("dev.firebase_snapshot.utils.parse", autospec=True) as parse,
    ):
        result = ensure_firebase_snapshot(
            config_path="missing-envs.json",
            certificate_path="missing-cert.json",
            snapshot_path=snapshot_path,
        )

    assert result == snapshot
    download.assert_not_called()
    parse.assert_not_called()


def test_missing_snapshot_is_downloaded_once_and_written_privately(tmp_path):
    snapshot = {"AAPL": {"gemini": {"info": {}}}}
    snapshot_path = tmp_path / "firebase_companies.json"

    with (
        patch(
            "dev.firebase_snapshot.utils.parse",
            autospec=True,
            return_value=make_config(),
        ) as parse,
        patch(
            "dev.firebase_snapshot.download_firebase_snapshot",
            autospec=True,
            return_value=snapshot,
        ) as download,
    ):
        result = ensure_firebase_snapshot(
            config_path="envs.json",
            certificate_path="cert.json",
            snapshot_path=snapshot_path,
        )

    assert result == snapshot
    assert load_firebase_snapshot(snapshot_path) == snapshot
    assert stat.S_IMODE(snapshot_path.stat().st_mode) == 0o600
    parse.assert_called_once_with("envs.json")
    download.assert_called_once_with(
        database_url="https://example.firebaseio.com",
        certificate_path="cert.json",
    )


def test_download_reads_only_company_root_and_closes_named_app():
    snapshot = {"AAPL": {"enabled": True}}
    credential = Mock()
    app = Mock()
    reference = Mock()
    reference.get.return_value = snapshot

    with (
        patch(
            "dev.firebase_snapshot.credentials.Certificate",
            autospec=True,
            return_value=credential,
        ) as certificate,
        patch(
            "dev.firebase_snapshot.firebase_admin.initialize_app",
            autospec=True,
            return_value=app,
        ) as initialize,
        patch(
            "dev.firebase_snapshot.firebase_admin.delete_app",
            autospec=True,
        ) as delete,
        patch(
            "dev.firebase_snapshot.db.reference",
            autospec=True,
            return_value=reference,
        ) as database_reference,
    ):
        result = download_firebase_snapshot(
            database_url="https://example.firebaseio.com",
            certificate_path="cert.json",
        )

    assert result == snapshot
    certificate.assert_called_once_with("cert.json")
    initialize.assert_called_once_with(
        credential,
        {"databaseURL": "https://example.firebaseio.com"},
        name="dev-firebase-snapshot",
    )
    database_reference.assert_called_once_with("company", app=app)
    reference.get.assert_called_once_with()
    delete.assert_called_once_with(app)
