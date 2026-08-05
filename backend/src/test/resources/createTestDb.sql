
INSERT INTO Company (id, ticker, currency) VALUES (2213, 'XXX', 'K');
INSERT INTO Company (id, ticker, currency) VALUES (1041, 'YYY', 'K');
INSERT INTO Company (id, ticker, currency) VALUES (1579, 'ZZZ', '$');
INSERT INTO Company (id, ticker, currency, sector) VALUES (1927, 'NVDA', '$',  'SEMICONDUCTORS');
INSERT INTO Company (id, ticker, currency, sector) VALUES (1425, 'SHELL', '€', 'ENERGY_MINERALS');
INSERT INTO Company (id, ticker, currency) VALUES (2229, 'RR', '£');
INSERT INTO Company (id, ticker, currency) VALUES (1531, 'CEZ', 'K');
INSERT INTO Company (id, ticker, currency) VALUES (1557, 'ABCD', '$');
INSERT INTO Company (id, ticker, currency) VALUES (2150, 'XRC', '$');
INSERT INTO Company (id, ticker, currency) VALUES (1501, 'XRSA', '$');
INSERT INTO Company (id, ticker, currency) VALUES (1658, 'XRSB', '$');
INSERT INTO Company (id, ticker, currency, sector) VALUES (1492, 'XCW', '$',  'ELECTRIC_VEHICLES');
INSERT INTO Company (id, ticker, currency) VALUES (1173, 'XTC', '$');
INSERT INTO Company (id, ticker, currency) VALUES (2050, 'XTS', '$');
INSERT INTO Company (id, ticker, currency) VALUES (2249, 'XRL', '$');
INSERT INTO Company (id, ticker, currency) VALUES (1562, 'XFC', '$');
INSERT INTO Company (id, ticker, currency) VALUES (1563, 'XFQ', '$');


INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES (5, 2213, '10', '2018-04-05', '0', '0', '2018-05-05', '10', '5');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES (6, 1041, '10', '2018-04-05', '10', '50', '2018-05-05', '0', '0');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES (1, 1927, '5', '2023-11-11', '400.5', '14.5', '2024-01-05', '500', '50');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES (2, 1425, '100', '2021-05-10', '20.1', '18', '2023-12-31', '30.4', '30.5');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES (3, 2229, '10', '2022-11-01', '200000', '25');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES (4, 1531, '1150.1234', '2023-04-05', '500.25', '250.12');

INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES (1930, 1501, '10', '2021-01-05', '90', '10');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES (2147, 1658, '20', '2020-12-30', '180', '10');

INSERT INTO Record (id, companyId, date, title, price) VALUES (1, 1927, '2023-11-11', 'bought 5@400.5$', '400.5');
INSERT INTO Record (id, companyId, date, title, price) VALUES (2, 1927, '2024-01-05', 'sold 5@500$', '500');
INSERT INTO Record (id, companyId, date, title, price) VALUES (3, 1425, '2021-05-10', 'bought 100@20.1€', '400.5');
INSERT INTO Record (id, companyId, date, title, price) VALUES (4, 1425, '2023-07-10', 'sold 100@30.4€', '30.4');
INSERT INTO Record (id, companyId, date, title, price) VALUES (5, 2229, '2022-11-01', 'bought 10@200£', '400.5');
INSERT INTO Record (id, companyId, date, title, price) VALUES (6, 1531, '2021-04-05', 'bought 1150.1234@500500.25K', '400.5');
INSERT INTO Record (id, companyId, date, title, price, p_net) VALUES (1231, 1557, '2021-04-05', 'xxx', '100', '10.1');

INSERT INTO Record (id, companyId, date, title, price, strategy) VALUES (1332, 1501, '2021-04-05', '2021 strategy of A', '100', 'strat of A');
INSERT INTO Record (id, companyId, date, title, price, strategy) VALUES (1019, 1658, '2020-12-20', '2020 strategy of B', '200', 'strat of B');

INSERT INTO Record (id, companyId, date, title, price) VALUES (2332, 2249, '2022-11-01', 'latest price', '100');
INSERT INTO Record (id, companyId, date, title, price, p_net) VALUES (1939, 2249, '2022-10-01', 'latest pe', '90', '5');
INSERT INTO Record (id, companyId, date, title, price, p_net, p_rev) VALUES (1577, 2249, '2022-09-01', 'latest ps', '80', '5.5', '4');
INSERT INTO Record (id, companyId, date, title, price, p_rev, dy) VALUES (1559, 2249, '2022-08-01', 'latest dy', '110', '3', '1.5');
INSERT INTO Record (id, companyId, date, title, price, dy, targets) VALUES (2101, 2249, '2022-07-01', 'latest targets', '90', '1', '10-5~7');
INSERT INTO Record (id, companyId, date, title, price, targets, strategy) VALUES (1045, 2249, '2022-06-01', 'latest strategy', '70', '8-4~6', 'strat');

INSERT INTO Record (id, companyId, date, title, price) VALUES (1095, 1562, '2023-07-01', 'latest price', '60');
INSERT INTO Record (id, companyId, date, title, price) VALUES (1096, 1563, '2023-07-01', 'latest price', '60');


INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES (2024, 1927, '2021-06-01', '70', '7');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES (1195, 1927, '2022-12-01', '80', '8');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES (1775, 1531, '2021-12-01', '1000', '100');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES (1620, 1557, '2020-12-01', '1000', '100');


INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend) VALUES (1, 1927, '24Q4', '2501', '2025-02-15', '1000', '500', '300', '80', '20');
INSERT INTO Period (id, companyId, name, ending_month) VALUES (2, 1927, '25Q1', '2504');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend) VALUES (3, 1927, '24Q3', '2410', '2024-11-15', '500', '400', '50', '0', '10');

INSERT INTO Period (id, companyId, name, ending_month, report_date) VALUES (1839, 1041, '22Q2', '2501', '2025-02-15');

INSERT INTO Estimate (id, periodId, datetime, current, next1, next2) VALUES (1, 1, '2026-08-01 12:30:00', '11.50', '12.75', '14.00');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2, next3) VALUES (2, 2, '2026-08-01 13:30:00', '21.50', '22.75', '24.00', '25.25');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2, next3) VALUES (3, 1, '2026-08-02 12:30:00', '31.50', '32.75', '34.00', '35.25');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2, next3) VALUES (5, 1, '2026-08-02 12:30:00', '41.50', '42.75', '44.00', '45.25');


-- for rest.*EndpointsTest.create tests
INSERT INTO Company (id, ticker, currency) VALUES (1565, 'CRE', '$');
INSERT INTO Company (id, ticker, currency) VALUES (1564, 'IMP', '$');

-- for rest.*EndpointsTest.createInvalidValues tests
INSERT INTO Company (id, ticker, currency) VALUES (2287, 'CINV', '$');

-- for rest.*EndpointsTests.update tests
INSERT INTO Company (id, ticker, currency, sector) VALUES (1842, 'UPD', '$',  'ELECTRIC_VEHICLES');
INSERT INTO Period (id, companyId, name, ending_month, report_date) VALUES (1467, 1842, '21Q1', '2501', '2025-02-15');
INSERT INTO Period (id, companyId, name, ending_month) VALUES (1466, 1842, '21Q2', '2503');
INSERT INTO Record (id, companyId, date, title, price, p_net) VALUES (1974, 1842, '2021-04-05', 'xxx', '100', '10.1');

-- for rest.*EndpointsTests.updateInvalidValues tests
INSERT INTO Company (id, ticker, currency) VALUES (1581, 'UINV', '$');
INSERT INTO Period (id, companyId, name, ending_month, report_date) VALUES (2042, 1581, '21Q1', '2501', '2025-02-15');
INSERT INTO Record (id, companyId, date, title, price, p_net) VALUES (1317, 1581, '2021-04-05', 'xxx', '100', '10.1');

-- for rest.*EndpointsTests.delete tests
INSERT INTO Company (id, ticker, currency) VALUES (1692, 'DEL', '$');
INSERT INTO Record (id, companyId, date, title, price, p_net) VALUES (1916, 1692, '2021-04-05', 'xxx', '100', '10.1');

-- for rest.*EndpointsTests.deleteInvalidValues tests
INSERT INTO Company (id, ticker, currency) VALUES (1490, 'DINV', '$');
INSERT INTO Record (id, companyId, date, title, price, p_net) VALUES (1840, 1490, '2021-04-05', 'xxx', '100', '10.1');

-- for ResearchEndpointsTest.get test
INSERT INTO Company (id, ticker, currency) VALUES (2281, 'RCH', '$');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares) VALUES (1837, 2281, '24Q4', '2501', '2025-02-15', '1000', '500', '300', '80', '20', '1.25', '100');
INSERT INTO Period (id, companyId, name, ending_month) VALUES (2182, 2281, '25Q1', '2504');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares) VALUES (1338, 2281, '24Q3', '2410', '2024-11-15', '500', '400', '50', '0', '10', '90');
INSERT INTO Estimate (id, periodId, datetime, current, next1) VALUES (4, 2182, '2026-08-02 14:30:00', '1.62', '1.85');
INSERT INTO Latest (id, companyId, datetime, price) VALUES (1838, 2281, '2025-10-27 14:35:00', '1234');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES (2095, 2281, '10', '2021-01-05', '90', '10');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES (2177, 2281, '20', '2020-12-30', '180', '10');
INSERT INTO Record (id, companyId, date, title, price, p_rev, p_gross, p_oper, p_net, dy, asset_quantity, asset_price) VALUES (1756, 2281, '2021-04-05', 'xxx', '125', '10', '50', '100', '123', '10.12', '456', '75');
INSERT INTO Record (id, companyId, date, title, price, p_net) VALUES (1517, 2281, '2022-01-02', 'yyy', '100', '10.1');

-- for TradeEndpointsTest.sell test
INSERT INTO Company (id, ticker, currency) VALUES (1209, 'SELL', '$');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES (2335, 1209, '1', '2020-03-15', '400', '10');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES (1788, 1209, '5', '2020-04-05', '450', '10');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES (1130, 1209, '7.5', '2020-05-01', '500', '10');
