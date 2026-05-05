import functools
import json
import logging
import sys
import traceback
from datetime import datetime, timedelta

import firebase_admin
from firebase_admin import credentials


def init_firebase(db_url: str):
    cred = credentials.Certificate('cert.json')
    firebase_admin.initialize_app(cred, {"databaseURL": db_url})


def log(process: str, message: str):
    now = datetime.now().strftime("%y-%m-%d %H:%M:%S")
    log = "[{}][{}] {}".format(now, process, message)
    print(log)
    with open("log.log", "a") as log_file:
        log_file.write(log + "\n")


def parse(file: str):
    with open(file) as content:
        return json.load(content)


def is_past_date(date: str, offset=0) -> bool:
    try:
        input_date = datetime.strptime(date, "%Y-%m-%d").date()
        today = datetime.now().date()
        threshold = today - timedelta(days=offset)
        return input_date < threshold
    except ValueError:
        return False


def telemetry(func):
    @functools.wraps(func)
    def wrapper(self, *args, **kwargs):
        if type(self).__name__ == func.__qualname__.split('.')[0]:
            self.log.debug("[{}.{}]<={}{}".format(type(self).__name__, func.__name__, str(args), str(kwargs)))
            result = func(self, *args, **kwargs)
            self.log.debug("[{}.{}]=>{}".format(type(self).__name__, func.__name__, str(result)))
            return result
        else:
            return func(self, *args, **kwargs)

    return wrapper


class WithTelemetry(type):
    def __new__(cls, name, bases, local):
        for attr_name, attr_value in local.items():
            if callable(attr_value) and not attr_name.startswith("_"):
                local[attr_name] = telemetry(attr_value)
        return super().__new__(cls, name, bases, local)


class BaseClass(metaclass=WithTelemetry):
    def __init__(self, identity: str = __name__, logger_level: int = logging.INFO):
        handler = logging.StreamHandler(sys.stdout)
        handler.setFormatter(logging.Formatter("[%(asctime)s][%(levelname)s][%(name)s] %(message)s", datefmt="%Y-%m-%d %H:%M:%S"))
        self.log = logging.getLogger(identity)
        self.log.setLevel(logger_level)
        self.log.addHandler(handler)
        self.log.setLevel(logger_level)
        self.log.propagate = False

