from collections.abc import Mapping
from typing import TypeVar

from pydantic import BaseModel
from requests import Session
from requests.exceptions import RequestException

from cmc.models import BitcoinQuote, FearAndGreedReading

ResponseModel = TypeVar("ResponseModel", bound=BaseModel)


class CoinMarketCapError(RuntimeError):
    pass


class CoinMarketCapClient:
    BASE_URL = "https://pro-api.coinmarketcap.com"

    def __init__(
        self,
        api_key: str,
        session: Session | None = None,
        timeout: float = 10.0,
    ) -> None:
        self.session = session or Session()
        self.session.headers.update({
            "Accepts": "application/json",
            "X-CMC_PRO_API_KEY": api_key,
        })
        self.timeout = timeout

    def get_fear_and_greed(self) -> FearAndGreedReading:
        return self._get(
            "/v3/fear-and-greed/latest",
            parameters={},
            response_model=FearAndGreedReading,
        )

    def get_btc_price(self) -> BitcoinQuote:
        return self._get(
            "/v2/cryptocurrency/quotes/latest",
            parameters={"symbol": "BTC"},
            response_model=BitcoinQuote,
        )

    def _get(
        self,
        path: str,
        parameters: Mapping[str, str],
        response_model: type[ResponseModel],
    ) -> ResponseModel:
        try:
            response = self.session.get(
                self.BASE_URL + path,
                params=parameters,
                timeout=self.timeout,
            )
        except RequestException as exception:
            raise CoinMarketCapError(
                "CoinMarketCap request failed"
            ) from exception

        if response.status_code != 200:
            raise CoinMarketCapError(
                f"CoinMarketCap returned {response.status_code}: {response.text}"
            )

        try:
            payload: object = response.json()
            return response_model.model_validate(payload)
        except (TypeError, ValueError) as exception:
            raise CoinMarketCapError(
                "CoinMarketCap returned an invalid response"
            ) from exception
