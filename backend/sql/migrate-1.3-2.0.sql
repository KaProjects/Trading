CREATE TABLE Period
(
    id           VARCHAR(36)   NOT NULL PRIMARY KEY,
    name         CHAR(4)       NOT NULL,
    ending_month CHAR(4)       NOT NULL,
    report_date  DATE,
    research     TEXT,
    price_low    DECIMAL(10, 4),
    price_high   DECIMAL(10, 4),
    shares       DECIMAL(8, 2),
    revenue      DECIMAL(8, 2),
    gross_profit DECIMAL(8, 2),
    oper_income  DECIMAL(8, 2),
    net_income   DECIMAL(8, 2),
    dividend     DECIMAL(8, 2),
    companyId    VARCHAR(36)   NOT NULL,
    CONSTRAINT `fk_periodCompanyId` FOREIGN KEY (companyId) REFERENCES Company (id)
);
CREATE TABLE Latest
(
    id           VARCHAR(36)     NOT NULL PRIMARY KEY,
    datetime     DATETIME        NOT NULL,
    price        DECIMAL(10, 4)  NOT NULL,
    companyId    VARCHAR(36)     NOT NULL,
    CONSTRAINT `fk_latestCompanyId` FOREIGN KEY (companyId) REFERENCES Company (id)
);

DROP TABLE Financial;

ALTER TABLE Company DROP COLUMN shares_float;

ALTER TABLE Record RENAME COLUMN ps TO p_rev;
ALTER TABLE Record MODIFY COLUMN p_rev DECIMAL(6, 2);

ALTER TABLE Record RENAME COLUMN pe TO p_net;
ALTER TABLE Record MODIFY COLUMN p_net DECIMAL(6, 2);

ALTER TABLE Record ADD COLUMN p_gross DECIMAL(6, 2);
ALTER TABLE Record ADD COLUMN p_oper DECIMAL(6, 2);
ALTER TABLE Record ADD COLUMN asset_quantity DECIMAL(8, 4);
ALTER TABLE Record ADD COLUMN asset_price DECIMAL(10, 4);

