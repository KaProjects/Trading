from decimal import Decimal
from enum import StrEnum

from pydantic import AliasPath, BaseModel, ConfigDict, Field


class FearAndGreedClassification(StrEnum):
    EXTREME_FEAR = "Extreme fear"
    FEAR = "Fear"
    NEUTRAL = "Neutral"
    GREED = "Greed"
    EXTREME_GREED = "Extreme greed"


class FearAndGreedReading(BaseModel):
    model_config = ConfigDict(extra="ignore")

    value: int = Field(
        ge=0,
        le=100,
        validation_alias=AliasPath("data", "value"),
    )
    classification: FearAndGreedClassification = Field(
        validation_alias=AliasPath("data", "value_classification"),
    )


class BitcoinQuote(BaseModel):
    model_config = ConfigDict(extra="ignore")

    price: Decimal = Field(
        ge=0,
        validation_alias=AliasPath(
            "data",
            "BTC",
            0,
            "quote",
            "USD",
            "price",
        ),
    )
