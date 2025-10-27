
INSERT INTO Company (id, ticker, currency, watching) VALUES ('e7c49260-53da-42c1-80cf-eccf6ed928a7', 'XXX', 'K', false);
INSERT INTO Company (id, ticker, currency, watching) VALUES ('0a16ba1d-99de-4306-8fc5-81ee11b60ea0', 'YYY', 'K', false);
INSERT INTO Company (id, ticker, currency, watching) VALUES ('6a1e9d75-63b3-45a0-9ed7-dc38cfd22551', 'ZZZ', '$', true);
INSERT INTO Company (id, ticker, currency, watching, shares, sector) VALUES ('adb89a0a-86bc-4854-8a55-058ad2e6308f', 'NVDA', '$', true, '900.78M', 'SEMICONDUCTORS');
INSERT INTO Company (id, ticker, currency, watching, sector) VALUES ('4efe9235-0c00-4b51-aa81-f2febbb65232', 'SHELL', '€', true, 'ENERGY_MINERALS');
INSERT INTO Company (id, ticker, currency, watching) VALUES ('eaca1473-33c2-4128-a0f2-b7853cdece41', 'RR', '£', true);
INSERT INTO Company (id, ticker, currency, watching) VALUES ('61cc8096-87ac-4197-8b54-7c2595274bcc', 'CEZ', 'K', true);
INSERT INTO Company (id, ticker, currency, watching) VALUES ('66c725b2-9987-4653-a49c-3a9906168d2a', 'ABCD', '$', true);
INSERT INTO Company (id, ticker, currency, watching) VALUES ('d98c9ea1-ef2a-400a-bc7f-00d90e5d8e10', 'XRC', '$', true);
INSERT INTO Company (id, ticker, currency, watching) VALUES ('5cda9759-c31f-4c5c-ac0b-b5e1de01fdf0', 'XRSA', '$', true);
INSERT INTO Company (id, ticker, currency, watching) VALUES ('7781fba0-7071-45d7-b952-3c5f07ce564c', 'XRSB', '$', true);
INSERT INTO Company (id, ticker, currency, watching, shares, sector) VALUES ('5afe260b-c433-426c-9710-e9ff99faa5aa', 'XCW', '$', false, '2B', 'ELECTRIC_VEHICLES');
INSERT INTO Company (id, ticker, currency, watching) VALUES ('21322ef8-9e26-4eda-bf74-b0f0eb8925b1', 'XTC', '$', true);
INSERT INTO Company (id, ticker, currency, watching) VALUES ('c65ea6ac-d848-46dd-98bc-9e3d99f39b21', 'XTS', '$', true);
INSERT INTO Company (id, ticker, currency, watching) VALUES ('ededb691-b3c0-4c66-b03d-4e7b46bb2489', 'XRL', '$', true);
INSERT INTO Company (id, ticker, currency, watching) VALUES ('6877c444-00ee-4af5-99ef-415980484d8c', 'XFC', '$', true);
INSERT INTO Company (id, ticker, currency, watching) VALUES ('6877c555-00ee-4af5-99ef-415980484d8c', 'XFQ', '$', true);


INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('-2', 'e7c49260-53da-42c1-80cf-eccf6ed928a7', '10', '2018-04-05', '0', '0', '2018-05-05', '10', '5');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('-1', '0a16ba1d-99de-4306-8fc5-81ee11b60ea0', '10', '2018-04-05', '10', '50', '2018-05-05', '0', '0');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('1', 'adb89a0a-86bc-4854-8a55-058ad2e6308f', '5', '2023-11-11', '400.5', '14.5', '2024-01-05', '500', '50');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES ('2', '4efe9235-0c00-4b51-aa81-f2febbb65232', '100', '2021-05-10', '20.1', '18', '2023-12-31', '30.4', '30.5');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('3', 'eaca1473-33c2-4128-a0f2-b7853cdece41', '10', '2022-11-01', '200000', '25');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('4', '61cc8096-87ac-4197-8b54-7c2595274bcc', '1150.1234', '2023-04-05', '500.25', '250.12');

INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('ae79deca-e26d-4d43-ae83-94ee6b7b7382', '5cda9759-c31f-4c5c-ac0b-b5e1de01fdf0', '10', '2021-01-05', '90', '10');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('d94c5b43-f4e0-48a7-9dc6-9da7ea0cafe4', '7781fba0-7071-45d7-b952-3c5f07ce564c', '20', '2020-12-30', '180', '10');

INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('ff467fa5-77c6-4b1a-96b1-4038c476e038', 'c65ea6ac-d848-46dd-98bc-9e3d99f39b21', '1', '2020-03-15', '400', '10');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('91d9253e-aee5-4d86-9c3e-18102bff698d', 'c65ea6ac-d848-46dd-98bc-9e3d99f39b21', '5', '2020-04-05', '450', '10');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES ('19993bde-6d06-4006-918f-77baa8062e42', 'c65ea6ac-d848-46dd-98bc-9e3d99f39b21', '7.5', '2020-05-01', '500', '10');


INSERT INTO Record (id, companyId, date, title, price) VALUES ('1', 'adb89a0a-86bc-4854-8a55-058ad2e6308f', '2023-11-11', 'bought 5@400.5$', '400.5');
INSERT INTO Record (id, companyId, date, title, price) VALUES ('1b', 'adb89a0a-86bc-4854-8a55-058ad2e6308f', '2024-01-05', 'sold 5@500$', '500');
INSERT INTO Record (id, companyId, date, title, price) VALUES ('2', '4efe9235-0c00-4b51-aa81-f2febbb65232', '2021-05-10', 'bought 100@20.1€', '400.5');
INSERT INTO Record (id, companyId, date, title, price) VALUES ('2b', '4efe9235-0c00-4b51-aa81-f2febbb65232', '2023-07-10', 'sold 100@30.4€', '30.4');
INSERT INTO Record (id, companyId, date, title, price) VALUES ('3', 'eaca1473-33c2-4128-a0f2-b7853cdece41', '2022-11-01', 'bought 10@200£', '400.5');
INSERT INTO Record (id, companyId, date, title, price) VALUES ('4', '61cc8096-87ac-4197-8b54-7c2595274bcc', '2021-04-05', 'bought 1150.1234@500500.25K', '400.5');
INSERT INTO Record (id, companyId, date, title, price, pe) VALUES ('2ccbf4fe-dbe7-4c40-a2a2-49bf79f15dad', '66c725b2-9987-4653-a49c-3a9906168d2a', '2021-04-05', 'xxx', '100', '10.1');

INSERT INTO Record (id, companyId, date, title, price, strategy) VALUES ('3ec4752e-a716-4aed-ad05-350af8d42a26', '5cda9759-c31f-4c5c-ac0b-b5e1de01fdf0', '2021-04-05', '2021 strategy of A', '100', 'strat of A');
INSERT INTO Record (id, companyId, date, title, price, strategy) VALUES ('042a4b91-298d-49ff-8941-884501af459d', '7781fba0-7071-45d7-b952-3c5f07ce564c', '2020-12-20', '2020 strategy of B', '200', 'strat of B');

INSERT INTO Record (id, companyId, date, title, price) VALUES ('ff16e565-ec34-46f1-989b-3eb64e8c7cac', 'ededb691-b3c0-4c66-b03d-4e7b46bb2489', '2022-11-01', 'latest price', '100');
INSERT INTO Record (id, companyId, date, title, price, pe) VALUES ('b062b0d1-8682-4f13-81e9-2cc59dc63e66', 'ededb691-b3c0-4c66-b03d-4e7b46bb2489', '2022-10-01', 'latest pe', '90', '5');
INSERT INTO Record (id, companyId, date, title, price, pe, ps) VALUES ('69d3e956-b155-40c2-8e2e-8679a2d0f657', 'ededb691-b3c0-4c66-b03d-4e7b46bb2489', '2022-09-01', 'latest ps', '80', '5.5', '4');
INSERT INTO Record (id, companyId, date, title, price, ps, dy) VALUES ('675574fe-d212-4a96-9526-f3a8b91215bb', 'ededb691-b3c0-4c66-b03d-4e7b46bb2489', '2022-08-01', 'latest dy', '110', '3', '1.5');
INSERT INTO Record (id, companyId, date, title, price, dy, targets) VALUES ('d0ccae7d-af8c-4625-8ca3-e9628765df4f', 'ededb691-b3c0-4c66-b03d-4e7b46bb2489', '2022-07-01', 'latest targets', '90', '1', '10-5~7');
INSERT INTO Record (id, companyId, date, title, price, targets, strategy) VALUES ('0ac349c6-431d-4379-a2aa-2a8d4ba9c3ca', 'ededb691-b3c0-4c66-b03d-4e7b46bb2489', '2022-06-01', 'latest strategy', '70', '8-4~6', 'strat');

INSERT INTO Record (id, companyId, date, title, price) VALUES ('1389a449-2137-4da7-a7e4-8f9267741f0e', '6877c444-00ee-4af5-99ef-415980484d8c', '2023-07-01', 'latest price', '60');
INSERT INTO Record (id, companyId, date, title, price) VALUES ('1389a449-2137-5555-a7e4-8f9267741f0e', '6877c555-00ee-4af5-99ef-415980484d8c', '2023-07-01', 'latest price', '60');


INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('bff10473-3d42-45ae-965b-2f0d11e2d40d', 'adb89a0a-86bc-4854-8a55-058ad2e6308f', '2021-06-01', '70', '7');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('24a17552-c52a-4497-8b0e-8628d6847efc', 'adb89a0a-86bc-4854-8a55-058ad2e6308f', '2022-12-01', '80', '8');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('8f683c27-f334-41b1-9406-189d62ae2171', '61cc8096-87ac-4197-8b54-7c2595274bcc', '2021-12-01', '1000', '100');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES ('719c545d-26d6-4437-8e1a-e1eea53b3967', '66c725b2-9987-4653-a49c-3a9906168d2a', '2020-12-01', '1000', '100');


INSERT INTO Financial (id, companyId, quarter, revenue, cogs, op_exp, net_income) VALUES ('01596f61-19f6-409e-87b9-2bbc6b9b59a6', 'adb89a0a-86bc-4854-8a55-058ad2e6308f', '23Q3', '18120','8000','1100', '8125');
INSERT INTO Financial (id, companyId, quarter, revenue, cogs, op_exp, net_income) VALUES ('58c89a63-2baf-481e-ad6a-4b03e5163bf9', 'adb89a0a-86bc-4854-8a55-058ad2e6308f', '23Q2', '13507', '6007', '1300', '4587');
INSERT INTO Financial (id, companyId, quarter, revenue, cogs, op_exp, net_income) VALUES ('1c53f589-a00d-4b83-af33-b31fed0d9915', 'adb89a0a-86bc-4854-8a55-058ad2e6308f', '23Q1', '7192', '2192', '2600', '1234');

INSERT INTO Financial (id, companyId, quarter, revenue, cogs, op_exp, net_income) VALUES ('a1f81be4-5a7a-426d-b9ca-90d9e08e7cb8', '6877c444-00ee-4af5-99ef-415980484d8c', '23Q1', '1000', '100', '50', '800');
INSERT INTO Financial (id, companyId, quarter, revenue, cogs, op_exp, net_income) VALUES ('a1f81be4-5a55-426d-b9ca-90d9e08e7cb8', '6877c555-00ee-4af5-99ef-415980484d8c', '23Q1', '1000', '100', '50', '800');

INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, cogs, op_exp, net_income, dividend) VALUES ('getPeriods1', 'adb89a0a-86bc-4854-8a55-058ad2e6308f', '24Q4', '2501', '2025-02-15', '1000', '500', '300', '80', '20');
INSERT INTO Period (id, companyId, name, ending_month) VALUES ('getPeriods2', 'adb89a0a-86bc-4854-8a55-058ad2e6308f', '25Q1', '2504');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, cogs, op_exp, net_income, dividend) VALUES ('getPeriods3', 'adb89a0a-86bc-4854-8a55-058ad2e6308f', '24Q3', '2410', '2024-11-15', '500', '400', '50', '0', '10');

INSERT INTO Period (id, companyId, name, ending_month, report_date) VALUES ('9c3e92c2-2a66-46de-83f6-57eeb8a7b4b4', '0a16ba1d-99de-4306-8fc5-81ee11b60ea0', '22Q2', '2501', '2025-02-15');


-- for rest.*EndpointsTest.create tests
INSERT INTO Company (id, ticker, currency, watching) VALUES ('6877c555-1234-4af5-99ef-415980484d8c', 'CRE', '$', true);


-- for rest.*EndpointsTests.update tests
INSERT INTO Company (id, ticker, currency, watching) VALUES ('9c858901-8a57-4791-81fe-4c455b099bc9', 'UPD', '$', true);
INSERT INTO Period (id, companyId, name, ending_month, report_date) VALUES ('550e8400-e29b-41d4-a716-446655440000', '9c858901-8a57-4791-81fe-4c455b099bc9', '21Q1', '2501', '2025-02-15');


-- for ResearchEndpointsTest.get test
INSERT INTO Company (id, ticker, currency, watching) VALUES ('f47ac10b-58cc-4372-a567-0e02b2c3d479', 'RCH', '$', true);
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, cogs, op_exp, net_income, dividend, shares) VALUES ('9b37ef3c-3df6-4d1d-8c65-4a1e41a64b8f', 'f47ac10b-58cc-4372-a567-0e02b2c3d479', '24Q4', '2501', '2025-02-15', '1000', '500', '300', '80', '20', '100');
INSERT INTO Period (id, companyId, name, ending_month) VALUES ('e0c17a5a-f27f-4b82-9a42-7d8e5b6c3a72', 'f47ac10b-58cc-4372-a567-0e02b2c3d479', '25Q1', '2504');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, cogs, op_exp, net_income, dividend, shares) VALUES ('3f9a40e1-6b0b-44c5-9db3-6c75c3a3d13f', 'f47ac10b-58cc-4372-a567-0e02b2c3d479', '24Q3', '2410', '2024-11-15', '500', '400', '50', '0', '10', '90');
INSERT INTO Latest (id, companyId, datetime, price) VALUES ('9b3a4e42-84b3-4e3a-b60e-fb019b09b5ef', 'f47ac10b-58cc-4372-a567-0e02b2c3d479', '2025-10-27 14:35:00', '1234')

