"""US regular trading session: Mon–Fri 09:30–16:00 America/New_York. Exchange holidays are not handled yet
(a holiday counts as open). Used to decide whether an old price is "stale" or simply "market closed"."""

from datetime import datetime, time
from zoneinfo import ZoneInfo

NEW_YORK = ZoneInfo("America/New_York")
OPEN, CLOSE = time(9, 30), time(16, 0)


def us_regular_session(now: datetime) -> bool:
    local = now.astimezone(NEW_YORK)
    return local.weekday() < 5 and OPEN <= local.time() < CLOSE
