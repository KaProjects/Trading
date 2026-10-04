import logging
from datetime import date
from decimal import Decimal
from unittest.mock import MagicMock, call, create_autospec, patch

import pytest
from pydantic import ValidationError

from error_reporting import ErrorReporter
from indicators.models import Indicators, PriceIndicator
from indicators.service import FirebaseService, company_path, price_path

TODAY = date(2026, 10, 6)


def make_service():
    service = FirebaseService()
    service.log = create_autospec(logging.Logger, instance=True)
    service.errors = create_autospec(ErrorReporter, instance=True)
    return service


def test_company_and_price_paths_convert_ticker_to_firebase_key():
    assert company_path("BRK.B") == "company/BRK-B/indicators"
    assert price_path("BRK.B") == "company/BRK-B/indicators/price"


def test_upsert_price_merges_into_a_fixed_price_key():
    database_reference = MagicMock(spec_set=["update"])

    with patch(
        "indicators.service.db.reference",
        autospec=True,
        return_value=database_reference,
    ) as reference:
        make_service().upsert_price(
            "BRK.B",
            PriceIndicator(value=Decimal("502.65"), retrieved_on=TODAY),
        )

    # One write only, with no day in the key: there is a single latest
    # price, and no mark_company_enabled since this must never be a way
    # to create a company node.
    reference.assert_called_once_with("company/BRK-B/indicators/price")
    database_reference.update.assert_called_once_with({
        "value": "502.65",
        "retrieved_on": "2026-10-06",
    })


def test_get_prices_reads_each_requested_company_only():
    references = {
        "company/MU/indicators/price": MagicMock(
            get=MagicMock(return_value={"value": "1074.89", "retrieved_on": "2026-10-06"})
        ),
        "company/AMD/indicators/price": MagicMock(
            get=MagicMock(return_value=None)
        ),
    }

    with patch(
        "indicators.service.db.reference",
        autospec=True,
        side_effect=references.__getitem__,
    ) as reference:
        prices = make_service().get_prices(["MU", "AMD"])

    assert reference.call_args_list == [
        call("company/MU/indicators/price"),
        call("company/AMD/indicators/price"),
    ]
    assert prices == {
        "MU": PriceIndicator(value=Decimal("1074.89"), retrieved_on=TODAY),
    }


def test_get_prices_reports_and_skips_an_invalid_price_node():
    service = make_service()
    references = {
        "company/MU/indicators/price": MagicMock(
            get=MagicMock(return_value={"value": "not-a-number"})
        ),
        "company/AMD/indicators/price": MagicMock(
            get=MagicMock(return_value={"value": "633.91", "retrieved_on": "2026-10-06"})
        ),
    }

    with patch(
        "indicators.service.db.reference",
        autospec=True,
        side_effect=references.__getitem__,
    ):
        prices = service.get_prices(["MU", "AMD"])

    assert list(prices) == ["AMD"]
    service.errors.report.assert_called_once()
    assert (
        service.errors.report.call_args.kwargs["operation"] == "parse_price"
    )


def test_indicators_ignore_attributes_added_by_newer_code():
    indicators = Indicators.model_validate({
        "price": {"value": "1074.89", "retrieved_on": "2026-10-06", "currency": "USD"},
        "volume": {"value": "5"},
    })

    assert indicators.price == PriceIndicator(
        value=Decimal("1074.89"),
        retrieved_on=TODAY,
    )


@pytest.mark.parametrize("value", ["0", "-1"])
def test_price_indicator_rejects_a_non_positive_value(value):
    with pytest.raises(ValidationError):
        PriceIndicator(value=Decimal(value), retrieved_on=TODAY)


def test_get_prices_isolates_a_failing_read_to_its_company():
    service = make_service()
    failure = RuntimeError("firebase unavailable")
    broken = MagicMock(get=MagicMock(side_effect=failure))
    working = MagicMock(
        get=MagicMock(
            return_value={"value": "633.91", "retrieved_on": "2026-10-06"}
        )
    )
    references = {
        "company/MU/indicators/price": broken,
        "company/AMD/indicators/price": working,
    }

    with patch(
        "indicators.service.db.reference",
        autospec=True,
        side_effect=references.__getitem__,
    ):
        prices = service.get_prices(["MU", "AMD"])

    assert list(prices) == ["AMD"]
    service.errors.report.assert_called_once_with(
        failure,
        logger=service.log,
        source="IndicatorsService",
        operation="read_price",
        context={"company_id": "MU"},
    )
