from firebase_admin import db

import utils

data_path = "log/"


def analyze():
    logs: dict = db.reference(data_path).get()
    logs_by_tag = sort_by_tag(logs.values())
    for log in logs_by_tag["NVDA"]:
        print("{} | {}".format(log["time"], log["type"]))


def sort_by_tag(logs):
    logs_by_tag = dict()
    for log in logs:
        if log["ticker"] not in logs_by_tag.keys():
            logs_by_tag[log["ticker"]] = list()
        logs_by_tag[log["ticker"]].append(log)
    return logs_by_tag


def prune_db():
    counter = 1
    while True:
        print("Deleting {}. mile".format(counter))
        logs = db.reference(data_path).order_by_key().limit_to_last(1000).get()
        for key in logs:
            db.reference(data_path + "/" + key).delete()
        if len(logs) < 1000: break;
        counter = counter + 1


if __name__ == '__main__':
    envs = utils.parse("envs.json")
    utils.init_firebase(envs["firebase"])
    # db.reference(data_path).delete()
    # prune_db()
    analyze()


