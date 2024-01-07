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