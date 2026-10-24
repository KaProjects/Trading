
-- Research written for a period becomes one entry per date, each able to carry images. The old
-- Period.research column is left in place until the entries have been checked.
CREATE TABLE PeriodContent
(
    id       INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    date     DATE         NOT NULL,
    content  TEXT,
    periodId INT UNSIGNED NOT NULL,
    CONSTRAINT `uk_periodContentDate` UNIQUE (periodId, date),
    CONSTRAINT `fk_periodContentPeriodId` FOREIGN KEY (periodId) REFERENCES Period (id)
);
CREATE TABLE PeriodContentImage
(
    id         INT UNSIGNED      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    mime       VARCHAR(30)       NOT NULL,
    width      SMALLINT UNSIGNED NOT NULL,
    height     SMALLINT UNSIGNED NOT NULL,
    image      MEDIUMBLOB        NOT NULL,
    thumbnail  MEDIUMBLOB        NOT NULL,
    uploadedAt DATETIME          NOT NULL,
    contentId  INT UNSIGNED      NOT NULL,
    CONSTRAINT `fk_periodContentImageContentId` FOREIGN KEY (contentId) REFERENCES PeriodContent (id)
);
