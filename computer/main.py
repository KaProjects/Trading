import asyncio
import json
from datetime import datetime

import firebase_admin
from firebase_admin import credentials
from firebase_admin import db

from classes import Alert, Company, Opportunity, Log

alert_data_path = "alert/data/"
company_path = "company/"
opportunity_path = "opportunity/"
asset_path = "asset/"
log_path = "log/"

debug = True

"""
expects files:
    cert.json
    envs.json
"""
def init_firebase():
    cred = credentials.Certificate('cert.json')
    with open("envs.json") as envs_file:
        envs = json.load(envs_file)
    firebase_admin.initialize_app(cred, envs)

async def alert_consumer():
    while True:
        alerts_data: dict = db.reference(alert_data_path).get()
        if alerts_data is None:
            if debug: print("[{}] no alerts found, sleeping for 60s...".format(current_time()))
            await asyncio.sleep(60)
            continue

        if debug: print("[{}] alerts found: {}".format(current_time(), len(alerts_data)))

        for alert_id in alerts_data:
            alert = Alert(alerts_data[alert_id])
            # company_data: dict = db.reference(company_path + "/" + alert.ticker).get()
            # company = None if company_data is None else Company(company_data)
            opportunity_data: dict = db.reference(opportunity_path + "/" + alert.ticker).get()
            opportunity = None if opportunity_data is None else Opportunity(opportunity_data)

            resolve_alert(alert, opportunity)

            db.reference(company_path + "/" + alert.ticker).set(alert.__repr__())
            db.reference(alert_data_path + "/" + alert_id).delete()
            if debug: print("[{}] company {} updated, {} alert removed".format(current_time(), alert.ticker, alert_id))

        await asyncio.sleep(1)

def resolve_alert(alert: Alert, opportunity: Opportunity):
    if opportunity is None:
        if alert.cci < -1.5:
            opportunity = Opportunity({"ticker": alert.ticker, "min_price": alert.price, "min_cci": alert.cci, "min_diff": alert.diff, "min_macd": alert.macd})
            db.reference(opportunity_path + "/" + alert.ticker).set(opportunity.__repr__())
            if debug: print("[{}] opportunity {} created".format(current_time(), opportunity.ticker))
            log = Log("buy opportunity", alert, opportunity)
            obj = db.reference(log_path).push(log.__repr__())
            if debug: print("[{}] log {} for {} created".format(current_time(), obj.key, log.ticker))
    else:
        if alert.cci < -0.25:
            updated = opportunity.update(alert)
            if updated:
                db.reference(opportunity_path + "/" + opportunity.ticker).set(opportunity.__repr__())
                if debug: print("[{}] opportunity {} updated".format(current_time(), opportunity.ticker))
            # TODO:  if (cci crossing up -1f) { create log
            # TODO:  if (diff crossing up 0f) { create log
            # TODO: macd crossing up something ....

        else:
            db.reference(opportunity_path + "/" + opportunity.ticker).delete()
            if debug: print("[{}] opportunity {} deleted".format(current_time(), opportunity.ticker))


def current_time():
    return datetime.now().strftime("%y-%m-%d %H:%M:%S")

def init():
    init_firebase()
    asyncio.run(alert_consumer())

if __name__ == '__main__':
    init()
