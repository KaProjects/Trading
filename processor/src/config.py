from pydantic import BaseModel, ConfigDict, Field, PositiveFloat, SecretStr, field_validator
from pytz import UnknownTimeZoneError, timezone


class AppConfig(BaseModel):
    model_config = ConfigDict(extra="forbid")

    firebase: str = Field(min_length=1)
    cmc_api_key: SecretStr
    discord_btc_webhook_key: SecretStr
    discord_eventlog_webhook_key: SecretStr
    discord_earnings_webhook_key: SecretStr
    discord_errorlog_webhook_key: SecretStr
    finnhub_api_key: SecretStr
    gemini_api_key: SecretStr
    polygon_api_key: SecretStr | None = None
    timezone: str = "Europe/Prague"
    poll_interval_seconds: PositiveFloat = 60

    @field_validator("timezone")
    @classmethod
    def timezone_must_exist(cls, value: str) -> str:
        try:
            timezone(value)
        except UnknownTimeZoneError as exception:
            raise ValueError(f"Unknown timezone: {value}") from exception
        return value
