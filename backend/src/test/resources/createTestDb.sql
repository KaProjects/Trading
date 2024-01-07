DROP TABLE IF EXISTS Trade;
DROP TABLE IF EXISTS Record;

CREATE TABLE Trade (id VARCHAR(36) NOT NULL PRIMARY KEY,
                    ticker CHAR(5) NOT NULL,
                    currency CHAR(1) NOT NULL,
                    quantity DECIMAL(6,2) NOT NULL,
                    purchase_date DATE NOT NULL,
                    purchase_price DECIMAL(8,2) NOT NULL,
                    purchase_fees DECIMAL(5,2) NOT NULL,
                    sell_date DATE,
                    sell_price DECIMAL(8,2),
                    sell_fees DECIMAL(5,2)
);
CREATE TABLE Record
(
    id       VARCHAR(36) NOT NULL PRIMARY KEY,
    ticker   CHAR(5)     NOT NULL,
    date     DATE        NOT NULL,
    title    TINYTEXT    NOT NULL,
    price    DECIMAL     NOT NULL,
    text     TEXT,
    pe       DECIMAL,
    dy       DECIMAL,
    targets  TINYTEXT,
    strategy TINYTEXT
);

INSERT INTO Trade (id, ticker, currency, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees)
VALUES ('1', 'NVDA', 'D', '5', '2023-11-11', '400.5', '14.5', '2024-01-05', '500', '50');
INSERT INTO Trade (id, ticker, currency, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees)
VALUES ('2', 'SHELL', 'E', '100', '2021-05-10', '20.1', '18', '2024-01-06', '30.4', '30.5');
INSERT INTO Trade (id, ticker, currency, quantity, purchase_date, purchase_price, purchase_fees)
VALUES ('3', 'RR', 'L', '10', '2022-11-01', '200', '25');
INSERT INTO Trade (id, ticker, currency, quantity, purchase_date, purchase_price, purchase_fees)
VALUES ('4', 'CEZ', 'K', '1150.1234', '2021-04-05', '500500.25', '250.12');
