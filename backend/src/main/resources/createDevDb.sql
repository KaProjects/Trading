INSERT INTO Company (id, ticker, currency, watching, sector) VALUES ('e7c49260-53da-42c1-80cf-eccf6ed928a7', 'NVDA', '$', false, 'FINANCE');

INSERT INTO Company (id, ticker, currency, watching, sector) VALUES ('c9b68d4f-2d1a-4f5e-b8c7-9e0a1b2f3d4c', 'AA', '€', true, 'SOFTWARE');
INSERT INTO Company (id, ticker, currency, watching, sector) VALUES ('a1f5e8c7-4b0d-4a3f-8e2b-7c6d5a4b3f2e', 'BBC', '$', false, 'FINANCE');
INSERT INTO Company (id, ticker, currency, watching, sector) VALUES ('2e7d3b9c-5a6f-4d8e-9c1b-0a3f4e5d6c7b', 'CX', 'K', true, 'HARDWARE');
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

INSERT INTO Record (id, companyId, date, title, price,
                    strategy,targets, p_rev,p_gross,p_oper,p_net,dy,asset_quantity,asset_price) VALUES ('a1c7a08a-d94a-4b48-b7cf-8a3c10c29146', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '2023-12-11', 'bought 5@400.5$', '400.5',
                    'aaa', '~100', '1', '2', '3', '4', '5', '10', '20');
INSERT INTO Record (id, companyId, date, title, price) VALUES ('c8afcd7d-7f3d-41a0-8d6f-0ebf21a77377', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '2023-11-11', 'bought 5@400.5$', '400.5');
INSERT INTO Record (id, companyId, date, title, price, content) VALUES ('e9cf62c8-76e3-4b93-88b3-09a473f54f38', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '2023-10-11', 'bought 5@400.5$', '400.5', '[{"type": "paragrap", "children": [{ "text": "XXXXX" }]}]');

INSERT INTO Period (id, companyId, name, ending_month, price_high, price_low) VALUES ('01596f61-19f6-409e-87b9-2bbc6b9b59a6', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '24Q4', '2501', '120', '80');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, cogs, op_exp, net_income, dividend, shares) VALUES ('4e3d8f8a-276f-46d6-b7d7-f5f13cfd7810', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '24Q3', '2410', '2024-11-15', '1000', '500', '300', '80', '20', '1000');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, cogs, op_exp, net_income, dividend) VALUES ('f3ddc6a0-86c0-40f2-b0c7-0b74a1cf1b9a', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '24Q2', '2407', '2024-08-15', '800', '400', '200', '0', '10');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, cogs, op_exp, net_income, dividend) VALUES ('f9a26de4-6c9c-4f6b-8b79-0f4d6a58e671', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '24Q1', '2404', '2024-02-15', '1234', '456', '123', '11', '7');

INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('bff10473-3d42-45ae-965b-2f0d11e2d40d', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '2021-06-01', '70', '7');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('24a17552-c52a-4497-8b0e-8628d6847efc', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '2022-12-01', '80', '8');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('8f683c27-f334-41b1-9406-189d62ae2171', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '2021-12-01', '1000', '100');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('719c545d-26d6-4437-8e1a-e1eea53b3967', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '2020-12-01', '1000', '100');