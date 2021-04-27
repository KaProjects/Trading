from datetime import datetime

time_format = "%Y-%m-%dT%H:%M:%SZ"


class Company:
    def __init__(self, attributes: dict):
        self.ticker = attributes["ticker"]
        self.time = datetime.strptime(attributes["time"], time_format)
        self.price = float(attributes["price"])
        self.cci = float(attributes["cci"])
        self.diff = float(attributes["diff"])
        self.macd = float(attributes["macd"])
        self.signal = float(attributes["signal"])

    def __repr__(self):
        return {"ticker": self.ticker, "time": datetime.strftime(self.time, time_format), "price": str(self.price),
                "cci": str(self.cci), "macd": str(self.macd), "signal": str(self.signal), "diff": str(self.diff)}

    def __str__(self):
        return str(self.__repr__())


class Opportunity:
    def __init__(self, attributes: dict):
        self.ticker = attributes["ticker"]
        self.edge_price = float(attributes["edge_price"])
        self.edge_cci = float(attributes["edge_cci"])
        self.edge_diff = float(attributes["edge_diff"])
        self.edge_macd = float(attributes["edge_macd"])
        self.signal = Signal(attributes["signal"])

    def __repr__(self):
        return {"ticker": self.ticker, "edge_price": str(self.edge_price), "edge_cci": str(self.edge_cci),
                "edge_diff": str(self.edge_diff), "edge_macd": str(self.edge_macd), "signal": self.signal.__repr__(), }

    def __str__(self):
        return str(self.__repr__())

    def __eq__(self, other):
        if type(other) != Opportunity:
            return False
        else:
            return (self.ticker == other.ticker and self.edge_price == other.edge_price and
                    self.edge_cci == other.edge_cci and self.edge_diff == other.edge_diff and
                    self.edge_macd == other.edge_macd and self.signal == other.signal)

    def get_updated_copy(self, alert: Company, asset: bool):
        new = Opportunity(self.__repr__())
        if asset:
            if alert.price > new.edge_price:
                new.edge_price = alert.price
            if alert.cci > new.edge_cci:
                new.edge_cci = alert.cci
            if alert.diff > new.edge_diff:
                new.edge_diff = alert.diff
            if alert.macd > new.edge_macd:
                new.edge_macd = alert.macd
        else:
            if alert.price < new.edge_price:
                new.edge_price = alert.price
            if alert.cci < new.edge_cci:
                new.edge_cci = alert.cci
            if alert.diff < new.edge_diff:
                new.edge_diff = alert.diff
            if alert.macd < new.edge_macd:
                new.edge_macd = alert.macd
        return new


class Asset:
    def __init__(self, attributes: dict):
        self.ticker = attributes["ticker"]
        self.price = attributes["price"]
        self.quantity = attributes["quantity"]

    def __repr__(self):
        return {"ticker": self.ticker, "price": self.price, "quantity": self.quantity, }

    def __str__(self):
        return str(self.__repr__())

    def __eq__(self, other):
        if type(other) != Asset:
            return False
        else:
            return self.ticker == other.ticker and self.price == other.price and self.quantity == other.quantity

class Signal:
    def __init__(self, value: str):
        self.cci = value[0:1] == "1"
        self.diff = value[1:2] == "1"
        self.macd = value[2:3] == "1"

    def __repr__(self):
        signal = "1" if self.cci else "0"
        signal = signal + ("1" if self.diff else "0")
        signal = signal + ("1" if self.macd else "0")
        return signal

    def __str__(self):
        return self.__repr__()

    def __eq__(self, other):
        if type(other) != Signal:
            return False
        else:
            return self.cci == other.cci and self.diff == other.diff and self.macd == other.macd

class Log:
    def __init__(self, type: str, alert: Company, opportunity: Opportunity):
        self.type = type
        self.ticker = alert.ticker
        self.time = alert.time
        self.price = float(alert.price)
        self.cci = float(alert.cci)
        self.diff = float(alert.diff)
        self.macd = float(alert.macd)
        self.signal = float(alert.signal)
        self.edge_price = float(opportunity.edge_price)
        self.edge_cci = float(opportunity.edge_cci)
        self.edge_diff = float(opportunity.edge_diff)
        self.edge_macd = float(opportunity.edge_macd)

    def __repr__(self):
        return {"type": self.type, "ticker": self.ticker, "time": datetime.strftime(self.time, time_format),
                "price": str(self.price), "cci": str(self.cci), "macd": str(self.macd), "signal": str(self.signal),
                "diff": str(self.diff),
                "edge_price": str(self.edge_price), "edge_cci": str(self.edge_cci), "edge_diff": str(self.edge_diff),
                "edge_macd": str(self.edge_macd)}

    def __str__(self):
        return str(self.__repr__())
