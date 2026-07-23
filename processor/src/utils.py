import json
import logging
import sys
from datetime import date as Date
from datetime import datetime, timedelta

import firebase_admin
from firebase_admin import credentials


def init_firebase(db_url: str):
    cred = credentials.Certificate('cert.json')
    firebase_admin.initialize_app(cred, {"databaseURL": db_url})


def configure_logging(level: int = logging.INFO) -> None:
    logging.basicConfig(
        level=level,
        format="[%(asctime)s][%(levelname)s][%(name)s] %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
        stream=sys.stdout,
    )


def parse(file: str):
    with open(file) as content:
        return json.load(content)


def is_past_date(date: Date | str | None, offset: int = 0) -> bool:
    if date is None:
        return False
    try:
        input_date = (
            date
            if isinstance(date, Date)
            else datetime.strptime(date, "%Y-%m-%d").date()
        )
        today = datetime.now().date()
        threshold = today - timedelta(days=offset)
        return input_date < threshold
    except (TypeError, ValueError):
        return False
