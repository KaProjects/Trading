INSERT INTO Company (id, ticker, currency, watching, sector) VALUES ('e7c49260-53da-42c1-80cf-eccf6ed928a7', 'NVDA', '$', false, 'HARDWARE');
INSERT INTO Company (id, ticker, currency, watching, sector) VALUES ('e7c49260-53da-42c1-0123-eccf6ed928a7', 'AMD', '$', false, 'HARDWARE');
INSERT INTO Company (id, ticker, currency, watching, sector) VALUES ('e7c49260-53da-5678-0123-eccf6ed928a7', 'INTC', '$', true, 'HARDWARE');
INSERT INTO Company (id, ticker, currency, watching, sector) VALUES ('c9b68d4f-2d1a-4f5e-b8c7-9e0a1b2f3d4c', 'AA', '€', true, 'SOFTWARE');
INSERT INTO Company (id, ticker, currency, watching, sector) VALUES ('a1f5e8c7-4b0d-4a3f-8e2b-7c6d5a4b3f2e', 'BBC', '$', false, 'FINANCE');
INSERT INTO Company (id, ticker, currency, watching, sector) VALUES ('2e7d3b9c-5a6f-4d8e-9c1b-0a3f4e5d6c7b', 'CX', 'K', true, 'ETF');
INSERT INTO Company (id, ticker, currency, watching, sector) VALUES ('5d4b3f2e-1c9a-4e8b-a3d7-6f0e9c8b7a6d', 'DDD', '$', false, 'COMMUNICATION_SERVICES');
INSERT INTO Company (id, ticker, currency, watching, sector) VALUES ('f0e9c8b7-a6d5-4c3b-2a1f-0e9d8c7b6a5f', 'EFG', '£', true, 'HEALTH_TECH');


INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('c8c0a4c3-3c95-4d29-9f3f-4f6d8b8f3d47', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '10', '2018-04-05', '0', '0', '2018-05-05', '10', '5');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('1c7c5c5c-2c4c-4d0e-9b6c-1b5e7fa3ea22', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '10', '2018-04-05', '10', '50', '2018-06-05', '0', '0');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('f3c2bb0c-8c0e-4a4a-9e93-4a9b2caa9ef0', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '5', '2023-11-11', '400.5', '14.5', '2024-01-05', '500', '50');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('0b8e92b8-7b42-4a0b-8a30-5a1c1e5f91f6', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '100', '2021-05-10', '20.1', '18', '2023-12-31', '30.4', '30.5');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('7f6a1ea0-4b79-4af2-a637-1f7d43cd7188', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '10', '2018-04-05', '100', '25');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('e3e4ae27-73e8-40d2-bc3d-2c6be71aa5c4', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '123', '2018-04-05', '500.25', '250.12');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('f79bcb2f-cf21-4cf4-9d0c-5d1af1b863e1', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '400', '2022-11-01', '1000', '25');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('a1a16af8-7be8-48e3-9f67-c9d4ea0d624c', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '1150.1234', '2023-04-05', '300.25', '250.12');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('c8c01111-3c95-4d29-9f3f-4f6d8b8f3d47', 'c9b68d4f-2d1a-4f5e-b8c7-9e0a1b2f3d4c', '10', '2018-04-05', '0', '0', '2018-05-05', '10', '5');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('a1xc6af8-7be8-48e3-9f67-c9d4ea0d624c', 'e7c49260-53da-5678-0123-eccf6ed928a7', '1150.1234', '2025-04-15', '300.25', '250.12');


INSERT INTO Record (id, companyId, date, title, price, strategy,targets, p_rev,p_gross,p_oper,p_net,dy,asset_quantity,asset_price) VALUES ('a1c7a08a-d94a-4b48-b7cf-8a3c10c29146', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '2023-12-11', 'bought 5@400.5$', '400.5','aaa', '~100', '1', '2', '3', '4', '5', '10', '20');
INSERT INTO Record (id, companyId, date, title, price) VALUES ('c8afcd7d-7f3d-41a0-8d6f-0ebf21a77377', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '2023-11-11', 'bought 5@400.5$', '400.5');
INSERT INTO Record (id, companyId, date, title, price, content) VALUES ('e9cf62c8-76e3-4b93-88b3-09a473f54f38', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '2023-10-11', 'bought 5@400.5$', '400.5', '[{"type": "paragraph", "children": [{ "text": "XXXXX" }]}]');
INSERT INTO Record (id, companyId, date, title, price, content) VALUES ('xxcf62c8-76e3-4b93-88b3-09a473f54f38', 'e7c49260-53da-42c1-0123-eccf6ed928a7', '2025-05-11', 'x', '400.5', '[{"type": "paragraph", "children": [{ "text": "XXXXX" }]}]');


INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('01596f61-19f6-409e-87b9-2bbc6b9b59a6', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '24Q4', '2501', '2025-02-15', '1200', '720', '480', '240', '30', '1000', '120', '80');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares) VALUES ('4e3d8f8a-276f-46d6-b7d7-f5f13cfd7810', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '24Q3', '2410', '2024-11-15', '1000', '500', '300', '80', '20', '1000');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend) VALUES ('f3ddc6a0-86c0-40f2-b0c7-0b74a1cf1b9a', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '24Q2', '2407', '2024-08-15', '800', '400', '200', '0', '10');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend) VALUES ('f9a26de4-6c9c-4f6b-8b79-0f4d6a58e671', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '24Q1', '2404', '2024-02-15', '1234', '456', '123', '11', '7');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend) VALUES ('8a30c65d-56d2-4a0a-a462-d833fbd2f990', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '23Q4', '2401', '2024-02-01', '900', '450', '240', '120', '15');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('xx596f61-19f6-409e-87b9-2bbc6b9b59a6', 'e7c49260-53da-42c1-0123-eccf6ed928a7', '25Q3', '2510', '2025-11-04', '1850', '960', '410', '260', '0', '1250', '185', '132');


INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('bff10473-3d42-45ae-965b-2f0d11e2d40d', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '2021-06-01', '70', '7');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('24a17552-c52a-4497-8b0e-8628d6847efc', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '2022-12-01', '80', '8');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('8f683c27-f334-41b1-9406-189d62ae2171', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '2021-12-01', '1000', '100');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('719c545d-26d6-4437-8e1a-e1eea53b3967', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '2020-12-01', '1000', '100');


-- Recent active and closed trades for selector, aggregate, and asset scenarios.
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('10000000-0000-4000-8000-000000000001', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '20', '2026-06-20', '180.25', '4.95');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('10000000-0000-4000-8000-000000000002', 'e7c49260-53da-42c1-0123-eccf6ed928a7', '30', '2026-02-10', '110.25', '4.95');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('10000000-0000-4000-8000-000000000003', 'e7c49260-53da-42c1-0123-eccf6ed928a7', '15', '2025-08-12', '145.50', '4.95', '2026-02-20', '132.25', '4.95');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('10000000-0000-4000-8000-000000000004', 'e7c49260-53da-5678-0123-eccf6ed928a7', '50', '2026-05-12', '22.80', '3.95');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('10000000-0000-4000-8000-000000000005', 'e7c49260-53da-5678-0123-eccf6ed928a7', '40', '2025-10-03', '24.10', '3.95', '2026-04-16', '20.30', '3.95');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('10000000-0000-4000-8000-000000000006', 'c9b68d4f-2d1a-4f5e-b8c7-9e0a1b2f3d4c', '12', '2026-01-20', '82.40', '3.50');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('10000000-0000-4000-8000-000000000007', 'c9b68d4f-2d1a-4f5e-b8c7-9e0a1b2f3d4c', '20', '2025-06-02', '71', '4.20', '2026-03-18', '88.40', '4.20');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('10000000-0000-4000-8000-000000000008', 'a1f5e8c7-4b0d-4a3f-8e2b-7c6d5a4b3f2e', '8', '2026-06-05', '155.75', '5.25');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('10000000-0000-4000-8000-000000000009', '2e7d3b9c-5a6f-4d8e-9c1b-0a3f4e5d6c7b', '25', '2025-12-01', '1850', '99');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('10000000-0000-4000-8000-000000000010', '5d4b3f2e-1c9a-4e8b-a3d7-6f0e9c8b7a6d', '100', '2025-09-11', '42.50', '8.50', '2026-05-14', '54', '8.50');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('10000000-0000-4000-8000-000000000011', 'f0e9c8b7-a6d5-4c3b-2a1f-0e9d8c7b6a5f', '18', '2026-03-06', '96.20', '6.25');


-- Records cover complete, partial, and title-less editor states.
INSERT INTO Record (id, companyId, date, title, price, content, review, strategy, retro, targets, p_rev, p_gross, p_oper, p_net, dy, asset_quantity, asset_price) VALUES ('20000000-0000-4000-8000-000000000001', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '2026-07-20', 'Q1 review', '173.50', '[{"type":"paragraph","children":[{"text":"AI demand remains strong and data-center backlog supports the base case."}]}]', '[{"type":"paragraph","children":[{"text":"Margins expanded while execution remained consistent."}]}]', '[{"type":"paragraph","children":[{"text":"Add only below the lower target and keep position sizing disciplined."}]}]', '[{"type":"paragraph","children":[{"text":"The previous entry was early but the thesis remained intact."}]}]', '165-210', '18.40', '30.10', '34.50', '38.20', '0.04', '20', '180.25');
INSERT INTO Record (id, companyId, date, title, price, content, strategy, targets, p_rev, p_net, asset_quantity, asset_price) VALUES ('20000000-0000-4000-8000-000000000002', 'e7c49260-53da-42c1-0123-eccf6ed928a7', '2026-06-30', 'Product cycle checkpoint', '158.25', '[{"type":"paragraph","children":[{"text":"Server demand improved and the product roadmap remains competitive."}]}]', '[{"type":"paragraph","children":[{"text":"Hold the core position and reassess after the next report."}]}]', '145-190', '8.70', '31.40', '30', '110.25');
INSERT INTO Record (id, companyId, date, title, price, review, retro, p_rev, p_gross, p_oper, p_net, asset_quantity, asset_price) VALUES ('20000000-0000-4000-8000-000000000003', 'e7c49260-53da-5678-0123-eccf6ed928a7', '2026-06-18', 'Turnaround review', '21.50', '[{"type":"paragraph","children":[{"text":"Revenue stabilized, but operating leverage is still weak."}]}]', '[{"type":"paragraph","children":[{"text":"Wait for evidence before increasing exposure."}]}]', '2.10', '4.60', '18.20', '35.10', '1200.1234', '288.69');
INSERT INTO Record (id, companyId, date, title, price, content, targets, dy, asset_quantity, asset_price) VALUES ('20000000-0000-4000-8000-000000000004', 'c9b68d4f-2d1a-4f5e-b8c7-9e0a1b2f3d4c', '2026-04-01', 'Post-sale review', '90', '[{"type":"paragraph","children":[{"text":"The valuation reached the planned exit range."}]}]', '78-95', '1.25', '12', '82.40');
INSERT INTO Record (id, companyId, date, price, content, strategy, targets, p_net, dy, asset_quantity, asset_price) VALUES ('20000000-0000-4000-8000-000000000005', 'a1f5e8c7-4b0d-4a3f-8e2b-7c6d5a4b3f2e', '2026-06-06', '160', '[{"type":"paragraph","children":[{"text":"Credit quality remains healthy and capital returns are improving."}]}]', '[{"type":"paragraph","children":[{"text":"Keep as a diversified financial holding."}]}]', '145-185', '12.80', '2.10', '8', '155.75');
INSERT INTO Record (id, companyId, date, title, price, content, review, targets, p_rev, p_net, dy, asset_quantity, asset_price) VALUES ('20000000-0000-4000-8000-000000000006', 'f0e9c8b7-a6d5-4c3b-2a1f-0e9d8c7b6a5f', '2026-07-01', 'Pipeline update', '102.30', '[{"type":"paragraph","children":[{"text":"The clinical pipeline progressed without material delays."}]}]', '[{"type":"paragraph","children":[{"text":"Risk remains concentrated in the next trial readout."}]}]', '88-125', '6.20', '24.50', '1.80', '18', '96.20');


-- Quarterly financial history for NVDA and AMD.
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('30000000-0000-4000-8000-000000000001', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '25Q1', '2504', '2025-05-28', '1380', '815', '535', '285', '32', '990', '155', '92');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('30000000-0000-4000-8000-000000000002', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '25Q2', '2507', '2025-08-27', '1540', '925', '625', '350', '34', '980', '185', '95');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('30000000-0000-4000-8000-000000000003', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '25Q3', '2510', '2025-11-19', '1695', '1035', '710', '420', '36', '970', '198', '145');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('30000000-0000-4000-8000-000000000004', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '25Q4', '2601', '2026-02-25', '1880', '1165', '810', '505', '38', '960', '205', '132');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('30000000-0000-4000-8000-000000000005', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '26Q1', '2604', '2026-05-27', '2075', '1300', '925', '590', '40', '950', '195', '150');

INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('30000000-0000-4000-8000-000000000006', 'e7c49260-53da-42c1-0123-eccf6ed928a7', '24Q4', '2501', '2025-02-04', '1320', '670', '270', '160', '0', '1290', '178', '118');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('30000000-0000-4000-8000-000000000007', 'e7c49260-53da-42c1-0123-eccf6ed928a7', '25Q1', '2504', '2025-05-06', '1450', '745', '305', '185', '0', '1280', '165', '76');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('30000000-0000-4000-8000-000000000008', 'e7c49260-53da-42c1-0123-eccf6ed928a7', '25Q2', '2507', '2025-08-05', '1610', '835', '355', '220', '0', '1270', '182', '120');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('30000000-0000-4000-8000-000000000009', 'e7c49260-53da-42c1-0123-eccf6ed928a7', '25Q4', '2601', '2026-02-03', '1980', '1040', '455', '305', '0', '1240', '205', '151');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('30000000-0000-4000-8000-000000000010', 'e7c49260-53da-42c1-0123-eccf6ed928a7', '26Q1', '2604', '2026-05-05', '2180', '1160', '525', '365', '0', '1225', '190', '135');


-- Full-year and half-year histories exercise their separate growth rules.
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('30000000-0000-4000-8000-000000000011', 'e7c49260-53da-5678-0123-eccf6ed928a7', '23FY', '2312', '2024-01-25', '5200', '2180', '120', '-80', '45', '1350', '51', '24');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('30000000-0000-4000-8000-000000000012', 'e7c49260-53da-5678-0123-eccf6ed928a7', '24FY', '2412', '2025-01-30', '5480', '2320', '180', '-50', '48', '1330', '50', '18');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('30000000-0000-4000-8000-000000000013', 'e7c49260-53da-5678-0123-eccf6ed928a7', '25FY', '2512', '2026-01-29', '5790', '2510', '260', '30', '50', '1310', '29', '17');

INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('30000000-0000-4000-8000-000000000014', 'c9b68d4f-2d1a-4f5e-b8c7-9e0a1b2f3d4c', '24H1', '2406', '2024-08-01', '780', '430', '185', '105', '12', '420', '78', '54');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('30000000-0000-4000-8000-000000000015', 'c9b68d4f-2d1a-4f5e-b8c7-9e0a1b2f3d4c', '24H2', '2412', '2025-02-03', '840', '475', '205', '120', '14', '418', '84', '62');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('30000000-0000-4000-8000-000000000016', 'c9b68d4f-2d1a-4f5e-b8c7-9e0a1b2f3d4c', '25H1', '2506', '2025-08-04', '910', '520', '235', '145', '15', '415', '92', '68');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES ('30000000-0000-4000-8000-000000000017', 'c9b68d4f-2d1a-4f5e-b8c7-9e0a1b2f3d4c', '25H2', '2512', '2026-02-02', '995', '580', '270', '170', '17', '410', '96', '73');


-- Multi-currency dividend history.
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('40000000-0000-4000-8000-000000000001', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '2025-06-10', '12.50', '1.88');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('40000000-0000-4000-8000-000000000002', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '2026-06-10', '16', '2.40');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('40000000-0000-4000-8000-000000000003', 'e7c49260-53da-42c1-0123-eccf6ed928a7', '2025-09-15', '9.75', '1.46');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('40000000-0000-4000-8000-000000000004', 'e7c49260-53da-5678-0123-eccf6ed928a7', '2026-03-01', '25', '3.75');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('40000000-0000-4000-8000-000000000005', 'c9b68d4f-2d1a-4f5e-b8c7-9e0a1b2f3d4c', '2025-11-20', '18.40', '2.76');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('40000000-0000-4000-8000-000000000006', 'a1f5e8c7-4b0d-4a3f-8e2b-7c6d5a4b3f2e', '2026-04-15', '32', '4.80');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('40000000-0000-4000-8000-000000000007', '2e7d3b9c-5a6f-4d8e-9c1b-0a3f4e5d6c7b', '2025-12-18', '1250', '187.50');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('40000000-0000-4000-8000-000000000008', '5d4b3f2e-1c9a-4e8b-a3d7-6f0e9c8b7a6d', '2026-02-12', '28.50', '4.28');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('40000000-0000-4000-8000-000000000009', 'f0e9c8b7-a6d5-4c3b-2a1f-0e9d8c7b6a5f', '2025-10-08', '21.60', '3.24');


-- One fallback market price per company.
INSERT INTO Latest (id, companyId, datetime, price) VALUES ('50000000-0000-4000-8000-000000000001', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '2026-07-24 16:00:00', '173.50');
INSERT INTO Latest (id, companyId, datetime, price) VALUES ('50000000-0000-4000-8000-000000000002', 'e7c49260-53da-42c1-0123-eccf6ed928a7', '2026-07-24 16:00:00', '158.25');
INSERT INTO Latest (id, companyId, datetime, price) VALUES ('50000000-0000-4000-8000-000000000003', 'e7c49260-53da-5678-0123-eccf6ed928a7', '2026-07-24 16:00:00', '21.50');
INSERT INTO Latest (id, companyId, datetime, price) VALUES ('50000000-0000-4000-8000-000000000004', 'c9b68d4f-2d1a-4f5e-b8c7-9e0a1b2f3d4c', '2026-07-24 16:00:00', '90');
INSERT INTO Latest (id, companyId, datetime, price) VALUES ('50000000-0000-4000-8000-000000000005', 'a1f5e8c7-4b0d-4a3f-8e2b-7c6d5a4b3f2e', '2026-07-24 16:00:00', '160');
INSERT INTO Latest (id, companyId, datetime, price) VALUES ('50000000-0000-4000-8000-000000000006', '2e7d3b9c-5a6f-4d8e-9c1b-0a3f4e5d6c7b', '2026-07-24 16:00:00', '1950');
INSERT INTO Latest (id, companyId, datetime, price) VALUES ('50000000-0000-4000-8000-000000000007', '5d4b3f2e-1c9a-4e8b-a3d7-6f0e9c8b7a6d', '2026-07-24 16:00:00', '56.80');
INSERT INTO Latest (id, companyId, datetime, price) VALUES ('50000000-0000-4000-8000-000000000008', 'f0e9c8b7-a6d5-4c3b-2a1f-0e9d8c7b6a5f', '2026-07-24 16:00:00', '102.30');
