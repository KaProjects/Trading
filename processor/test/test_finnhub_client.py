from decimal import Decimal
from unittest.mock import patch

from myfinnhub.client import FinnhubClient


def earnings_response(
    *,
    eps_estimate=7594.5732,
    eps_actual=7889.445,
):
    return {
        "earningsCalendar": [{
            "date": "2026-08-03",
            "epsActual": eps_actual,
            "epsEstimate": eps_estimate,
            "hour": "",
            "quarter": 2,
            "revenueActual": 101_000_000_000,
            "revenueEstimate": 102_000_000_000,
            "symbol": "BRK.A",
            "year": 2026,
        }],
    }


@patch("myfinnhub.client.finnhub.Client")
def test_brk_b_earnings_eps_is_converted_to_class_b_scale(client_type):
    api = client_type.return_value
    api.earnings_calendar.return_value = earnings_response()

    earnings = FinnhubClient("api-key").get_earnings("BRK.B")["26Q2"]

    assert earnings.epse == Decimal("5.0630")
    assert earnings.epsa == Decimal("5.2596")
    assert earnings.reve == Decimal("102000000000")
    assert earnings.reva == Decimal("101000000000")
    assert api.earnings_calendar.call_args.kwargs["symbol"] == "BRK.B"


@patch("myfinnhub.client.finnhub.Client")
def test_other_ticker_eps_is_not_converted(client_type):
    api = client_type.return_value
    api.earnings_calendar.return_value = earnings_response(
        eps_estimate=1500,
        eps_actual=3000,
    )

    earnings = FinnhubClient("api-key").get_earnings("BRK.A")["26Q2"]

    assert earnings.epse == Decimal("1500")
    assert earnings.epsa == Decimal("3000")


@patch("myfinnhub.client.finnhub.Client")
def test_brk_b_eps_conversion_preserves_null_and_zero(client_type):
    api = client_type.return_value
    api.earnings_calendar.return_value = earnings_response(
        eps_estimate=None,
        eps_actual=0,
    )

    earnings = FinnhubClient("api-key").get_earnings("BRK.B")["26Q2"]

    assert earnings.epse is None
    assert earnings.epsa == Decimal("0")
