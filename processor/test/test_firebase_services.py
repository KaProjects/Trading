import logging
from unittest.mock import create_autospec, patch

import pytest

from gemini.service import FirebaseService as GeminiFirebaseService
from myfinnhub.service import FirebaseService as FinnhubFirebaseService


class FakeReference:
    def __init__(self, value):
        self.value = value

    def get(self):
        return self.value


def make_service(service_class):
    service = object.__new__(service_class)
    service.log = create_autospec(logging.Logger, instance=True)
    return service


@pytest.mark.xfail(strict=True, reason="Gemini service iterates over a None Firebase root")
def test_gemini_service_treats_empty_snapshot_as_empty_mapping():
    with patch("gemini.service.db.reference", autospec=True, return_value=FakeReference(None)):
        assert make_service(GeminiFirebaseService).get_companies() == {}


@pytest.mark.xfail(strict=True, reason="Finnhub service iterates over a None Firebase root")
def test_finnhub_service_treats_empty_snapshot_as_empty_mapping():
    with patch("myfinnhub.service.db.reference", autospec=True, return_value=FakeReference(None)):
        assert make_service(FinnhubFirebaseService).get_companies() == {}


@pytest.mark.parametrize(
    ("service_class", "reference_path", "data_root"),
    [
        (GeminiFirebaseService, "gemini.service.db.reference", "gemini"),
        (FinnhubFirebaseService, "myfinnhub.service.db.reference", "fhe"),
    ],
)
def test_firebase_service_maps_malformed_company_records_to_none(
    service_class,
    reference_path,
    data_root,
):
    snapshot = {
        "AAPL": None,
        "MSFT": "invalid",
        "NVDA": {data_root: None},
    }
    with patch(reference_path, autospec=True, return_value=FakeReference(snapshot)):
        companies = make_service(service_class).get_companies()

    assert companies == {"AAPL": None, "MSFT": None, "NVDA": None}
