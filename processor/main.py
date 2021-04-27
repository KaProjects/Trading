import asyncio
import json
from datetime import datetime

import firebase_admin
from firebase_admin import credentials
from firebase_admin import db
from google.auth.exceptions import TransportError

import analyzer
from classes import Company, Opportunity, Log, Asset

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
        try:
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
                alert = Company(alerts_data[alert_id])
                opportunity_data: dict = db.reference(opportunity_path + "/" + alert.ticker).get()
                opportunity = None if opportunity_data is None else Opportunity(opportunity_data)
                asset_data: dict = db.reference(asset_path + "/" + alert.ticker).get()
                asset = None if asset_data is None else Asset(asset_data)
                asset_opportunity = None if asset is None else asset.opportunity

                logs, updated_opportunity, updated_asset_opportunity = resolve_alert(alert, opportunity, asset_opportunity)

                if logs is not None:
                    for event_log in logs:
                        obj = db.reference(log_path).push(event_log.__repr__())
                        log("log {} type '{}' for {} created".format(obj.key, event_log.type, event_log.ticker))

                if opportunity is not None and updated_opportunity is None:
                    db.reference(opportunity_path + "/" + opportunity.ticker).delete()
                    log("{} opportunity deleted".format(opportunity.ticker))
                elif opportunity is None and updated_opportunity is not None:
                    db.reference(opportunity_path + "/" + updated_opportunity.ticker).set(updated_opportunity.__repr__())
                    log("{} opportunity created".format(updated_opportunity.ticker))
                elif opportunity is not None and updated_opportunity is not None and opportunity != updated_opportunity:
                    db.reference(opportunity_path + "/" + updated_opportunity.ticker).set(updated_opportunity.__repr__())
                    log("{} opportunity updated".format(updated_opportunity.ticker))

                if asset_opportunity != updated_asset_opportunity:
                    asset.opportunity = updated_asset_opportunity
                    db.reference(asset_path + "/" + asset.ticker).set(asset.__repr__())
                    log("{} asset opportunity updated".format(asset.ticker))


                db.reference(company_path + "/" + alert.ticker).set(alert.__repr__())
                db.reference(alert_data_path + "/" + alert_id).delete()
                # log("company {} updated, {} alert removed".format(alert.ticker, alert_id))

            await asyncio.sleep(1)
        except Exception as e:
            log(e)
            log("^^^ exception occurred, sleeping for 5min...")
            await asyncio.sleep(5*60)

def fast_forward_alert_consumer(alerts_data):
    companies: dict = db.reference(company_path).get()
    if companies is None: companies = dict()
    opportunities: dict = db.reference(opportunity_path).get()
    if opportunities is None: opportunities = dict()
    logs = list()
    for alert_id in alerts_data:
        alert = Company(alerts_data[alert_id])
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
def resolve_alert(alert: Company, former_opportunity: Opportunity, former_asset_opportunity: Opportunity):
    logs = list()
    new_opportunity = None
    new_asset_opportunity = None
    
    if former_opportunity is None and alert.cci < -1.5:
        new_opportunity = Opportunity({"ticker": alert.ticker, "edge_price": alert.price, "edge_cci": alert.cci,
                                       "edge_diff": alert.diff, "edge_macd": alert.macd, "signal": "000", })
        logs.append(Log("buy|create(cci<-1.5)", alert, new_opportunity))

    if former_asset_opportunity is None and alert.cci > 1.5:
        new_asset_opportunity = Opportunity({"ticker": alert.ticker, "edge_price": alert.price, "edge_cci": alert.cci,
                                             "edge_diff": alert.diff, "edge_macd": alert.macd, "signal": "000", })
        logs.append(Log("sell|create(cci>1.5)", alert, new_asset_opportunity))

    if former_opportunity is not None and alert.cci < -0.25:
        new_opportunity = former_opportunity.get_updated_copy(alert, False)

        if not new_opportunity.signal.cci and alert.cci >= -1:
            logs.append(Log("buy|signal(cci>-1)", alert, new_opportunity))
            new_opportunity.signal.cci = True
        if new_opportunity.signal.cci and alert.cci < -1.2:
            new_opportunity.signal.cci = False

        if not new_opportunity.signal.diff and alert.diff >= 0:
            logs.append(Log("buy|signal(diff>0)", alert, new_opportunity))
            new_opportunity.signal.diff = True
        if new_opportunity.signal.diff and alert.diff < -0.2:
            new_opportunity.signal.diff = False

        if not new_opportunity.signal.macd and alert.macd >= 0:
            logs.append(Log("buy|signal(macd>0)", alert, new_opportunity))
            new_opportunity.signal.macd = True
        if new_opportunity.signal.macd and alert.macd < -0.2:
            new_opportunity.signal.macd = False

    if former_asset_opportunity is not None and alert.cci > 0.25:
        new_asset_opportunity = former_asset_opportunity.get_updated_copy(alert, True)

        if not new_asset_opportunity.signal.cci and alert.cci < 1:
            logs.append(Log("sell|signal(cci<1)", alert, new_asset_opportunity))
            new_asset_opportunity.signal.cci = True
        if new_asset_opportunity.signal.cci and alert.cci > 1.2:
            new_asset_opportunity.signal.cci = False

        if not new_asset_opportunity.signal.diff and alert.diff <= 0:
            logs.append(Log("sell|signal(diff<0)", alert, new_asset_opportunity))
            new_asset_opportunity.signal.diff = True
        if new_asset_opportunity.signal.diff and alert.diff > 0.2:
            new_asset_opportunity.signal.diff = False

        if not new_asset_opportunity.signal.macd and alert.macd <= 0:
            logs.append(Log("sell|signal(macd<0)", alert, new_asset_opportunity))
            new_asset_opportunity.signal.macd = True
        if new_asset_opportunity.signal.macd and alert.macd > 0.2:
            new_asset_opportunity.signal.macd = False


    return logs, new_opportunity, new_asset_opportunity

def init():
    init_firebase()
    # analyzer.analyze()
    asyncio.run(alert_consumer())


if __name__ == '__main__':
    init()

