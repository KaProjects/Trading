
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
    file = open("data.tsv", "r")
    data = file.read()
    file.close()
    print("# file data.tsv successfully loaded")

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

    file = open("data_import.sql", "w")
    counter = 0
    for trade in trades:
        if trade.sellDate is None:
            file.write("INSERT INTO Trade (id, ticker, currency, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('{0}', '{1}', '{2}', '{3}', '{4}', '{5}', '{6}');"
                       .format(trades.index(trade), trade.ticker, trade.currency, trade.quantity, trade.purchaseDate, trade.purchasePrice, trade.purchaseFees))
            counter += 1
        else:
            file.write("INSERT INTO Trade (id, ticker, currency, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('{0}', '{1}', '{2}', '{3}', '{4}', '{5}', '{6}', '{7}', '{8}', '{9}');"
                       .format(trades.index(trade), trade.ticker, trade.currency, trade.quantity, trade.purchaseDate, trade.purchasePrice, trade.purchaseFees, trade.sellDate, trade.sellPrice, trade.sellFees))
            counter += 1

        file.write("INSERT INTO Record (id, ticker, date, title, price) VALUES ('{0}', '{1}', '{2}', '{3}', '{4}');"
                   .format("b" + str(trades.index(trade)), trade.ticker, trade.purchaseDate, "bought " + trade.quantity + "@" + trade.purchasePrice + trade.currency, trade.purchasePrice))
        counter += 1

        if trade.sellDate is not None:
            file.write("INSERT INTO Record (id, ticker, date, title, price) VALUES ('{0}', '{1}', '{2}', '{3}', '{4}');"
                       .format("s" + str(trades.index(trade)), trade.ticker, trade.sellDate, "sold " + trade.quantity + "@" + trade.sellPrice + trade.currency, trade.sellPrice))
            counter += 1

    print("# prepared " + str(counter) + " sql inserts")
    file.close()
    print("# generated data_import.sql successfully")


if __name__ == '__main__':
    process()