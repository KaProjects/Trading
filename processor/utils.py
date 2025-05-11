import json
from datetime import datetime

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