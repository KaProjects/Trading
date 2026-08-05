INSERT INTO Company (id, ticker, currency, sector) VALUES (2213, 'NVDA', '$', 'HARDWARE');
INSERT INTO Company (id, ticker, currency, sector) VALUES (2212, 'AMD', '$', 'HARDWARE');
INSERT INTO Company (id, ticker, currency, sector) VALUES (2214, 'INTC', '$', 'HARDWARE');
INSERT INTO Company (id, ticker, currency, sector) VALUES (2073, 'AA', '€', 'SOFTWARE');
INSERT INTO Company (id, ticker, currency, sector) VALUES (1873, 'BBC', '$', 'FINANCE');
INSERT INTO Company (id, ticker, currency, sector) VALUES (1240, 'CX', 'K', 'ETF');
INSERT INTO Company (id, ticker, currency, sector) VALUES (1504, 'DDD', '$', 'COMMUNICATION_SERVICES');
INSERT INTO Company (id, ticker, currency, sector) VALUES (2261, 'EFG', '£', 'HEALTH_TECH');
INSERT INTO Company (id, ticker, currency, sector) VALUES (2300, 'NTRD', '$', 'SOFTWARE');


-- Reusable custom company lists. Each development company belongs to two or three tags.
INSERT INTO Tag (companyId, value) VALUES (2213, 'growth');
INSERT INTO Tag (companyId, value) VALUES (2213, 'watchlist');
INSERT INTO Tag (companyId, value) VALUES (2212, 'growth');
INSERT INTO Tag (companyId, value) VALUES (2212, 'turnaround');
INSERT INTO Tag (companyId, value) VALUES (2212, 'watchlist');
INSERT INTO Tag (companyId, value) VALUES (2214, 'value');
INSERT INTO Tag (companyId, value) VALUES (2214, 'turnaround');
INSERT INTO Tag (companyId, value) VALUES (2214, 'watchlist');
INSERT INTO Tag (companyId, value) VALUES (2073, 'growth');
INSERT INTO Tag (companyId, value) VALUES (2073, 'watchlist');
INSERT INTO Tag (companyId, value) VALUES (1873, 'income');
INSERT INTO Tag (companyId, value) VALUES (1873, 'value');
INSERT INTO Tag (companyId, value) VALUES (1240, 'income');
INSERT INTO Tag (companyId, value) VALUES (1240, 'value');
INSERT INTO Tag (companyId, value) VALUES (1240, 'watchlist');
INSERT INTO Tag (companyId, value) VALUES (1504, 'growth');
INSERT INTO Tag (companyId, value) VALUES (1504, 'turnaround');
INSERT INTO Tag (companyId, value) VALUES (2261, 'growth');
INSERT INTO Tag (companyId, value) VALUES (2261, 'watchlist');
INSERT INTO Tag (companyId, value) VALUES (2300, 'growth');
INSERT INTO Tag (companyId, value) VALUES (2300, 'turnaround');
INSERT INTO Tag (companyId, value) VALUES (2300, 'watchlist');


INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (2068, 2213, '10', '2018-04-05', '0', '0', '2018-05-05', '10', '5', 'PATRIA_STANDARD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES (1145, 2213, '10', '2018-04-05', '10', '50', '2018-06-05', '0', '0');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (2276, 2213, '5', '2023-11-11', '400.5', '14.5', '2024-01-05', '500', '50', 'PATRIA_MARGIN');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES (1048, 2213, '100', '2021-05-10', '20.1', '18', '2023-12-31', '30.4', '30.5');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, portfolio) VALUES (1705, 2213, '10', '2018-04-05', '100', '25', 'PATRIA_DIP');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES (2191, 2213, '123', '2018-04-05', '500.25', '250.12');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, portfolio) VALUES (2293, 2213, '400', '2022-11-01', '1000', '25', 'REVOLUT_STANDARD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES (1870, 2213, '1150.1234', '2023-04-05', '300.25', '250.12');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (2067, 2073, '10', '2018-04-05', '0', '0', '2018-05-05', '10', '5', 'REVOLUT_CFD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES (1874, 2214, '1150.1234', '2025-04-15', '300.25', '250.12');


INSERT INTO Record (id, companyId, date, price, content, review, strategy, retro, targets, p_rev, p_gross, p_oper, p_net, dy, asset_quantity, asset_price) VALUES (1871, 2213, '2023-12-11', '400.5', '[{"type":"paragraph","children":[{"text":"Started a tracking position as accelerated-computing demand moved from an early thesis into reported growth."}]}]', '[{"type":"paragraph","children":[{"text":"The entry captured the long-term opportunity, but the valuation already required strong execution."}]}]', '[{"type":"paragraph","children":[{"text":"Keep this as a small discovery position and add only after validating data-center growth and supply availability."}]}]', '[{"type":"paragraph","children":[{"text":"The position was useful for following the thesis, although the initial notes should have defined clearer add levels."}]}]', '~100', '1', '2', '3', '4', '5', '10', '20');
INSERT INTO Record (id, companyId, date, price, content, review, strategy, retro) VALUES (2066, 2213, '2023-11-11', '400.5', '[{"type":"paragraph","children":[{"text":"Opened the position while hyperscaler AI infrastructure spending continued to expand."}]}]', '[{"type":"paragraph","children":[{"text":"Demand signals were constructive, but customer concentration and export restrictions remained important risks."}]}]', '[{"type":"paragraph","children":[{"text":"Use earnings revisions and gross-margin durability as the main checkpoints before increasing exposure."}]}]', '[{"type":"paragraph","children":[{"text":"The thesis was directionally correct; the record would have benefited from explicit valuation ranges."}]}]');
INSERT INTO Record (id, companyId, date, price, content, review, strategy, retro) VALUES (2225, 2213, '2023-10-11', '400.5', '[{"type":"paragraph","children":[{"text":"Documented the first NVIDIA entry around the transition from gaming-led results to data-center-led growth."}]}]', '[{"type":"paragraph","children":[{"text":"The product advantage was visible, while visibility beyond the first infrastructure buildout remained limited."}]}]', '[{"type":"paragraph","children":[{"text":"Track networking attach rates, software adoption, and competition rather than relying only on GPU unit demand."}]}]', '[{"type":"paragraph","children":[{"text":"Early research focused too much on revenue and not enough on cash conversion and ecosystem strength."}]}]');
INSERT INTO Record (id, companyId, date, price, content, review, strategy, retro) VALUES (2338, 2212, '2025-05-11', '400.5', '[{"type":"paragraph","children":[{"text":"Reviewed EPYC and accelerator adoption as AMD broadened its data-center portfolio."}]}]', '[{"type":"paragraph","children":[{"text":"Server share gains continued, though accelerator execution and software maturity still needed proof."}]}]', '[{"type":"paragraph","children":[{"text":"Maintain a measured position until product launches translate into sustained margin expansion."}]}]', '[{"type":"paragraph","children":[{"text":"The earlier review underestimated how long customer qualification cycles could take."}]}]');


INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES (1004, 2213, '24Q4', '2501', '2025-02-15', '1200', '720', '480', '240', '30', '1000', '120', '80');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares) VALUES (1420, 2213, '24Q3', '2410', '2024-11-15', '1000', '500', '300', '80', '20', '1000');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend) VALUES (2278, 2213, '24Q2', '2407', '2024-08-15', '800', '400', '200', '0', '10');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend) VALUES (2303, 2213, '24Q1', '2404', '2024-02-15', '1234', '456', '123', '11', '7');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend) VALUES (1750, 2213, '23Q4', '2401', '2024-02-01', '900', '450', '240', '120', '15');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2337, 2212, '25Q3', '2510', '2025-11-04', '1850', '960', '410', '260', '0', '0.75', '1250', '185', '132');


INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES (2024, 2213, '2021-06-01', '70', '7');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES (1195, 2213, '2022-12-01', '80', '8');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES (1775, 2213, '2021-12-01', '1000', '100');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES (1620, 2213, '2020-12-01', '1000', '100');


-- Recent active and closed trades for selector, aggregate, and asset scenarios.
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, portfolio) VALUES (1068, 2213, '20', '2026-06-20', '180.25', '4.95', 'PATRIA_STANDARD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES (1069, 2212, '30', '2026-02-10', '110.25', '4.95');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (1070, 2212, '15', '2025-08-12', '145.50', '4.95', '2026-02-20', '132.25', '4.95', 'PATRIA_MARGIN');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES (1071, 2214, '50', '2026-05-12', '22.80', '3.95');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (1072, 2214, '40', '2025-10-03', '24.10', '3.95', '2026-04-16', '20.30', '3.95', 'PATRIA_DIP');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES (1073, 2073, '12', '2026-01-20', '82.40', '3.50');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (1074, 2073, '20', '2025-06-02', '71', '4.20', '2026-03-18', '88.40', '4.20', 'REVOLUT_STANDARD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees) VALUES (1075, 1873, '8', '2026-06-05', '155.75', '5.25');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, portfolio) VALUES (1076, 1240, '25', '2025-12-01', '1850', '99', 'REVOLUT_CFD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees) VALUES (1077, 1504, '100', '2025-09-11', '42.50', '8.50', '2026-05-14', '54', '8.50');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, portfolio) VALUES (1078, 2261, '18', '2026-03-06', '96.20', '6.25', 'PATRIA_STANDARD');


-- Records cover complete, partial, and title-less editor states.
INSERT INTO Record (id, companyId, date, price, content, review, strategy, retro, targets, p_rev, p_gross, p_oper, p_net, dy, asset_quantity, asset_price) VALUES (1164, 2213, '2026-07-20', '173.50', '[{"type":"paragraph","children":[{"text":"AI infrastructure demand remains strong and the data-center backlog supports the base case."}]},{"type":"paragraph","children":[{"text":"Networking, inference, and enterprise adoption provide additional growth paths beyond the initial hyperscaler buildout."}]},{"type":"paragraph","children":[{"text":"Key risks are customer concentration, export controls, custom silicon, and expectations already embedded in the valuation."}]}]', '[{"type":"paragraph","children":[{"text":"Revenue, gross margin, and cash generation remained ahead of the original plan while execution stayed consistent."}]},{"type":"paragraph","children":[{"text":"The strongest evidence was broad demand across compute and networking; the weakest point was limited visibility into normalized infrastructure spending."}]}]', '[{"type":"paragraph","children":[{"text":"Keep the core position, add only below the lower target, and cap total exposure despite positive estimate revisions."}]},{"type":"paragraph","children":[{"text":"Reassess if gross margin compresses without a product-transition explanation or if forward earnings stop rising."}]}]', '[{"type":"paragraph","children":[{"text":"The previous entry was early but the thesis remained intact and position sizing prevented valuation risk from dominating the result."}]},{"type":"paragraph","children":[{"text":"Future records should separate business momentum from multiple expansion and define exit conditions before the report."}]}]', '165-210', '18.40', '30.10', '34.50', '38.20', '0.04', '20', '180.25');
INSERT INTO Record (id, companyId, date, price, content, review, strategy, retro, targets, p_rev, p_net, asset_quantity, asset_price) VALUES (1165, 2212, '2026-06-30', '158.25', '[{"type":"paragraph","children":[{"text":"Server demand improved and the CPU roadmap remains competitive across cloud and enterprise workloads."}]},{"type":"paragraph","children":[{"text":"Accelerator revenue adds meaningful optionality, but software adoption and customer concentration remain the central uncertainties."}]},{"type":"paragraph","children":[{"text":"The current valuation assumes continued share gains and improving data-center mix."}]}]', '[{"type":"paragraph","children":[{"text":"EPYC execution remained dependable and client demand recovered, while accelerator progress was slower and less visible than the optimistic case."}]},{"type":"paragraph","children":[{"text":"Gross-margin improvement supports the thesis, but operating expenses must scale more slowly than revenue."}]}]', '[{"type":"paragraph","children":[{"text":"Hold the core position and reassess after each data-center report rather than trading short-term product headlines."}]},{"type":"paragraph","children":[{"text":"Add below the lower target only when server share, accelerator backlog, and forward earnings remain intact."}]}]', '[{"type":"paragraph","children":[{"text":"Earlier expectations gave too much weight to headline accelerator TAM and too little to software readiness and deployment timing."}]},{"type":"paragraph","children":[{"text":"The disciplined entry price helped offset slower realization of the accelerator thesis."}]}]', '145-190', '8.70', '31.40', '30', '110.25');
INSERT INTO Record (id, companyId, date, price, content, review, strategy, retro, p_rev, p_gross, p_oper, p_net, asset_quantity, asset_price) VALUES (1166, 2214, '2026-06-18', '21.50', '[{"type":"paragraph","children":[{"text":"Intel is a restructuring and manufacturing-execution thesis rather than a simple semiconductor-cycle recovery."}]},{"type":"paragraph","children":[{"text":"Revenue stabilization is encouraging, but foundry utilization, product competitiveness, and capital intensity determine the long-term outcome."}]},{"type":"paragraph","children":[{"text":"The balance between government support, partner commitments, and continued cash consumption remains the primary risk."}]}]', '[{"type":"paragraph","children":[{"text":"Revenue stabilized, but operating leverage and free cash flow remained weak relative to the capital invested."}]},{"type":"paragraph","children":[{"text":"Product delivery improved incrementally, while foundry economics still lacked enough external proof."}]}]', '[{"type":"paragraph","children":[{"text":"Treat the position as a small turnaround allocation and require measurable execution before increasing exposure."}]},{"type":"paragraph","children":[{"text":"The next checkpoints are external foundry customers, gross-margin recovery, manufacturing milestones, and lower cash burn."}]}]', '[{"type":"paragraph","children":[{"text":"Previous additions relied too heavily on valuation mean reversion before the operating evidence was available."}]},{"type":"paragraph","children":[{"text":"Waiting for confirmation would have reduced time risk even if it meant accepting a moderately higher entry price."}]}]', '2.10', '4.60', '18.20', '35.10', '1200.1234', '288.69');
INSERT INTO Record (id, companyId, date, title, price, content, targets, dy, asset_quantity, asset_price) VALUES (1167, 2073, '2026-04-01', 'Post-sale review', '90', '[{"type":"paragraph","children":[{"text":"The valuation reached the planned exit range."}]}]', '78-95', '1.25', '12', '82.40');
INSERT INTO Record (id, companyId, date, price, content, strategy, targets, p_net, dy, asset_quantity, asset_price) VALUES (1168, 1873, '2026-06-06', '160', '[{"type":"paragraph","children":[{"text":"Credit quality remains healthy and capital returns are improving."}]}]', '[{"type":"paragraph","children":[{"text":"Keep as a diversified financial holding."}]}]', '145-185', '12.80', '2.10', '8', '155.75');
INSERT INTO Record (id, companyId, date, title, price, content, review, targets, p_rev, p_net, dy, asset_quantity, asset_price) VALUES (1169, 2261, '2026-07-01', 'Pipeline update', '102.30', '[{"type":"paragraph","children":[{"text":"The clinical pipeline progressed without material delays."}]}]', '[{"type":"paragraph","children":[{"text":"Risk remains concentrated in the next trial readout."}]}]', '88-125', '6.20', '24.50', '1.80', '18', '96.20');
INSERT INTO Record (id, companyId, date, price, content, review, strategy, retro, targets) VALUES (1170, 2300, '2026-07-28', '48.20', '[{"type":"paragraph","children":[{"text":"The company is being researched as a potential holding, but no position has been opened."}]},{"type":"paragraph","children":[{"text":"Recurring revenue and customer retention support the thesis while the valuation still requires disciplined entry levels."}]}]', '[{"type":"paragraph","children":[{"text":"Recent execution was steady, although evidence of durable operating leverage is still limited."}]}]', '[{"type":"paragraph","children":[{"text":"Continue monitoring results and initiate a position only if growth quality remains intact within the target range."}]}]', '[{"type":"paragraph","children":[{"text":"This research-only record intentionally has no associated trades or dividends."}]}]', '42-55');


-- Research-only company: periods and a record, intentionally without trades or dividends.
INSERT INTO Period (id, companyId, name, ending_month, report_date, research) VALUES (2501, 2300, '26Q1', '2603', '2026-05-07', '[{"type":"paragraph","children":[{"text":"Initial research focused on recurring revenue, customer retention, and the path toward sustainable operating leverage."}]},{"type":"paragraph","children":[{"text":"The main risks are valuation compression, slower enterprise spending, and rising customer-acquisition costs."}]}]');
INSERT INTO Period (id, companyId, name, ending_month, research) VALUES (2502, 2300, '26Q2', '2606', '[{"type":"paragraph","children":[{"text":"Monitor the next report for stable retention, improving free cash flow, and evidence that growth is not dependent on higher sales spending."}]}]');


-- Quarterly financial history and adjusted EPS for NVDA and AMD.
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES (1251, 2213, '25Q1', '2504', '2025-05-28', '1380', '815', '535', '285', '32', '990', '155', '92');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (1252, 2213, '25Q2', '2507', '2025-08-27', '1540', '925', '625', '350', '34', '0.76', '980', '185', '95');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (1253, 2213, '25Q3', '2510', '2025-11-19', '1695', '1035', '710', '420', '36', '0.81', '970', '198', '145');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (1254, 2213, '25Q4', '2601', '2026-02-25', '1880', '1165', '810', '505', '38', '0.89', '960', '205', '132');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (1255, 2213, '26Q1', '2604', '2026-05-27', '2075', '1300', '925', '590', '40', '0.96', '950', '195', '150');

INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES (1256, 2212, '24Q4', '2501', '2025-02-04', '1320', '670', '270', '160', '0', '1290', '178', '118');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES (1257, 2212, '25Q1', '2504', '2025-05-06', '1450', '745', '305', '185', '0', '1280', '165', '76');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (1258, 2212, '25Q2', '2507', '2025-08-05', '1610', '835', '355', '220', '0', '0.54', '1270', '182', '120');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (1259, 2212, '25Q4', '2601', '2026-02-03', '1980', '1040', '455', '305', '0', '1.09', '1240', '205', '151');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (1260, 2212, '26Q1', '2604', '2026-05-05', '2180', '1160', '525', '365', '0', '1.16', '1225', '190', '135');
INSERT INTO Period (id, companyId, name, ending_month) VALUES (1268, 2212, '26Q2', '2607');

-- Saved analyst snapshots for AMD quarters, including shortened future horizons.
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2, next3) VALUES (1395, 1256, '2025-02-01 09:00:00', '0.95', '1.05', '1.15', '1.28');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2) VALUES (1396, 1257, '2025-05-01 09:00:00', '1.10', '1.18', '1.30');
INSERT INTO Estimate (id, periodId, datetime, current, next1) VALUES (1397, 1258, '2025-08-01 09:00:00', '1.22', '1.34');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2, next3) VALUES (1398, 2337, '2025-11-01 09:00:00', '1.30', '1.42', '1.55', '1.70');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2, next3) VALUES (1399, 1259, '2026-02-01 09:00:00', '1.40', '1.52', '1.68', '1.82');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2) VALUES (1400, 1260, '2026-05-01 09:00:00', '1.50', '1.65', '1.82');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2, next3) VALUES (1401, 1268, '2026-07-20 09:00:00', '1.44', '1.82', '2.55', '2.70');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2, next3) VALUES (1402, 1268, '2026-08-01 18:30:00', '1.62', '1.85', '2.64', '2.76');


-- Intel quarterly sequence: only the latest period remains unreported.
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2410, 2214, '25Q1', '2503', '2025-04-24', '12667', '4672', '-301', '-821', '0', '0.13', '4343', '27', '18');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2401, 2214, '25Q2', '2506', '2025-07-24', '12860', '4780', '-630', '-590', '130', '-0.02', '4210', '26', '19');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2402, 2214, '25Q3', '2509', '2025-10-23', '13300', '5200', '100', '-80', '130', '0.23', '4180', '31', '20');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2403, 2214, '25Q4', '2512', '2026-01-22', '13800', '5500', '350', '190', '130', '0.13', '4160', '42', '22');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (1269, 2214, '26Q1', '2603', '2026-04-23', '13100', '5000', '-100', '-140', '130', '0.12', '4140', '38', '19');
INSERT INTO Period (id, companyId, name, ending_month) VALUES (2411, 2214, '26Q2', '2606');

-- Intel analyst snapshots exercise reported and unreported estimate states.
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2, next3) VALUES (1410, 2410, '2025-04-15 09:00:00', '-0.15', '-0.02', '0.18', '0.12');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2, next3) VALUES (1411, 2401, '2025-07-20 09:00:00', '-0.02', '0.20', '0.12', '0.15');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2, next3) VALUES (1412, 2402, '2025-10-20 09:00:00', '0.23', '0.14', '0.17', '0.20');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2, next3) VALUES (1413, 2403, '2026-01-18 09:00:00', '0.13', '0.12', '0.18', '0.23');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2, next3) VALUES (1414, 1269, '2026-04-18 09:00:00', '0.12', '0.17', '0.23', '0.28');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2, next3) VALUES (1415, 2411, '2026-07-20 09:00:00', '0.10', '0.19', '0.25', '0.31');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2, next3) VALUES (1416, 2411, '2026-08-03 09:00:00', '0.11', '0.20', '0.26', '0.32');

-- Half-year history exercises its separate growth rules.

INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES (1264, 2073, '24H1', '2406', '2024-08-01', '780', '430', '185', '105', '12', '420', '78', '54');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES (1265, 2073, '24H2', '2412', '2025-02-03', '840', '475', '205', '120', '14', '418', '84', '62');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES (1266, 2073, '25H1', '2506', '2025-08-04', '910', '520', '235', '145', '15', '415', '92', '68');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, shares, price_high, price_low) VALUES (1267, 2073, '25H2', '2512', '2026-02-02', '995', '580', '270', '170', '17', '410', '96', '73');


-- Multi-currency dividend history.
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES (1339, 2213, '2025-06-10', '12.50', '1.88');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES (1340, 2213, '2026-06-10', '16', '2.40');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES (1341, 2212, '2025-09-15', '9.75', '1.46');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES (1342, 2214, '2026-03-01', '25', '3.75');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES (1343, 2073, '2025-11-20', '18.40', '2.76');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES (1344, 1873, '2026-04-15', '32', '4.80');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES (1345, 1240, '2025-12-18', '1250', '187.50');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES (1346, 1504, '2026-02-12', '28.50', '4.28');
INSERT INTO Dividend (id, companyId, date, dividend, tax) VALUES (1347, 2261, '2025-10-08', '21.60', '3.24');


-- One fallback market price per company.
INSERT INTO Latest (id, companyId, datetime, price) VALUES (1429, 2213, '2026-07-24 16:00:00', '173.50');
INSERT INTO Latest (id, companyId, datetime, price) VALUES (1430, 2212, '2026-07-24 16:00:00', '158.25');
INSERT INTO Latest (id, companyId, datetime, price) VALUES (1431, 2214, '2026-07-24 16:00:00', '21.50');
INSERT INTO Latest (id, companyId, datetime, price) VALUES (1432, 2073, '2026-07-24 16:00:00', '90');
INSERT INTO Latest (id, companyId, datetime, price) VALUES (1433, 1873, '2026-07-24 16:00:00', '160');
INSERT INTO Latest (id, companyId, datetime, price) VALUES (1434, 1240, '2026-07-24 16:00:00', '1950');
INSERT INTO Latest (id, companyId, datetime, price) VALUES (1435, 1504, '2026-07-24 16:00:00', '56.80');
INSERT INTO Latest (id, companyId, datetime, price) VALUES (1436, 2261, '2026-07-24 16:00:00', '102.30');
