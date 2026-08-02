-- Preconditions:
--   1. Stop the backend for the entire migration.
--   2. Back up traderdb and test restoring that backup.
--   3. Run this file as a user allowed to create procedures and tables.
--
-- MariaDB DDL auto-commits. This migration therefore copies into shadow tables,
-- verifies them, and keeps the original UUID tables after the final cutover.

-- Step 1: reject orphaned rows and tables too large for INT UNSIGNED.
DELIMITER //
CREATE PROCEDURE `_assert_uuid_id_migration_preconditions`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM `Dividend` child
        LEFT JOIN `Company` company ON company.id = child.companyId
        WHERE company.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Dividend contains an orphaned companyId';
    END IF;
    IF EXISTS (
        SELECT 1 FROM `Trade` child
        LEFT JOIN `Company` company ON company.id = child.companyId
        WHERE company.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Trade contains an orphaned companyId';
    END IF;
    IF EXISTS (
        SELECT 1 FROM `Record` child
        LEFT JOIN `Company` company ON company.id = child.companyId
        WHERE company.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Record contains an orphaned companyId';
    END IF;
    IF EXISTS (
        SELECT 1 FROM `Period` child
        LEFT JOIN `Company` company ON company.id = child.companyId
        WHERE company.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Period contains an orphaned companyId';
    END IF;
    IF EXISTS (
        SELECT 1 FROM `Latest` child
        LEFT JOIN `Company` company ON company.id = child.companyId
        WHERE company.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Latest contains an orphaned companyId';
    END IF;

    IF (SELECT COUNT(*) FROM `Company`) > 4294967295
        OR (SELECT COUNT(*) FROM `Dividend`) > 4294967295
        OR (SELECT COUNT(*) FROM `Trade`) > 4294967295
        OR (SELECT COUNT(*) FROM `Record`) > 4294967295
        OR (SELECT COUNT(*) FROM `Period`) > 4294967295
        OR (SELECT COUNT(*) FROM `Latest`) > 4294967295 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A table exceeds the INT UNSIGNED identifier range';
    END IF;
END//
DELIMITER ;

CALL `_assert_uuid_id_migration_preconditions`();
DROP PROCEDURE `_assert_uuid_id_migration_preconditions`;

-- Step 2: create durable UUID-to-integer mappings.
CREATE TABLE `_migration_CompanyId`
(
    old_id VARCHAR(36) NOT NULL PRIMARY KEY,
    new_id INT UNSIGNED NOT NULL AUTO_INCREMENT UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

CREATE TABLE `_migration_DividendId` LIKE `_migration_CompanyId`;
CREATE TABLE `_migration_TradeId` LIKE `_migration_CompanyId`;
CREATE TABLE `_migration_RecordId` LIKE `_migration_CompanyId`;
CREATE TABLE `_migration_PeriodId` LIKE `_migration_CompanyId`;
CREATE TABLE `_migration_LatestId` LIKE `_migration_CompanyId`;

INSERT INTO `_migration_CompanyId` (old_id)
SELECT id FROM `Company` ORDER BY id;
INSERT INTO `_migration_DividendId` (old_id)
SELECT id FROM `Dividend` ORDER BY id;
INSERT INTO `_migration_TradeId` (old_id)
SELECT id FROM `Trade` ORDER BY id;
INSERT INTO `_migration_RecordId` (old_id)
SELECT id FROM `Record` ORDER BY id;
INSERT INTO `_migration_PeriodId` (old_id)
SELECT id FROM `Period` ORDER BY id;
INSERT INTO `_migration_LatestId` (old_id)
SELECT id FROM `Latest` ORDER BY id;

-- Step 3: create the replacement schema.
CREATE TABLE `Company_int`
(
    id       INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticker   CHAR(5)      NOT NULL,
    currency CHAR(1)      NOT NULL,
    watching BOOL         NOT NULL,
    sector   VARCHAR(30)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

CREATE TABLE `Dividend_int`
(
    id        INT UNSIGNED  NOT NULL AUTO_INCREMENT PRIMARY KEY,
    date      DATE          NOT NULL,
    dividend  DECIMAL(7, 2) NOT NULL,
    tax       DECIMAL(6, 2) NOT NULL,
    companyId INT UNSIGNED  NOT NULL,
    KEY `fk_dividendCompanyId_int` (`companyId`),
    CONSTRAINT `fk_dividendCompanyId_int`
        FOREIGN KEY (`companyId`) REFERENCES `Company_int` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

CREATE TABLE `Trade_int`
(
    id             INT UNSIGNED   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    quantity       DECIMAL(8, 4)  NOT NULL,
    purchase_date  DATE           NOT NULL,
    purchase_price DECIMAL(10, 4) NOT NULL,
    purchase_fees  DECIMAL(5, 2)  NOT NULL,
    sell_date      DATE,
    sell_price     DECIMAL(10, 4),
    sell_fees      DECIMAL(5, 2),
    companyId      INT UNSIGNED   NOT NULL,
    KEY `fk_tradeCompanyId_int` (`companyId`),
    CONSTRAINT `fk_tradeCompanyId_int`
        FOREIGN KEY (`companyId`) REFERENCES `Company_int` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

CREATE TABLE `Record_int`
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
    dy              DECIMAL(5, 2),
    asset_quantity  DECIMAL(8, 4),
    asset_price     DECIMAL(10, 4),
    companyId       INT UNSIGNED   NOT NULL,
    KEY `fk_recordCompanyId_int` (`companyId`),
    CONSTRAINT `fk_recordCompanyId_int`
        FOREIGN KEY (`companyId`) REFERENCES `Company_int` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

CREATE TABLE `Period_int`
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
    companyId    INT UNSIGNED  NOT NULL,
    KEY `fk_periodCompanyId_int` (`companyId`),
    CONSTRAINT `fk_periodCompanyId_int`
        FOREIGN KEY (`companyId`) REFERENCES `Company_int` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

CREATE TABLE `Latest_int`
(
    id        INT UNSIGNED   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    datetime  DATETIME       NOT NULL,
    price     DECIMAL(10, 4) NOT NULL,
    companyId INT UNSIGNED   NOT NULL,
    KEY `fk_latestCompanyId_int` (`companyId`),
    CONSTRAINT `fk_latestCompanyId_int`
        FOREIGN KEY (`companyId`) REFERENCES `Company_int` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

-- Step 4: copy data through the mappings.
INSERT INTO `Company_int` (id, ticker, currency, watching, sector)
SELECT ids.new_id, source.ticker, source.currency, source.watching, source.sector
FROM `Company` source
JOIN `_migration_CompanyId` ids ON ids.old_id = source.id;

INSERT INTO `Dividend_int` (id, date, dividend, tax, companyId)
SELECT ids.new_id, source.date, source.dividend, source.tax, company_ids.new_id
FROM `Dividend` source
JOIN `_migration_DividendId` ids ON ids.old_id = source.id
JOIN `_migration_CompanyId` company_ids ON company_ids.old_id = source.companyId;

INSERT INTO `Trade_int`
    (id, quantity, purchase_date, purchase_price, purchase_fees,
     sell_date, sell_price, sell_fees, companyId)
SELECT ids.new_id, source.quantity, source.purchase_date,
       source.purchase_price, source.purchase_fees, source.sell_date,
       source.sell_price, source.sell_fees, company_ids.new_id
FROM `Trade` source
JOIN `_migration_TradeId` ids ON ids.old_id = source.id
JOIN `_migration_CompanyId` company_ids ON company_ids.old_id = source.companyId;

INSERT INTO `Record_int`
    (id, date, title, content, review, strategy, retro, targets, price,
     p_rev, p_gross, p_oper, p_net, dy, asset_quantity, asset_price, companyId)
SELECT ids.new_id, source.date, source.title, source.content, source.review,
       source.strategy, source.retro, source.targets, source.price,
       source.p_rev, source.p_gross, source.p_oper, source.p_net, source.dy,
       source.asset_quantity, source.asset_price, company_ids.new_id
FROM `Record` source
JOIN `_migration_RecordId` ids ON ids.old_id = source.id
JOIN `_migration_CompanyId` company_ids ON company_ids.old_id = source.companyId;

INSERT INTO `Period_int`
    (id, name, ending_month, report_date, research, price_low, price_high,
     shares, revenue, gross_profit, oper_income, net_income, dividend, companyId)
SELECT ids.new_id, source.name, source.ending_month, source.report_date,
       source.research, source.price_low, source.price_high, source.shares,
       source.revenue, source.gross_profit, source.oper_income,
       source.net_income, source.dividend, company_ids.new_id
FROM `Period` source
JOIN `_migration_PeriodId` ids ON ids.old_id = source.id
JOIN `_migration_CompanyId` company_ids ON company_ids.old_id = source.companyId;

INSERT INTO `Latest_int` (id, datetime, price, companyId)
SELECT ids.new_id, source.datetime, source.price, company_ids.new_id
FROM `Latest` source
JOIN `_migration_LatestId` ids ON ids.old_id = source.id
JOIN `_migration_CompanyId` company_ids ON company_ids.old_id = source.companyId;

-- Step 5: abort before cutover if any copied table differs from its source.
DELIMITER //
CREATE PROCEDURE `_assert_uuid_id_migration_copy`()
BEGIN
    IF (SELECT COUNT(*) FROM `Company_int`) <> (SELECT COUNT(*) FROM `Company`) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Company copy count differs';
    END IF;
    IF (SELECT COUNT(*) FROM `Dividend_int`) <> (SELECT COUNT(*) FROM `Dividend`) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Dividend copy count differs';
    END IF;
    IF (SELECT COUNT(*) FROM `Trade_int`) <> (SELECT COUNT(*) FROM `Trade`) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Trade copy count differs';
    END IF;
    IF (SELECT COUNT(*) FROM `Record_int`) <> (SELECT COUNT(*) FROM `Record`) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Record copy count differs';
    END IF;
    IF (SELECT COUNT(*) FROM `Period_int`) <> (SELECT COUNT(*) FROM `Period`) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Period copy count differs';
    END IF;
    IF (SELECT COUNT(*) FROM `Latest_int`) <> (SELECT COUNT(*) FROM `Latest`) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Latest copy count differs';
    END IF;
END//
DELIMITER ;

CALL `_assert_uuid_id_migration_copy`();
DROP PROCEDURE `_assert_uuid_id_migration_copy`;

-- Step 6: atomically expose the integer tables. Original tables remain as
-- *_uuid_backup and can be renamed back while the backend is stopped.
SET @previous_foreign_key_checks = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

RENAME TABLE
    `Company` TO `Company_uuid_backup`,
    `Dividend` TO `Dividend_uuid_backup`,
    `Trade` TO `Trade_uuid_backup`,
    `Record` TO `Record_uuid_backup`,
    `Period` TO `Period_uuid_backup`,
    `Latest` TO `Latest_uuid_backup`,
    `Company_int` TO `Company`,
    `Dividend_int` TO `Dividend`,
    `Trade_int` TO `Trade`,
    `Record_int` TO `Record`,
    `Period_int` TO `Period`,
    `Latest_int` TO `Latest`;

SET FOREIGN_KEY_CHECKS = @previous_foreign_key_checks;

-- Step 7: inspect the result before starting the new backend.
SELECT 'Company' AS table_name, COUNT(*) AS migrated_rows FROM `Company`
UNION ALL SELECT 'Dividend', COUNT(*) FROM `Dividend`
UNION ALL SELECT 'Trade', COUNT(*) FROM `Trade`
UNION ALL SELECT 'Record', COUNT(*) FROM `Record`
UNION ALL SELECT 'Period', COUNT(*) FROM `Period`
UNION ALL SELECT 'Latest', COUNT(*) FROM `Latest`;

SELECT child_table, orphan_count
FROM (
    SELECT 'Dividend' AS child_table, COUNT(*) AS orphan_count
    FROM `Dividend` child LEFT JOIN `Company` company ON company.id = child.companyId
    WHERE company.id IS NULL
    UNION ALL
    SELECT 'Trade', COUNT(*)
    FROM `Trade` child LEFT JOIN `Company` company ON company.id = child.companyId
    WHERE company.id IS NULL
    UNION ALL
    SELECT 'Record', COUNT(*)
    FROM `Record` child LEFT JOIN `Company` company ON company.id = child.companyId
    WHERE company.id IS NULL
    UNION ALL
    SELECT 'Period', COUNT(*)
    FROM `Period` child LEFT JOIN `Company` company ON company.id = child.companyId
    WHERE company.id IS NULL
    UNION ALL
    SELECT 'Latest', COUNT(*)
    FROM `Latest` child LEFT JOIN `Company` company ON company.id = child.companyId
    WHERE company.id IS NULL
) verification;

-- Step 8: execute this cleanup only after the migrated backend is verified and
-- the separate database backup is confirmed usable.
--
-- To roll back before cleanup, stop the backend and execute this atomic rename:
-- SET @previous_foreign_key_checks = @@FOREIGN_KEY_CHECKS;
-- SET FOREIGN_KEY_CHECKS = 0;
-- RENAME TABLE
--     `Company` TO `Company_int_failed`,
--     `Dividend` TO `Dividend_int_failed`,
--     `Trade` TO `Trade_int_failed`,
--     `Record` TO `Record_int_failed`,
--     `Period` TO `Period_int_failed`,
--     `Latest` TO `Latest_int_failed`,
--     `Company_uuid_backup` TO `Company`,
--     `Dividend_uuid_backup` TO `Dividend`,
--     `Trade_uuid_backup` TO `Trade`,
--     `Record_uuid_backup` TO `Record`,
--     `Period_uuid_backup` TO `Period`,
--     `Latest_uuid_backup` TO `Latest`;
-- SET FOREIGN_KEY_CHECKS = @previous_foreign_key_checks;
--
-- After successful verification, remove the rollback tables and mappings:
-- DROP TABLE `Dividend_uuid_backup`, `Trade_uuid_backup`, `Record_uuid_backup`,
--            `Period_uuid_backup`, `Latest_uuid_backup`, `Company_uuid_backup`;
-- DROP TABLE `_migration_DividendId`, `_migration_TradeId`,
--            `_migration_RecordId`, `_migration_PeriodId`,
--            `_migration_LatestId`, `_migration_CompanyId`;
