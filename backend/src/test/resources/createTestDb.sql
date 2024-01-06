DROP TABLE IF EXISTS Trade;
DROP TABLE IF EXISTS Record;

CREATE TABLE Trade (id VARCHAR(36) NOT NULL PRIMARY KEY,
                    ticker CHAR(4) NOT NULL,
                    currency CHAR(1) NOT NULL,
                    quantity DECIMAL NOT NULL,
                    purchase_date DATE NOT NULL,
                    purchase_price DECIMAL NOT NULL,
                    purchase_fees DECIMAL NOT NULL,
                    sell_date DATE NOT NULL,
                    sell_price DECIMAL NOT NULL,
                    sell_fees DECIMAL NOT NULL
);
CREATE TABLE Record
(
    id       VARCHAR(36) NOT NULL PRIMARY KEY,
    ticker   CHAR(4)     NOT NULL,
    date     DATE        NOT NULL,
    title    TINYTEXT    NOT NULL,
    price    DECIMAL     NOT NULL,
    text     TEXT,
    pe       DECIMAL,
    dy       DECIMAL,
    targets  TINYTEXT,
    strategy TINYTEXT
);