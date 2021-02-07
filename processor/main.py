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
            company_data: dict = db.reference(company_path + "/" + alert.ticker).get()
            company = None if company_data is None else Company(company_data)
            opportunity_data: dict = db.reference(opportunity_path + "/" + alert.ticker).get()
            opportunity = None if opportunity_data is None else Opportunity(opportunity_data)

            if company is None: company = Company(alerts_data[alert_id])

            resolve_alert(alert, company, opportunity)

            db.reference(company_path + "/" + alert.ticker).set(alert.__repr__())
            db.reference(alert_data_path + "/" + alert_id).delete()
            #if debug: print("[{}] company {} updated, {} alert removed".format(current_time(), alert.ticker, alert_id))

        await asyncio.sleep(1)

def resolve_alert(alert: Alert, company: Company, opportunity: Opportunity):
    if opportunity is None:
        if alert.cci < -1.5:
            opportunity = Opportunity({"ticker": alert.ticker, "min_price": alert.price, "min_cci": alert.cci, "min_diff": alert.diff, "min_macd": alert.macd})
            db.reference(opportunity_path + "/" + alert.ticker).set(opportunity.__repr__())
            if debug: print("[{}] opportunity {} created".format(current_time(), opportunity.ticker))
            create_opportunity_log("buy|create(cci<-1.5)", alert, opportunity)
    else:
        if alert.cci < -0.25:
            updated = opportunity.update(alert)
            if updated:
                db.reference(opportunity_path + "/" + opportunity.ticker).set(opportunity.__repr__())
                if debug: print("[{}] opportunity {} updated".format(current_time(), opportunity.ticker))
            if company.cci < -1 <= alert.cci:
                create_opportunity_log("buy|signal(cci>-1)", alert, opportunity)
            if company.diff < 0 <= alert.diff:
                create_opportunity_log("buy|signal(diff>0)", alert, opportunity)
            if company.macd < 0 <= alert.macd:
                create_opportunity_log("buy|signal(macd>0)", alert, opportunity)
        else:
            db.reference(opportunity_path + "/" + opportunity.ticker).delete()
            if debug: print("[{}] opportunity {} deleted".format(current_time(), opportunity.ticker))

def create_opportunity_log(type: str, alert: Alert, opportunity: Opportunity):
    log = Log(type, alert, opportunity)
    obj = db.reference(log_path).push(log.__repr__())
    if debug: print("[{}] log {} type '{}' for {} created".format(current_time(), obj.key, type, log.ticker))

def current_time():
    return datetime.now().strftime("%y-%m-%d %H:%M:%S")

def init():
    init_firebase()
    asyncio.run(alert_consumer())

if __name__ == '__main__':
    init()
