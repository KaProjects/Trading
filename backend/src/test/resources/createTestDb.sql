DROP TABLE IF EXISTS Trade;
DROP TABLE IF EXISTS Record;
DROP TABLE IF EXISTS Company;

CREATE TABLE Company
(
    id       VARCHAR(36) NOT NULL PRIMARY KEY,
    ticker   CHAR(5)     NOT NULL,
    currency CHAR(1)     NOT NULL
);
CREATE TABLE Trade
(
    id             VARCHAR(36)    NOT NULL PRIMARY KEY,
    quantity       DECIMAL(8, 4)  NOT NULL,
    purchase_date  DATE           NOT NULL,
    purchase_price DECIMAL(10, 4) NOT NULL,
    purchase_fees  DECIMAL(5, 2)  NOT NULL,
    sell_date      DATE,
    sell_price     DECIMAL(10, 4),
    sell_fees      DECIMAL(5, 2),
    companyId VARCHAR(36) NOT NULL,
    CONSTRAINT `fk_tradeCompanyId` FOREIGN KEY (companyId) REFERENCES Company (id)
);
CREATE TABLE Record
(
    id       VARCHAR(36)    NOT NULL PRIMARY KEY,
    date     DATE           NOT NULL,
    title    TINYTEXT       NOT NULL,
    price    DECIMAL(10, 4) NOT NULL,
    pe       DECIMAL(5, 2),
    dy       DECIMAL(5, 2),
    targets  TINYTEXT,
    content  TEXT,
    strategy TINYTEXT,
    companyId VARCHAR(36) NOT NULL,
    CONSTRAINT `fk_recordCompanyId` FOREIGN KEY (companyId) REFERENCES Company (id)
);

INSERT INTO Company (id, ticker, currency) VALUES ('e7c49260-53da-42c1-80cf-eccf6ed928a7', 'XXX', 'K');
INSERT INTO Company (id, ticker, currency) VALUES ('0a16ba1d-99de-4306-8fc5-81ee11b60ea0', 'YYY', 'K');
INSERT INTO Company (id, ticker, currency) VALUES ('adb89a0a-86bc-4854-8a55-058ad2e6308f', 'NVDA', '$');
INSERT INTO Company (id, ticker, currency) VALUES ('4efe9235-0c00-4b51-aa81-f2febbb65232', 'SHELL', '€');
INSERT INTO Company (id, ticker, currency) VALUES ('eaca1473-33c2-4128-a0f2-b7853cdece41', 'RR', '£');
INSERT INTO Company (id, ticker, currency) VALUES ('61cc8096-87ac-4197-8b54-7c2595274bcc', 'CEZ', 'K');
INSERT INTO Company (id, ticker, currency) VALUES ('66c725b2-9987-4653-a49c-3a9906168d2a', 'ABCD', '$');
INSERT INTO Company (id, ticker, currency) VALUES ('d98c9ea1-ef2a-400a-bc7f-00d90e5d8e10', 'XRC', '$');

INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('-2', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '10', '2018-04-05', '0', '0', '2018-05-05', '10', '5');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('-1', '0a16ba1d-99de-4306-8fc5-81ee11b60ea0', '10', '2018-04-05', '10', '50', '2018-05-05', '0', '0');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('1', 'adb89a0a-86bc-4854-8a55-058ad2e6308f', '5', '2023-11-11', '400.5', '14.5', '2024-01-05', '500', '50');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('2', '4efe9235-0c00-4b51-aa81-f2febbb65232', '100', '2021-05-10', '20.1', '18', '2023-12-31', '30.4', '30.5');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('3', 'eaca1473-33c2-4128-a0f2-b7853cdece41', '10', '2022-11-01', '200000', '25');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('4', '61cc8096-87ac-4197-8b54-7c2595274bcc', '1150.1234', '2023-04-05', '500.25', '250.12');


INSERT INTO Record (id, companyId, date, title, price) VALUES ('1', 'adb89a0a-86bc-4854-8a55-058ad2e6308f', '2023-11-11', 'bought 5@400.5$', '400.5');
INSERT INTO Record (id, companyId, date, title, price) VALUES ('1b', 'adb89a0a-86bc-4854-8a55-058ad2e6308f', '2024-01-05', 'sold 5@500$', '500');
INSERT INTO Record (id, companyId, date, title, price) VALUES ('2', '4efe9235-0c00-4b51-aa81-f2febbb65232', '2021-05-10', 'bought 100@20.1€', '400.5');
INSERT INTO Record (id, companyId, date, title, price) VALUES ('2b', '4efe9235-0c00-4b51-aa81-f2febbb65232', '2023-07-10', 'sold 100@30.4€', '30.4');
INSERT INTO Record (id, companyId, date, title, price) VALUES ('3', 'eaca1473-33c2-4128-a0f2-b7853cdece41', '2022-11-01', 'bought 10@200£', '400.5');
INSERT INTO Record (id, companyId, date, title, price) VALUES ('4', '61cc8096-87ac-4197-8b54-7c2595274bcc', '2021-04-05', 'bought 1150.1234@500500.25K', '400.5');
INSERT INTO Record (id, companyId, date, title, price, pe) VALUES ('2ccbf4fe-dbe7-4c40-a2a2-49bf79f15dad', '66c725b2-9987-4653-a49c-3a9906168d2a', '2021-04-05', 'xxx', '100', '10.1');


