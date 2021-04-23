
from firebase_admin import db


def analyze():
    logs:dict = db.reference("log/").get()
    logs_by_tag = sort_by_tag(logs.values())
    for log in logs_by_tag["AAL"]:
        print("{} | {}".format(log["time"],log["type"]))

def sort_by_tag(logs):
    logs_by_tag = dict()
    for log in logs:
        if log["ticker"] not in logs_by_tag.keys():
            logs_by_tag[log["ticker"]] = list()
        logs_by_tag[log["ticker"]].append(log)
    return logs_by_tag
