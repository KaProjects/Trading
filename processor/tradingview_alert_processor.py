
from classes import Company, Opportunity, Log
from firebase_admin import db
import traceback
import utils
import asyncio

alert_data_path = "alert/data/"
company_path = "company-dep/"
opportunity_path = "opportunity/"
asset_path = "asset/"
log_path = "log/"


def log(message: str):
    utils.log("TAC", message)


def get_sleep(sleep_counter):
    if sleep_counter <= 5:
        return 1
    elif sleep_counter <= 10:
        return 5
    elif sleep_counter <= 15:
        return 30
    else:
        return 60


def resolve_alert(alert: Company, former_opportunity: Opportunity):
    logs = list()
    new_opportunity = None

    if former_opportunity is None:
        if alert.cci < -1.5:
            new_opportunity = Opportunity({"ticker": alert.id(), "edge_price": alert.price, "edge_cci": alert.cci,
                                           "edge_diff": alert.diff, "edge_macd": alert.macd, "signal": "000", })
            logs.append(Log("buy|create(cci<-1.5)", alert, new_opportunity))

        if alert.cci > 1.5:
            new_opportunity = Opportunity({"ticker": alert.id(), "edge_price": alert.price, "edge_cci": alert.cci,
                                           "edge_diff": alert.diff, "edge_macd": alert.macd, "signal": "000", })
            logs.append(Log("sell|create(cci>1.5)", alert, new_opportunity))

    else:
        if alert.cci < -0.25:
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

        if alert.cci > 0.25:
            new_opportunity = former_opportunity.get_updated_copy(alert, True)

            if not new_opportunity.signal.cci and alert.cci < 1:
                logs.append(Log("sell|signal(cci<1)", alert, new_opportunity))
                new_opportunity.signal.cci = True
            if new_opportunity.signal.cci and alert.cci > 1.2:
                new_opportunity.signal.cci = False

            if not new_opportunity.signal.diff and alert.diff <= 0:
                logs.append(Log("sell|signal(diff<0)", alert, new_opportunity))
                new_opportunity.signal.diff = True
            if new_opportunity.signal.diff and alert.diff > 0.2:
                new_opportunity.signal.diff = False

            if not new_opportunity.signal.macd and alert.macd <= 0:
                logs.append(Log("sell|signal(macd<0)", alert, new_opportunity))
                new_opportunity.signal.macd = True
            if new_opportunity.signal.macd and alert.macd > 0.2:
                new_opportunity.signal.macd = False

    return logs, new_opportunity


def fast_forward_alert_consumer(alerts_data):
    companies: dict = db.reference(company_path).get()
    if companies is None: companies = dict()
    opportunities: dict = db.reference(opportunity_path).get()
    if opportunities is None: opportunities = dict()
    logs = list()
    for alert_id in alerts_data:
        try:
            alert = Company(alerts_data[alert_id])
        except ValueError as e:
            log("Alert: {} Error: {}".format(alert_id, e))
            continue
        opportunity = None
        if alert.id() in opportunities:
            opportunity_data: dict = opportunities[alert.id()]
            opportunity = Opportunity(opportunity_data)

        new_logs, updated_opportunity = resolve_alert(alert, opportunity)
        if new_logs is not None:
            for new_log in new_logs:
                logs.append(new_log)

        if opportunity is not None and updated_opportunity is None:
            del opportunities[alert.id()]
        if updated_opportunity is not None:
            opportunities[alert.id()] = updated_opportunity.__repr__()
        companies[alert.id()] = alerts_data[alert_id]

    db.reference(company_path).set(companies)
    db.reference(opportunity_path).set(opportunities)
    for event_log in logs:
        db.reference(log_path).push(event_log.__repr__())
    log("{} new logs created".format(len(logs)))
    db.reference(alert_data_path).delete()


# if fast_forward_alert_consumer couldn't delete alert node because too big (50k should delete)
def prune_db():
    alerts_count = len(db.reference(alert_data_path).get())
    print("Alerts count: {}".format(alerts_count))
    counter = 0
    while alerts_count > 1000:
        for key in db.reference(alert_data_path).order_by_key().limit_to_last(1000).get():
            db.reference(alert_data_path + "/" + key).delete()
        counter += 1
        print("{}. mile deleted".format(counter))
        alerts_count -= 1000


async def run():
    sleep_counter = 0
    while True:
        try:
            alerts_data: dict = db.reference(alert_data_path).get()
            if alerts_data is None:
                sleep_counter += 1
                sleep = get_sleep(sleep_counter)
                log("no alerts found, sleeping for {}min...".format(sleep))
                await asyncio.sleep(sleep * 60)
                continue
            else:
                sleep_counter = 0

            log("alerts found: {}".format(len(alerts_data)))

            if len(alerts_data) > 1000:
                fast_forward_alert_consumer(alerts_data)
                log("alerts consumption fast forwarded")
                await asyncio.sleep(1)
                continue

            for alert_id in alerts_data:
                try:
                    alert = Company(alerts_data[alert_id])
                except ValueError as e:
                    log("Alert: {} Error: {}".format(alert_id, e))
                    db.reference(alert_data_path + "/" + alert_id).delete()
                    continue

                opportunity_data: dict = db.reference(opportunity_path + "/" + alert.id()).get()
                opportunity = None if opportunity_data is None else Opportunity(opportunity_data)

                logs, updated_opportunity = resolve_alert(alert, opportunity)

                if logs is not None:
                    for event_log in logs:
                        # obj = db.reference(log_path).push(event_log.__repr__())
                        log("log {} type '{}' for {} created".format(obj.key, event_log.type, event_log.ticker))

                if opportunity is not None and updated_opportunity is None:
                    db.reference(opportunity_path + "/" + opportunity.ticker).delete()
                    log("{} opportunity deleted".format(opportunity.ticker))
                elif opportunity is None and updated_opportunity is not None:
                    db.reference(opportunity_path + "/" + updated_opportunity.ticker).set(
                        updated_opportunity.__repr__())
                    log("{} opportunity created".format(updated_opportunity.ticker))
                elif opportunity is not None and updated_opportunity is not None and opportunity != updated_opportunity:
                    db.reference(opportunity_path + "/" + updated_opportunity.ticker).set(
                        updated_opportunity.__repr__())
                    log("{} opportunity updated".format(updated_opportunity.ticker))

                db.reference(company_path + "/" + alert.id()).set(alert.__repr__())
                db.reference(alert_data_path + "/" + alert_id).delete()
                # log("company {} updated, {} alert removed".format(alert.id(), alert_id))

            await asyncio.sleep(1)
        except Exception:
            log(traceback.format_exc())
            sleep = get_sleep(sleep_counter)
            log("^^^ exception occurred, sleeping for {}min...".format(sleep))
            await asyncio.sleep(sleep * 60)
