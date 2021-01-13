from datetime import datetime

time_format = "%Y-%m-%dT%H:%M:%SZ"

class Alert:
    def __init__(self, attributes: dict):
        self.ticker = attributes["ticker"]
        self.time = datetime.strptime(attributes["time"],time_format)
        self.price = float(attributes["price"])
        self.cci = float(attributes["cci"])
        self.diff = float(attributes["diff"])
        self.macd = float(attributes["macd"])
        self.signal = float(attributes["signal"])

    def __repr__(self):
        return {"ticker":self.ticker,"time":datetime.strftime(self.time, time_format),"price":str(self.price),
                "cci":str(self.cci),"macd":str(self.macd),"signal":str(self.signal),"diff":str(self.diff)}

    def __str__(self):
        return str(self.__repr__())



class Company:
    def __init__(self, attributes: dict):
        self.ticker = attributes["ticker"]
        self.time = datetime.strptime(attributes["time"],time_format)
        self.price = float(attributes["price"])
        self.cci = float(attributes["cci"])
        self.diff = float(attributes["diff"])
        self.macd = float(attributes["macd"])
        self.signal = float(attributes["signal"])

    def __repr__(self):
        return {"ticker":self.ticker,"time":datetime.strftime(self.time, time_format),"price":str(self.price),
                "cci":str(self.cci),"macd":str(self.macd),"signal":str(self.signal),"diff":str(self.diff)}

    def __str__(self):
        return str(self.__repr__())

class Opportunity:
    def __init__(self, attributes: dict):
        self.ticker = attributes["ticker"]
        self.min_price = float(attributes["min_price"])
        self.min_cci = float(attributes["min_cci"])
        self.min_diff = float(attributes["min_diff"])
        self.min_macd = float(attributes["min_macd"])

    def __repr__(self):
        return {"ticker": self.ticker, "min_price": str(self.min_price),
                    "min_cci": str(self.min_cci), "min_diff": str(self.min_diff), "min_macd": str(self.min_macd)}

    def __str__(self):
        return str(self.__repr__())

    def update(self, alert: Alert):
        updated = False
        if alert.price < self.min_price:
            self.min_price = alert.price
            updated = True
        if alert.cci < self.min_cci:
            self.min_cci = alert.cci
            updated = True
        if alert.diff < self.min_diff:
            self.min_diff = alert.diff
            updated = True
        if alert.macd < self.min_macd:
            self.min_macd = alert.macd
            updated = True
        return updated

class Log:
    def __init__(self, type: str, alert: Alert, opportunity: Opportunity):
        self.type = type
        self.ticker = alert.ticker
        self.time = alert.time
        self.price = float(alert.price)
        self.cci = float(alert.cci)
        self.diff = float(alert.diff)
        self.macd = float(alert.macd)
        self.signal = float(alert.signal)
        self.min_price = float(opportunity.min_price)
        self.min_cci = float(opportunity.min_cci)
        self.min_diff = float(opportunity.min_diff)
        self.min_macd = float(opportunity.min_macd)

    def __repr__(self):
        return {"type": self.type, "ticker": self.ticker, "time":datetime.strftime(self.time, time_format),
                "price":str(self.price),"cci":str(self.cci),"macd":str(self.macd),"signal":str(self.signal),"diff":str(self.diff),
                "min_price": str(self.min_price),"min_cci": str(self.min_cci), "min_diff": str(self.min_diff), "min_macd": str(self.min_macd)}

    def __str__(self):
        return str(self.__repr__())