
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
