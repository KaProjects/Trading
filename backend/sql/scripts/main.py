import sys
import uuid


class Company:
    id: str = None
    ticker: str = None
    currency: str = None

    def __init__(self, id: str, ticker: str, currency: str):
        self.id = id
        self.ticker = ticker
        self.currency = currency


class Trade:
    ticker: str = None
    currency: str = None
    purchaseDate: str = None
    purchasePrice: str = None
    quantity: str = None
    purchaseFees: str = None
    sellDate: str = None
    sellPrice: str = None
    sellFees: str = None


def format_date(date: str):
    if not date: return None
    split = date.split(".")
    return split[2] + "-" + split[1] + "-" + split[0]


def format_decimal(decimal: str):
    if not decimal: return None
    return decimal.replace(",", ".")


def process():
    file = open("trade.tsv", "r")
    data = file.read()
    file.close()
    print("# file trade.tsv successfully loaded")

    lines = data.split("\n")
    trades = list()
    for line in lines:
        values = line.split("\t")
        trade = Trade()
        trade.ticker = values[1]
        trade.currency = values[2]
        trade.purchaseDate = format_date(values[3])
        trade.purchasePrice = format_decimal(values[4])
        trade.quantity = format_decimal(values[5])
        trade.purchaseFees = format_decimal(values[6])
        if len(values) > 12:
            trade.sellDate = format_date(values[8])
            trade.sellPrice = format_decimal(values[9])
            if values[10] and values[10] != values[5]: print("# WARNING: quantities @ " + str(values))
            trade.sellFees = format_decimal(values[11])
        trades.append(trade)
    print("# found " + str(len(trades)) + " trades")

    file = open("trade.sql", "w")

    companies_map = dict()
    for trade in trades:
        if trade.ticker not in companies_map:
            companies_map[trade.ticker] = Company(str(uuid.uuid4()), trade.ticker, trade.currency)

    counter_companies = 0
    for company in companies_map.values():
        file.write("INSERT INTO Company (id, ticker, currency) VALUES ('{0}', '{1}', '{2}');"
                   .format(company.id, company.ticker, company.currency))
        counter_companies += 1

    print("# prepared " + str(counter_companies) + " company sql inserts")

    counter_trades = 0
    counter_records = 0
    for trade in trades:
        if trade.sellDate is None:
            file.write("INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('{0}', '{1}', '{2}', '{3}', '{4}', '{5}');"
                       .format(str(uuid.uuid4()), companies_map[trade.ticker].id, trade.quantity, trade.purchaseDate, trade.purchasePrice, trade.purchaseFees))
            counter_trades += 1
        else:
            file.write("INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('{0}', '{1}', '{2}', '{3}', '{4}', '{5}', '{6}', '{7}', '{8}');"
                       .format(str(uuid.uuid4()), companies_map[trade.ticker].id, trade.quantity, trade.purchaseDate, trade.purchasePrice, trade.purchaseFees, trade.sellDate, trade.sellPrice, trade.sellFees))
            counter_trades += 1

        file.write("INSERT INTO Record (id, companyId, date, title, price) VALUES ('{0}', '{1}', '{2}', '{3}', '{4}');"
                   .format(str(uuid.uuid4()), companies_map[trade.ticker].id, trade.purchaseDate, "bought " + trade.quantity + "@" + trade.purchasePrice + trade.currency, trade.purchasePrice))
        counter_records += 1

        if trade.sellDate is not None:
            file.write("INSERT INTO Record (id, companyId, date, title, price) VALUES ('{0}', '{1}', '{2}', '{3}', '{4}');"
                       .format(str(uuid.uuid4()), companies_map[trade.ticker].id, trade.sellDate, "sold " + trade.quantity + "@" + trade.sellPrice + trade.currency, trade.sellPrice))
            counter_records += 1

    print("# prepared " + str(counter_trades) + " trade sql inserts")
    print("# prepared " + str(counter_records) + " record sql inserts")
    print("# generated trade.sql successfully")
    file.close()


if __name__ == '__main__':
    process()
