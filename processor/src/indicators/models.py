from datetime import date as Date
from decimal import Decimal

from pydantic import BaseModel, ConfigDict, Field


class PriceIndicator(BaseModel):
    # Unknown keys are ignored (not forbidden) so that rolling back a deploy
    # can still read nodes that newer code enriched with more attributes.
    model_config = ConfigDict(extra="ignore", allow_inf_nan=False)

    value: Decimal = Field(gt=0)
    # The day the price was retrieved - not the trading session the close
    # belongs to, hence not just "date". Consumers use it to tell whether
    # today's retrieval actually happened.
    retrieved_on: Date


class Indicators(BaseModel):
    model_config = ConfigDict(extra="ignore")

    price: PriceIndicator | None = None
