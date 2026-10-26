from pydantic import BaseModel, ConfigDict, Field, PositiveFloat, SecretStr, field_validator
from pytz import UnknownTimeZoneError, timezone


class AppConfig(BaseModel):
    model_config = ConfigDict(extra="forbid")

    firebase: str = Field(min_length=1)
    cmc_api_key: SecretStr
    discord_bot_token: SecretStr
    discord_guild_id: str = Field(min_length=1)
    discord_btc_webhook_key: SecretStr
    discord_eventlog_webhook_key: SecretStr
    discord_earnings_webhook_key: SecretStr
    discord_errorlog_webhook_key: SecretStr
    finnhub_api_key: SecretStr
    gemini_api_key: SecretStr
    polygon_api_key: SecretStr
    # Contact address SEC requires in the User-Agent of every request.
    sec_contact_email: SecretStr
    timezone: str = "Europe/Prague"
    poll_interval_seconds: PositiveFloat = 60

    @field_validator("sec_contact_email")
    @classmethod
    def contact_email_must_look_like_an_email(cls, value: SecretStr) -> SecretStr:
        if "@" not in value.get_secret_value():
            raise ValueError("sec_contact_email must be an email address")
        return value

    @field_validator("timezone")
    @classmethod
    def timezone_must_exist(cls, value: str) -> str:
        try:
            timezone(value)
        except UnknownTimeZoneError as exception:
            raise ValueError(f"Unknown timezone: {value}") from exception
        return value
