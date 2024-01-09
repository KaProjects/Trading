DROP TABLE IF EXISTS Trade;
DROP TABLE IF EXISTS Record;

CREATE TABLE Trade
(
    id             VARCHAR(36)   NOT NULL PRIMARY KEY,
    ticker         CHAR(5)       NOT NULL,
    currency       CHAR(1)       NOT NULL,
    quantity       DECIMAL(8, 4) NOT NULL,
    purchase_date  DATE          NOT NULL,
    purchase_price DECIMAL(8, 2) NOT NULL,
    purchase_fees  DECIMAL(5, 2) NOT NULL,
    sell_date      DATE,
    sell_price     DECIMAL(8, 2),
    sell_fees      DECIMAL(5, 2)
);
CREATE TABLE Record
(
    id       VARCHAR(36)   NOT NULL PRIMARY KEY,
    ticker   CHAR(5)       NOT NULL,
    date     DATE          NOT NULL,
    title    TINYTEXT      NOT NULL,
    price    DECIMAL(8, 2) NOT NULL,
    text     TEXT,
    pe       DECIMAL(5, 2),
    dy       DECIMAL(5, 2),
    targets  TINYTEXT,
    strategy TINYTEXT
);

INSERT INTO Trade (id, ticker, currency, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('-2', 'XXX', 'K', '10', '2018-04-05', '0', '0', '2018-05-05', '10', '5');
INSERT INTO Trade (id, ticker, currency, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('-1', 'YYY', 'K', '10', '2018-04-05', '10', '50', '2018-05-05', '0', '0');
INSERT INTO Trade (id, ticker, currency, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('1', 'NVDA', '$', '5', '2023-11-11', '400.5', '14.5', '2024-01-05', '500', '50');
INSERT INTO Trade (id, ticker, currency, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('2', 'SHELL', '€', '100', '2021-05-10', '20.1', '18', '2023-12-31', '30.4', '30.5');
INSERT INTO Trade (id, ticker, currency, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('3', 'RR', '£', '10', '2022-11-01', '200000', '25');
INSERT INTO Trade (id, ticker, currency, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('4', 'CEZ', 'K', '1150.1234', '2023-04-05', '500.25', '250.12');


INSERT INTO Record (id, ticker, date, title, price) VALUES ('1', 'NVDA', '2023-11-11', 'bought 5@400.5$', '400.5');
INSERT INTO Record (id, ticker, date, title, price) VALUES ('1b', 'NVDA', '2024-01-05', 'sold 5@500$', '500');
INSERT INTO Record (id, ticker, date, title, price) VALUES ('2', 'SHELL', '2021-05-10', 'bought 100@20.1€', '400.5');
INSERT INTO Record (id, ticker, date, title, price) VALUES ('2b', 'SHELL', '2023-07-10', 'sold 100@30.4€', '30.4');
INSERT INTO Record (id, ticker, date, title, price) VALUES ('3', 'RR', '2022-11-01', 'bought 10@200£', '400.5');
INSERT INTO Record (id, ticker, date, title, price) VALUES ('4', 'CEZ', '2021-04-05', 'bought 1150.1234@500500.25K', '400.5');

