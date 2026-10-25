
-- Investment cases: a thesis followed over time across the companies it covers. "Case" is a
-- keyword in SQL and JPQL, hence the longer table name.
CREATE TABLE InvestmentCase
(
    id        INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(100) NOT NULL,
    createdAt DATE         NOT NULL,
    closedAt  DATE
);

CREATE TABLE CaseCompany
(
    caseId    INT UNSIGNED NOT NULL,
    companyId INT UNSIGNED NOT NULL,
    CONSTRAINT `uk_caseCompany` UNIQUE (caseId, companyId),
    CONSTRAINT `fk_caseCompanyCaseId` FOREIGN KEY (caseId) REFERENCES InvestmentCase (id),
    CONSTRAINT `fk_caseCompanyCompanyId` FOREIGN KEY (companyId) REFERENCES Company (id)
);

CREATE TABLE CaseEntry
(
    id      INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    date    DATE         NOT NULL,
    content TEXT,
    caseId  INT UNSIGNED NOT NULL,
    CONSTRAINT `uk_caseEntryDate` UNIQUE (caseId, date),
    CONSTRAINT `fk_caseEntryCaseId` FOREIGN KEY (caseId) REFERENCES InvestmentCase (id)
);

CREATE TABLE CaseEntryImage
(
    id         INT UNSIGNED      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    mime       VARCHAR(30)       NOT NULL,
    width      SMALLINT UNSIGNED NOT NULL,
    height     SMALLINT UNSIGNED NOT NULL,
    image      MEDIUMBLOB        NOT NULL,
    thumbnail  MEDIUMBLOB        NOT NULL,
    uploadedAt DATETIME          NOT NULL,
    entryId    INT UNSIGNED      NOT NULL,
    CONSTRAINT `fk_caseEntryImageEntryId` FOREIGN KEY (entryId) REFERENCES CaseEntry (id)
);
