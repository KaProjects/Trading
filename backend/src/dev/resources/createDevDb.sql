INSERT INTO Company (id, ticker, exchangeCode, currency, sector, name, description, logoUrl, website)
VALUES (2213, 'NVDA', 'XNAS', '$', 'HARDWARE', 'NVIDIA Corporation',
        'NVIDIA designs accelerated computing platforms, graphics processors, networking products, and related software.',
        'https://upload.wikimedia.org/wikipedia/commons/thumb/a/a4/NVIDIA_logo.svg/330px-NVIDIA_logo.svg.png',
        'https://www.nvidia.com');
INSERT INTO Company (id, ticker, exchangeCode, currency, sector, name, description, logoUrl, website)
VALUES (2212, 'AMD', 'XNAS', '$', 'HARDWARE', 'Advanced Micro Devices, Inc.',
        'AMD designs high-performance and adaptive computing products.',
        'https://upload.wikimedia.org/wikipedia/commons/thumb/7/7c/AMD_Logo.svg/330px-AMD_Logo.svg.png',
        'https://www.amd.com');
INSERT INTO Company (id, ticker, exchangeCode, currency, sector, name, description, logoUrl, website)
VALUES (2214, 'INTC', 'XNAS', '$', 'HARDWARE', 'Intel Corporation',
        'Intel designs and manufactures semiconductor products and computing platforms.',
        'https://upload.wikimedia.org/wikipedia/commons/thumb/6/6a/Intel_logo_%282020%2C_dark_blue%29.svg/330px-Intel_logo_%282020%2C_dark_blue%29.svg.png',
        'https://www.intel.com');
INSERT INTO Company (id, ticker, alphaVantageTicker, exchangeCode, currency, sector)
VALUES (2301, 'ASML', 'ASML.AMS', 'XAMS', '€', 'HARDWARE');
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


-- Research to-dos are interleaved by creation date to exercise oldest-first ordering.
INSERT INTO Todo (content, createdAt, companyId) VALUES ('Review #NVDA Blackwell ramp and gross-margin outlook after the next results.', '2026-08-12 08:30:00', 2213);
INSERT INTO Todo (content, createdAt, companyId) VALUES ('Compare #AMD EPYC share gains with data-center revenue growth.', '2026-08-13 09:15:00', 2212);
INSERT INTO Todo (content, createdAt, companyId) VALUES ('Check #INTC foundry customer milestones and manufacturing roadmap execution.', '2026-08-14 10:00:00', 2214);
INSERT INTO Todo (content, createdAt, companyId) VALUES ('Revisit #NVDA networking growth and inference demand assumptions.', '2026-08-16 08:45:00', 2213);
INSERT INTO Todo (content, createdAt, companyId) VALUES ('Review #AMD accelerator adoption and ROCm ecosystem progress.', '2026-08-18 11:30:00', 2212);
INSERT INTO Todo (content, createdAt, companyId) VALUES ('Update #INTC capital-expenditure and free-cash-flow expectations.', '2026-08-19 13:20:00', 2214);
INSERT INTO Todo (content, createdAt, companyId) VALUES ('Refresh #NVDA valuation range using the latest earnings estimates.', '2026-08-21 14:10:00', 2213);
INSERT INTO Todo (content, createdAt, companyId) VALUES ('Reassess #AMD margins and product mix before updating the strategy.', '2026-08-22 15:00:00', 2212);
INSERT INTO Todo (content, createdAt, companyId) VALUES ('Review #INTC gross-margin recovery and external foundry commitments.', '2026-08-23 16:40:00', 2214);


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


-- Closed trade history for cumulative P/L chart and selector scenarios.
-- This early loss pushes the USD-normalized cumulative result below zero.
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3001, 2213, '10', '2018-05-07', '50', '5', '2018-05-20', '20', '5', 'PATRIA_STANDARD');

-- Thirty additional trades cover every development company and all portfolios.
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3002, 2212, '12', '2018-07-10', '25', '4.50', '2018-10-15', '31', '4.50', 'PATRIA_MARGIN');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3003, 2214, '20', '2018-11-05', '48', '4.95', '2019-02-20', '42', '4.95', 'PATRIA_DIP');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3004, 2301, '3', '2019-02-12', '155', '5.50', '2019-06-18', '180', '5.50', 'REVOLUT_STANDARD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3005, 2073, '18', '2019-07-08', '42', '4.50', '2019-11-12', '49', '4.50', 'REVOLUT_CFD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3006, 1873, '8', '2019-12-03', '72', '3', '2020-03-09', '68', '3', 'FIDELITY_ORCL');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3007, 1240, '30', '2020-03-16', '850', '10', '2020-07-22', '980', '10', 'PATRIA_STANDARD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3008, 1504, '25', '2020-08-04', '28', '5', '2020-11-30', '36', '5', 'PATRIA_MARGIN');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3009, 2261, '10', '2020-12-08', '64', '4', '2021-03-17', '58', '4', 'PATRIA_DIP');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3010, 2300, '40', '2021-04-12', '4.20', '3', '2021-07-14', '6.10', '3', 'REVOLUT_STANDARD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3011, 2213, '15', '2021-08-09', '52', '5', '2021-11-19', '61', '5', 'REVOLUT_CFD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3012, 2212, '18', '2021-12-13', '42', '4.50', '2022-03-24', '38', '4.50', 'FIDELITY_ORCL');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3013, 2214, '30', '2022-04-11', '50', '5.50', '2022-07-28', '57', '5.50', 'PATRIA_STANDARD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3014, 2301, '4', '2022-08-08', '210', '5', '2022-11-15', '238', '5', 'PATRIA_MARGIN');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3015, 2073, '12', '2022-12-05', '55', '4', '2023-03-21', '63', '4', 'PATRIA_DIP');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3016, 1873, '7', '2023-04-17', '88', '5', '2023-07-18', '103', '5', 'REVOLUT_STANDARD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3017, 1240, '20', '2023-08-07', '1150', '9', '2023-11-22', '1030', '9', 'REVOLUT_CFD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3018, 1504, '35', '2023-12-04', '31', '5', '2024-03-14', '44', '5', 'FIDELITY_ORCL');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3019, 2261, '14', '2024-03-25', '76', '5', '2024-06-20', '91', '5', 'PATRIA_STANDARD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3020, 2300, '60', '2024-07-08', '5.50', '4', '2024-09-25', '4.80', '4', 'PATRIA_MARGIN');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3021, 2213, '20', '2024-09-30', '95', '6.50', '2024-12-12', '120', '6.50', 'PATRIA_DIP');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3022, 2212, '25', '2024-12-16', '82', '7.50', '2025-02-26', '103', '7.50', 'REVOLUT_STANDARD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3023, 2214, '45', '2025-02-17', '62', '6.50', '2025-05-08', '49', '6.50', 'REVOLUT_CFD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3024, 2301, '5', '2025-04-14', '320', '6.50', '2025-07-17', '370', '6.50', 'FIDELITY_ORCL');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3025, 2073, '16', '2025-06-09', '68', '5.50', '2025-09-23', '80', '5.50', 'PATRIA_STANDARD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3026, 1873, '10', '2025-08-11', '110', '6.50', '2025-11-13', '127', '6.50', 'PATRIA_MARGIN');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3027, 1240, '18', '2025-10-06', '1400', '10', '2026-01-22', '1680', '10', 'PATRIA_DIP');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3028, 1504, '50', '2025-12-01', '39', '6.50', '2026-03-05', '52', '6.50', 'REVOLUT_STANDARD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3029, 2261, '12', '2026-01-05', '89', '6', '2026-03-31', '112', '6', 'REVOLUT_CFD');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3030, 2300, '75', '2026-02-09', '7.20', '5', '2026-05-28', '9.80', '5', 'FIDELITY_ORCL');
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees, sell_date, sell_price, sell_fees, portfolio) VALUES (3031, 2213, '30', '2026-04-06', '130', '7.50', '2026-07-30', '158', '7.50', 'PATRIA_STANDARD');

-- Temporary high-volume fixture for visually testing the P/L chart with 200 additional closed trades.
INSERT INTO Trade (id, companyId, quantity, purchase_date, purchase_price, purchase_fees,
                   sell_date, sell_price, sell_fees, portfolio)
SELECT 4000 + synthetic_trade.X,
       2213,
       1 + MOD(synthetic_trade.X, 12),
       DATEADD('DAY', synthetic_trade.X * 5, DATE '2020-01-01'),
       75 + MOD(synthetic_trade.X, 35),
       1.50,
       DATEADD('DAY', synthetic_trade.X * 5 + 20 + MOD(synthetic_trade.X, 30), DATE '2020-01-01'),
       CASE
           WHEN MOD(synthetic_trade.X, 4) = 0 THEN 65 + MOD(synthetic_trade.X, 25)
           WHEN MOD(synthetic_trade.X, 7) = 0 THEN 72 + MOD(synthetic_trade.X, 20)
           ELSE 82 + MOD(synthetic_trade.X, 45)
       END,
       1.50,
       CASE MOD(synthetic_trade.X, 6)
           WHEN 0 THEN 'FIDELITY_ORCL'
           WHEN 1 THEN 'PATRIA_STANDARD'
           WHEN 2 THEN 'PATRIA_MARGIN'
           WHEN 3 THEN 'PATRIA_DIP'
           WHEN 4 THEN 'REVOLUT_STANDARD'
           ELSE 'REVOLUT_CFD'
       END
FROM SYSTEM_RANGE(1, 200) AS synthetic_trade;


INSERT INTO Record (id, companyId, date, price, content, review, strategy, retro, targets, p_rev, p_gross, p_oper, p_net, dy, asset_quantity, asset_price) VALUES (1871, 2213, '2023-12-11', '400.5', '[{"type":"paragraph","children":[{"text":"Started a tracking position as accelerated-computing demand moved from an early thesis into reported growth."}]}]', '[{"type":"paragraph","children":[{"text":"The entry captured the long-term opportunity, but the valuation already required strong execution."}]}]', '[{"type":"paragraph","children":[{"text":"Keep this as a small discovery position and add only after validating data-center growth and supply availability."}]}]', '[{"type":"paragraph","children":[{"text":"The position was useful for following the thesis, although the initial notes should have defined clearer add levels."}]}]', '~100', '1', '2', '3', '4', '5', '10', '20');
INSERT INTO Record (id, companyId, date, price, content, review, strategy, retro) VALUES (2066, 2213, '2023-11-11', '400.5', '[{"type":"paragraph","children":[{"text":"Opened the position while hyperscaler AI infrastructure spending continued to expand."}]}]', '[{"type":"paragraph","children":[{"text":"Demand signals were constructive, but customer concentration and export restrictions remained important risks."}]}]', '[{"type":"paragraph","children":[{"text":"Use earnings revisions and gross-margin durability as the main checkpoints before increasing exposure."}]}]', '[{"type":"paragraph","children":[{"text":"The thesis was directionally correct; the record would have benefited from explicit valuation ranges."}]}]');
INSERT INTO Record (id, companyId, date, price, content, review, strategy, retro) VALUES (2225, 2213, '2023-10-11', '400.5', '[{"type":"paragraph","children":[{"text":"Documented the first NVIDIA entry around the transition from gaming-led results to data-center-led growth."}]}]', '[{"type":"paragraph","children":[{"text":"The product advantage was visible, while visibility beyond the first infrastructure buildout remained limited."}]}]', '[{"type":"paragraph","children":[{"text":"Track networking attach rates, software adoption, and competition rather than relying only on GPU unit demand."}]}]', '[{"type":"paragraph","children":[{"text":"Early research focused too much on revenue and not enough on cash conversion and ecosystem strength."}]}]');
INSERT INTO Record (id, companyId, date, price, content, review, strategy, retro) VALUES (2338, 2212, '2025-05-11', '400.5', '[{"type":"paragraph","children":[{"text":"Reviewed EPYC and accelerator adoption as AMD broadened its data-center portfolio."}]}]', '[{"type":"paragraph","children":[{"text":"Server share gains continued, though accelerator execution and software maturity still needed proof."}]}]', '[{"type":"paragraph","children":[{"text":"Maintain a measured position until product launches translate into sustained margin expansion."}]}]', '[{"type":"paragraph","children":[{"text":"The earlier review underestimated how long customer qualification cycles could take."}]}]');


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


-- NVDA periods mirror the downloaded production Firebase Gemini snapshot.
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2601, 2213, '26Q1', '2504', '2025-05-28', '44062', '26668', '21638', '18775', '243', '0.76', '24300', '136.33', '104.47');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2602, 2213, '26Q2', '2507', '2025-08-27', '46743', '33842', '28440', '26422', '243', '1.05', '24300', '184.45', '135.37');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2603, 2213, '26Q3', '2510', '2025-11-19', '57006', '41849', '36010', '31910', '243', '1.30', '24300', '212.19', '173.12');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2604, 2213, '26Q4', '2601', '2026-02-25', '68127', '51093', '44299', '42960', '243', '1.62', '24300', '195.98', '174.63');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2605, 2213, '27Q1', '2604', '2026-05-20', '81615', '61157', '53536', '58321', '244', '1.87', '24391', '236.54', '165.17');
INSERT INTO Period (id, companyId, name, ending_month, report_date) VALUES (2606, 2213, '27Q2', '2707', '2026-08-26');

-- AMD periods mirror the downloaded production Firebase Gemini snapshot.
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2610, 2212, '25Q1', '2503', '2025-05-06', '7438', '3736', '806', '709', '0', '0.44', '1620', '122.52', '90.37');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2611, 2212, '25Q2', '2506', '2025-08-05', '7685', '3074', '-134', '872', '0', '0.54', '1610', '186.65', '99.27');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2612, 2212, '25Q3', '2509', '2025-11-04', '9246', '4780', '1300', '1200', '0', '0.75', '1610', '262.80', '157.80');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2613, 2212, '25Q4', '2512', '2026-02-03', '10270', '5577', '1752', '1511', '0', '0.92', '1648', '266.96', '194.28');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2614, 2212, '26Q1', '2603', '2026-05-05', '10253', '5434', '1476', '1383', '0', '0.84', '1653', '362.79', '189.02');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2615, 2212, '26Q2', '2606', '2026-08-04', '11536', '6203', '1990', '2297', '0', '1.38', '1664', '584.73', '401.08');
INSERT INTO Period (id, companyId, name, ending_month) VALUES (2616, 2212, '26Q3', '2609');

-- INTC periods mirror the downloaded production Firebase Gemini snapshot.
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2620, 2214, '25Q2', '2506', '2025-07-24', '12859', '3536', '-3200', '-2918', '0', '-0.67', '4377', '44.00', '20.70');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2621, 2214, '25Q3', '2509', '2025-10-23', '13653', '5220', '683', '4063', '0', '0.90', '4514', '38.28', '18.96');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2622, 2214, '25Q4', '2512', '2026-01-22', '13674', '4936', '-500', '-591', '0', '-0.12', '4925', '54.41', '38.16');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2623, 2214, '26Q1', '2603', '2026-04-23', '13577', '5350', '-3140', '-3730', '0', '-0.73', '5083', '70.33', '40.63');
INSERT INTO Period (id, companyId, name, ending_month, report_date, revenue, gross_profit, oper_income, net_income, dividend, adjusted_eps, shares, price_high, price_low) VALUES (2624, 2214, '26Q2', '2606', '2026-07-23', '16128', '6509', '1796', '-11033', '0', '-2.16', '5104', '142.35', '79.62');
INSERT INTO Period (id, companyId, name, ending_month) VALUES (2625, 2214, '26Q3', '2609');

-- Estimate snapshots use EPS projections from the same Firebase copy.
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2, next3) VALUES (1395, 2606, '2026-05-28 09:00:00', '2.12', '2.40', '2.72', '2.92');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2, next3) VALUES (1396, 2615, '2026-05-12 09:00:00', '1.62', '1.85', '2.64', '2.76');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2) VALUES (1397, 2616, '2026-06-18 09:00:00', '1.85', '2.64', '2.76');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2) VALUES (1398, 2616, '2026-08-11 09:00:00', '1.96', '2.64', '3.01');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2, next3) VALUES (1399, 2624, '2026-04-30 09:00:00', '0.20', '0.27', '0.31', '0.28');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2) VALUES (1400, 2625, '2026-06-18 09:00:00', '0.27', '0.31', '0.28');
INSERT INTO Estimate (id, periodId, datetime, current, next1, next2) VALUES (1401, 2625, '2026-08-11 09:00:00', '0.39', '0.43', '0.40');

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


-- Persisted targets use identities present in the downloaded Firebase copy.
INSERT INTO Target (periodId, date, institution, price, rating, overview, takeaway1) VALUES (2606, '2026-08-03', 'Bernstein', '315', 'buy', 'Recent target data exercises local persistence and Firebase sync de-duplication.', 'Compare the target range with forward earnings revisions.');
INSERT INTO Target (periodId, date, institution, price, rating) VALUES (2606, '2026-08-10', 'Morgan Stanley', '288', 'buy');
INSERT INTO Target (periodId, date, institution, price, rating, overview, takeaway1) VALUES (2606, '2026-08-20', 'RBC Capital', '300', 'Outperform', 'Accelerated computing demand remains the central valuation driver.', 'Watch customer concentration and export restrictions.');
INSERT INTO Target (periodId, date, institution, price, rating) VALUES (2606, '2026-08-24', 'Wedbush', '330', 'Outperform');
INSERT INTO Target (periodId, date, institution, price, rating) VALUES (2606, '2026-08-25', 'Raymond James', '352', 'Strong Buy');

INSERT INTO Target (periodId, date, institution, price, rating, overview, takeaway1) VALUES (2615, '2026-07-23', 'Benchmark', '685', 'Buy', 'Server share gains and accelerator execution support the target.', 'Track data-center growth and software adoption.');
INSERT INTO Target (periodId, date, institution, price) VALUES (2615, '2026-07-24', 'UBS', '730');
INSERT INTO Target (periodId, date, institution, price, rating) VALUES (2616, '2026-08-04', 'Benchmark', '685', 'Buy');
INSERT INTO Target (periodId, date, institution, price, rating, overview, takeaway1) VALUES (2616, '2026-08-05', 'Morgan Stanley', '465', 'Equal-Weight', 'The target reflects a more cautious accelerator adoption scenario.', 'Execution against the product roadmap remains the key variable.');
INSERT INTO Target (periodId, date, institution, price, rating) VALUES (2616, '2026-08-25', 'Raymond James', '641', 'Strong Buy');

INSERT INTO Target (periodId, date, institution, price, overview, takeaway1) VALUES (2625, '2026-07-24', 'Baird', '125', 'Turnaround expectations remain sensitive to foundry execution.', 'Monitor capital intensity and external customer wins.');
INSERT INTO Target (periodId, date, institution, price, rating) VALUES (2625, '2026-07-24', 'Rosenblatt Securities', '80', 'Sell');
INSERT INTO Target (periodId, date, institution, price, rating) VALUES (2625, '2026-07-28', 'Rosenblatt', '65', 'Sell');
INSERT INTO Target (periodId, date, institution, price, rating, overview, takeaway1) VALUES (2625, '2026-08-12', 'Bank of America', '145', 'Buy', 'Product progress is offset by the cost of the manufacturing transition.', 'Free cash flow remains the critical checkpoint.');
INSERT INTO Target (periodId, date, institution, price, rating) VALUES (2625, '2026-08-17', 'UBS', '112', 'Neutral');

-- NTRD targets demonstrate low-price formatting across reported and unreported periods.
INSERT INTO Target (periodId, date, institution, price, rating, overview, takeaway1) VALUES (2501, '2026-02-12', 'Lakeview Research', '0.85', 'Hold', 'Retention remains stable while the company is still proving operating leverage.', 'Monitor customer acquisition costs.');
INSERT INTO Target (periodId, date, institution, price, rating) VALUES (2501, '2026-03-05', 'Pioneer Capital', '1.00', 'Neutral');
INSERT INTO Target (periodId, date, institution, price, rating, overview, takeaway1) VALUES (2501, '2026-04-02', 'Summit Analytics', '1.15', 'Buy', 'Recurring revenue supports a cautiously improving outlook.', 'Free cash flow is the next milestone.');
INSERT INTO Target (periodId, date, institution, price, rating) VALUES (2501, '2026-04-28', 'Harbor Securities', '1.35', 'Outperform');

INSERT INTO Target (periodId, date, institution, price, rating, overview, takeaway1) VALUES (2502, '2026-05-12', 'Lakeview Research', '6.40', 'Buy', 'Improving retention and cost discipline support a higher valuation range.', 'Confirm operating leverage in the next report.');
INSERT INTO Target (periodId, date, institution, price, rating) VALUES (2502, '2026-06-03', 'Pioneer Capital', '6.90', 'Overweight');
INSERT INTO Target (periodId, date, institution, price, rating, overview, takeaway1) VALUES (2502, '2026-07-08', 'Summit Analytics', '7.50', 'Outperform', 'Growth quality is improving as recurring revenue becomes more predictable.', 'Watch sales efficiency.');
INSERT INTO Target (periodId, date, institution, price, rating, overview, takeaway1, takeaway2) VALUES (2502, '2026-08-01', 'Harbor Securities', '8.20', 'Buy', 'The business is approaching sustainable free cash flow.', 'Retention remains the core indicator.', 'Valuation assumes continued execution.');
