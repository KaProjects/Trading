

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

CREATE TABLE CaseEntryTable
(
    id       INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    position SMALLINT UNSIGNED NOT NULL,
    title    VARCHAR(100),
    data     TEXT         NOT NULL,
    entryId  INT UNSIGNED NOT NULL,
    CONSTRAINT `fk_caseEntryTableEntryId` FOREIGN KEY (entryId) REFERENCES CaseEntry (id)
);

CREATE TABLE CaseCompanyName
(
    caseId INT UNSIGNED NOT NULL,
    value  VARCHAR(50)  NOT NULL,
    CONSTRAINT `uk_caseCompanyName` UNIQUE (caseId, value),
    CONSTRAINT `fk_caseCompanyNameCaseId` FOREIGN KEY (caseId) REFERENCES InvestmentCase (id)
);

ALTER TABLE CaseCompany ADD COLUMN addedAt DATE NOT NULL;
ALTER TABLE CaseCompanyName ADD COLUMN addedAt DATE NOT NULL;
