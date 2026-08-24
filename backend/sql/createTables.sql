DROP TABLE IF EXISTS Target;
DROP TABLE IF EXISTS Todo;
DROP TABLE IF EXISTS Tag;
DROP TABLE IF EXISTS Estimate;
DROP TABLE IF EXISTS Latest;
DROP TABLE IF EXISTS Period;
DROP TABLE IF EXISTS Dividend;
DROP TABLE IF EXISTS Trade;
DROP TABLE IF EXISTS Record;
DROP TABLE IF EXISTS Company;

CREATE TABLE Company
(
    id       INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticker   CHAR(5)     NOT NULL,
    currency CHAR(1)     NOT NULL,
    sector   VARCHAR(30)
);
CREATE TABLE Dividend
(
    id        INT UNSIGNED  NOT NULL AUTO_INCREMENT PRIMARY KEY,
    date      DATE          NOT NULL,
    dividend  DECIMAL(7, 2) NOT NULL,
    tax       DECIMAL(6, 2) NOT NULL,
    companyId INT UNSIGNED  NOT NULL,
    CONSTRAINT `fk_dividendCompanyId` FOREIGN KEY (companyId) REFERENCES Company (id)
);
CREATE TABLE Trade
(
    id             INT UNSIGNED   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    quantity       DECIMAL(8, 4)  NOT NULL,
    purchase_date  DATE           NOT NULL,
    purchase_price DECIMAL(10, 4) NOT NULL,
    purchase_fees  DECIMAL(5, 2)  NOT NULL,
    sell_date      DATE,
    sell_price     DECIMAL(10, 4),
    sell_fees      DECIMAL(5, 2),
    portfolio      VARCHAR(30),
    companyId      INT UNSIGNED   NOT NULL,
    CONSTRAINT `fk_tradeCompanyId` FOREIGN KEY (companyId) REFERENCES Company (id)
);
CREATE TABLE Record
(
    id              INT UNSIGNED   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    date            DATE           NOT NULL,
    title           TINYTEXT,
    content         TEXT,
    review          TEXT,
    strategy        TEXT,
    retro           TEXT,
    targets         TINYTEXT,
    price           DECIMAL(10, 4) NOT NULL,
    p_rev           DECIMAL(6, 2),
    p_gross         DECIMAL(6, 2),
    p_oper          DECIMAL(6, 2),
    p_net           DECIMAL(6, 2),
    p_fcf           DECIMAL(6, 2),
    dy              DECIMAL(5, 2),
    asset_quantity  DECIMAL(8, 4),
    asset_price     DECIMAL(10, 4),
    companyId       INT UNSIGNED   NOT NULL,
    CONSTRAINT `fk_recordCompanyId` FOREIGN KEY (companyId) REFERENCES Company (id)
);
CREATE TABLE Period
(
    id           INT UNSIGNED  NOT NULL AUTO_INCREMENT PRIMARY KEY,
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
    capex        DECIMAL(8, 2),
    fcf          DECIMAL(8, 2),
    adjusted_eps DECIMAL(6, 2),
    companyId    INT UNSIGNED  NOT NULL,
    CONSTRAINT `fk_periodCompanyId` FOREIGN KEY (companyId) REFERENCES Company (id)
);
CREATE TABLE Latest
(
    id           INT UNSIGNED    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    datetime     DATETIME        NOT NULL,
    price        DECIMAL(10, 4)  NOT NULL,
    companyId    INT UNSIGNED    NOT NULL,
    CONSTRAINT `fk_latestCompanyId` FOREIGN KEY (companyId) REFERENCES Company (id)
);
CREATE TABLE Estimate
(
    id           INT UNSIGNED    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    datetime     DATETIME        NOT NULL,
    current      DECIMAL(6, 2)   NOT NULL,
    next1        DECIMAL(6, 2),
    next2        DECIMAL(6, 2),
    next3        DECIMAL(6, 2),
    periodId     INT UNSIGNED    NOT NULL,
    CONSTRAINT `fk_estimatePeriodId` FOREIGN KEY (periodId) REFERENCES Period (id)
);
CREATE TABLE Tag
(
    id           INT UNSIGNED    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    value        VARCHAR(30)     NOT NULL,
    companyId    INT UNSIGNED    NOT NULL,
    CONSTRAINT `fk_tagCompanyId` FOREIGN KEY (companyId) REFERENCES Company (id)
);
CREATE TABLE Todo
(
    id        INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    content   TEXT         NOT NULL,
    createdAt DATETIME     NOT NULL,
    companyId INT UNSIGNED
);
CREATE TABLE Target
(
    id          INT UNSIGNED   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    date        DATE           NOT NULL,
    institution VARCHAR(50)    NOT NULL,
    price       DECIMAL(10, 4) NOT NULL,
    rating      VARCHAR(30),
    overview    VARCHAR(1000),
    takeaway1   VARCHAR(500),
    takeaway2   VARCHAR(500),
    takeaway3   VARCHAR(500),
    takeaway4   VARCHAR(500),
    periodId    INT UNSIGNED   NOT NULL,
    CONSTRAINT `uk_targetIdentity` UNIQUE (periodId, date, institution, price),
    CONSTRAINT `fk_targetPeriodId` FOREIGN KEY (periodId) REFERENCES Period (id)
);
