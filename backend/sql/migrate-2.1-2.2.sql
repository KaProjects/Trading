

ALTER TABLE Period ADD COLUMN capex DECIMAL(8, 2);
ALTER TABLE Period ADD COLUMN fcf DECIMAL(8, 2);
ALTER TABLE Record ADD COLUMN p_fcf DECIMAL(6, 2);

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
