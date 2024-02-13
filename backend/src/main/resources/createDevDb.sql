DROP TABLE IF EXISTS Dividend;
DROP TABLE IF EXISTS Trade;
DROP TABLE IF EXISTS Record;
DROP TABLE IF EXISTS Financial;
DROP TABLE IF EXISTS Company;

CREATE TABLE Company
(
    id           VARCHAR(36) NOT NULL PRIMARY KEY,
    ticker       CHAR(5)     NOT NULL,
    currency     CHAR(1)     NOT NULL,
    watching     BOOL        NOT NULL,
    shares_float VARCHAR(7),
    sector       VARCHAR(30)
);
CREATE TABLE Financial
(
    id         VARCHAR(36)   NOT NULL PRIMARY KEY,
    quarter    CHAR(4)       NOT NULL,
    revenue    DECIMAL(8, 2) NOT NULL,
    net_income DECIMAL(8, 2) NOT NULL,
    eps        DECIMAL(4, 2) NOT NULL,
    companyId  VARCHAR(36)   NOT NULL,
    CONSTRAINT `fk_financialCompanyId` FOREIGN KEY (companyId) REFERENCES Company (id)
);
CREATE TABLE Dividend
(
    id        VARCHAR(36)   NOT NULL PRIMARY KEY,
    date      DATE          NOT NULL,
    dividend  DECIMAL(7, 2) NOT NULL,
    tax       DECIMAL(6, 2) NOT NULL,
    companyId VARCHAR(36)   NOT NULL,
    CONSTRAINT `fk_dividendCompanyId` FOREIGN KEY (companyId) REFERENCES Company (id)
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
    companyId      VARCHAR(36)    NOT NULL,
    CONSTRAINT `fk_tradeCompanyId` FOREIGN KEY (companyId) REFERENCES Company (id)
);
CREATE TABLE Record
(
    id        VARCHAR(36)    NOT NULL PRIMARY KEY,
    date      DATE           NOT NULL,
    title     TINYTEXT       NOT NULL,
    price     DECIMAL(10, 4) NOT NULL,
    pe        DECIMAL(5, 2),
    ps        DECIMAL(5, 2),
    dy        DECIMAL(5, 2),
    targets   TINYTEXT,
    content   TEXT,
    strategy  TINYTEXT,
    companyId VARCHAR(36)    NOT NULL,
    CONSTRAINT `fk_recordCompanyId` FOREIGN KEY (companyId) REFERENCES Company (id)
);


