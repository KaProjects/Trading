
ALTER TABLE Record MODIFY COLUMN title TINYTEXT;
ALTER TABLE Record MODIFY COLUMN strategy TEXT;
ALTER TABLE Record ADD COLUMN review TEXT;
ALTER TABLE Record ADD COLUMN retro TEXT;

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

ALTER TABLE Period ADD COLUMN adjusted_eps DECIMAL(6, 2);

ALTER TABLE Trade ADD COLUMN portfolio VARCHAR(30);

CREATE TABLE Tag
(
    id           INT UNSIGNED    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    value        VARCHAR(30)     NOT NULL,
    companyId    INT UNSIGNED    NOT NULL,
    CONSTRAINT `fk_tagCompanyId` FOREIGN KEY (companyId) REFERENCES Company (id)
);

ALTER TABLE Company DROP COLUMN watching;
