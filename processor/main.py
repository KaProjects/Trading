import asyncio
import json
from datetime import datetime

import firebase_admin
from firebase_admin import credentials
from firebase_admin import db

import analyzer
from classes import Alert, Company, Opportunity, Log

alert_data_path = "alert/data/"
company_path = "company/"
opportunity_path = "opportunity/"
asset_path = "asset/"
log_path = "log/"

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

def log(message):
    time = datetime.now().strftime("%y-%m-%d %H:%M:%S")
    print("[{}] {}".format(time, message))

async def alert_consumer():
    while True:
        alerts_data: dict = db.reference(alert_data_path).get()
        if alerts_data is None:
            log("no alerts found, sleeping for 60s...")
            await asyncio.sleep(60)
            continue

        log("alerts found: {}".format(len(alerts_data)))

        if len(alerts_data) > 1000:
            fast_forward_alert_consumer(alerts_data)
            log("alerts consumption fast forwarded")
            await asyncio.sleep(1)
            continue

        for alert_id in alerts_data:
            alert = Alert(alerts_data[alert_id])
            opportunity_data: dict = db.reference(opportunity_path + "/" + alert.ticker).get()
            opportunity = None if opportunity_data is None else Opportunity(opportunity_data)

            logs, updated_opportunity = resolve_alert(alert, opportunity)

            if logs is not None:
                for event_log in logs:
                    obj = db.reference(log_path).push(event_log.__repr__())
                    log("log {} type '{}' for {} created".format(obj.key, type, event_log.ticker))

            if opportunity is not None and updated_opportunity is None:
                db.reference(opportunity_path + "/" + opportunity.ticker).delete()
                log("opportunity {} deleted".format(opportunity.ticker))
            elif opportunity is None and updated_opportunity is not None:
                db.reference(opportunity_path + "/" + updated_opportunity.ticker).set(updated_opportunity.__repr__())
                log("opportunity {} created".format(updated_opportunity.ticker))
            elif opportunity is not None and updated_opportunity is not None and not opportunity.__eq__(updated_opportunity):
                db.reference(opportunity_path + "/" + updated_opportunity.ticker).set(updated_opportunity.__repr__())
                log("opportunity {} updated".format(updated_opportunity.ticker))

            db.reference(company_path + "/" + alert.ticker).set(alert.__repr__())
            db.reference(alert_data_path + "/" + alert_id).delete()
            # log("company {} updated, {} alert removed".format(alert.ticker, alert_id))

        await asyncio.sleep(1)

def fast_forward_alert_consumer(alerts_data):
    companies: dict = db.reference(company_path).get()
    if companies is None: companies = dict()
    opportunities: dict = db.reference(opportunity_path).get()
    if opportunities is None: opportunities = dict()
    logs = list()
    for alert_id in alerts_data:
        alert = Alert(alerts_data[alert_id])
        opportunity = None
        if alert.ticker in opportunities:
            opportunity_data: dict = opportunities[alert.ticker]
            opportunity = Opportunity(opportunity_data)

        new_logs, updated_opportunity = resolve_alert(alert, opportunity)
        if new_logs is not None:
            for new_log in new_logs:
                logs.append(new_log)

        if opportunity is not None and updated_opportunity is None:
            del opportunities[alert.ticker]
        if updated_opportunity is not None:
            opportunities[alert.ticker] = updated_opportunity.__repr__()
        companies[alert.ticker] = alerts_data[alert_id]

    db.reference(company_path).set(companies)
    db.reference(opportunity_path).set(opportunities)
    for event_log in logs:
        db.reference(log_path).push(event_log.__repr__())
    log("{} new logs created".format(len(logs)))
    db.reference(alert_data_path).delete()


"""
returns:
    log list 
    updated opportunity
"""
def resolve_alert(alert: Alert, former_opportunity: Opportunity):
    if former_opportunity is None:
        if alert.cci < -1.5:
            opportunity = Opportunity({"ticker": alert.ticker, "min_price": alert.price, "min_cci": alert.cci,
                                       "min_diff": alert.diff, "min_macd": alert.macd, "signal": "000", })
            event_log = Log("buy|create(cci<-1.5)", alert, opportunity)
            return [event_log], opportunity


    else:
        if alert.cci < -0.25:
            opportunity = former_opportunity.get_updated_copy(alert)
            logs = list()

            if not opportunity.signal.cci and alert.cci >= -1:
                logs.append(Log("buy|signal(cci>-1)", alert, opportunity))
                opportunity.signal.cci = True
            if opportunity.signal.cci and alert.cci < -1.2:
                opportunity.signal.cci = False

            if not opportunity.signal.diff and alert.cci >= 0:
                logs.append(Log("buy|signal(diff>0)", alert, opportunity))
                opportunity.signal.diff = True
            if opportunity.signal.diff and alert.cci < -0.2:
                opportunity.signal.diff = False

            if not opportunity.signal.macd and alert.cci >= 0:
                logs.append(Log("buy|signal(macd>0)", alert, opportunity))
                opportunity.signal.macd = True
            if opportunity.signal.macd and alert.cci < -0.2:
                opportunity.signal.macd = False

            return logs, opportunity

    return None, None

def init():
    init_firebase()
    # analyzer.analyze()
    asyncio.run(alert_consumer())


if __name__ == '__main__':
    init()

