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


@patch("myfinnhub.client.finnhub.Client")
def test_get_beta_reads_it_from_the_basic_financials_metrics(client_type):
    api = client_type.return_value
    api.company_basic_financials.return_value = {
        "metric": {"beta": 3.268587, "52WeekHigh": 308.67},
        "series": {},
        "symbol": "CRDO",
    }

    assert FinnhubClient("api-key").get_beta("CRDO") == Decimal("3.268587")
    api.company_basic_financials.assert_called_once_with("CRDO", "all")


@patch("myfinnhub.client.finnhub.Client")
def test_get_beta_keeps_zero(client_type):
    api = client_type.return_value
    api.company_basic_financials.return_value = {"metric": {"beta": 0}}

    assert FinnhubClient("api-key").get_beta("X") == Decimal("0")


@patch("myfinnhub.client.finnhub.Client")
def test_get_beta_is_none_when_finnhub_has_none(client_type):
    api = client_type.return_value
    for response in (
        {"metric": {"beta": None}},
        {"metric": {"beta": ""}},
        {"metric": {}},
        {},
        None,
    ):
        api.company_basic_financials.return_value = response
        assert FinnhubClient("api-key").get_beta("NEW") is None


@patch("myfinnhub.client.finnhub.Client")
def test_get_beta_lets_api_errors_propagate(client_type):
    api = client_type.return_value
    api.company_basic_financials.side_effect = RuntimeError("429")

    try:
        FinnhubClient("api-key").get_beta("CRDO")
    except RuntimeError as exception:
        assert str(exception) == "429"
    else:
        raise AssertionError("expected the API error to propagate")


@patch("myfinnhub.client.finnhub.Client")
def test_get_shares_outstanding_reads_the_company_profile(client_type):
    api = client_type.return_value
    api.company_profile2.return_value = {
        "ticker": "000660.KS",
        "shareOutstanding": 730.49,
    }

    shares = FinnhubClient("api-key").get_shares_outstanding("SKHY")

    assert shares == Decimal("730.49")
    api.company_profile2.assert_called_once_with(symbol="SKHY")


@patch("myfinnhub.client.finnhub.Client")
def test_get_shares_outstanding_is_none_without_a_count(client_type):
    api = client_type.return_value
    for response in ({}, {"shareOutstanding": None}, {"shareOutstanding": 0}, None):
        api.company_profile2.return_value = response
        assert FinnhubClient("api-key").get_shares_outstanding("X") is None
