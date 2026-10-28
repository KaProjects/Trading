import AppKit
import LocalAuthentication
import Observation
import os
import ServiceManagement
import TraderCore

/// App state: owns the service process, the event stream, the Touch ID lock and notifications.
@MainActor
@Observable
final class AppModel {
    let environment = AppEnvironment.selected
    private(set) var phase: ServicePhase = .starting
    private(set) var status: BrokerStatus?
    private(set) var locked = true
    private(set) var lastError: String?
    private(set) var startsAtLogin = SMAppService.mainApp.status == .enabled
    private(set) var slowResponses = false
    /// Developer menu: Finnhub outage simulated in the service (resets when the service restarts).
    private(set) var finnhubOfflineSimulated = false
    private(set) var autoApprove2FA = true
    private(set) var fakeAutoLogin = AppEnvironment.fakeAutoLogin
    private(set) var hasFinnhubKey = KeychainStore.hasFinnhubKey
    private(set) var priceFeed = AppEnvironment.fakePriceFeed
    /// Account data shown in the window: live while connected, else the last saved snapshot (greyed out).
    private(set) var account: AccountSnapshot?
    private(set) var accountIsLive = false
    /// Latest price per feed symbol (T4): loaded on refresh, then updated from `price` events.
    private(set) var quotes: [String: Quote] = [:]
    /// T8 planner rows: scratchpad, kept in memory only (D8.2).
    private(set) var planRows: [PlanRow] = []
    /// Main window tab (Overview / Planner / Execution). Debug + fake opens on the Planner.
    var mainSection: MainSection = .overview
    var planQuantityText: [UUID: String] = [:]
    /// Planned volume per row in thousands of base currency (D8.5); fills the quantity once a price is known.
    var planVolumeText: [UUID: String] = [:]
    /// Expected return per row, percent of cost (user's estimate).
    var planReturnText: [UUID: String] = [:]
    /// T12: expected returns prefilled from the ticker's position → the plan they came from (placed time).
    /// Shown as a note under the field until the value is changed.
    private(set) var planReturnPrefilled: [UUID: Date] = [:]
    @ObservationIgnored private var volumeWaitingForPrice: Set<UUID> = []
    @ObservationIgnored private var devPresetAdded = false
    /// Dev data switched on (Developer menu); read by the menu so its check marks follow.
    private(set) var devData = Set(AppEnvironment.DevData.allCases.filter(\.isOn))
    /// Open order ticket (T5), nil when closed.
    private(set) var ticket: OrderTicketModel?

    var presentation: StatusPresentation { .make(locked: locked, phase: phase, status: status) }

    @ObservationIgnored let api = ServiceAPI(socketPath: AppEnvironment.socketPath)
    /// T9: committed plan → buy orders review.
    let basket: PlanBasketModel
    /// T12: sell review in the Execution tab.
    let sellBasket: SellBasketModel
    /// Execution tab: tickers ticked for "Sell selected".
    var sellSelection: Set<Int> = []
    @ObservationIgnored private let appLock = AppLock()
    @ObservationIgnored private let notifier = Notifier()
    @ObservationIgnored private var process: ServiceProcess?
    @ObservationIgnored private var streamTask: Task<Void, Never>?
    @ObservationIgnored private var heartbeatTask: Task<Void, Never>?
    @ObservationIgnored private var refreshTask: Task<Void, Never>?
    /// Last known state per order, to notify only on real transitions (D6.2).
    @ObservationIgnored private var knownOrders: [Int: Order] = [:]
    @ObservationIgnored private var restarts: [Date] = []
    @ObservationIgnored private var quitting = false
    @ObservationIgnored private var plannedRestart = false
    @ObservationIgnored private let logger = Logger(subsystem: "org.kaleta.trader-terminal", category: "app")

    init() {
        basket = PlanBasketModel(api: api)
        sellBasket = SellBasketModel(api: api)
        appLock.onLock = { [weak self] in self?.didLock() }
    }

    // MARK: - Service lifecycle

    func start() {
        KeychainStore.removeLegacyFinnhubKey()
        UserDefaults.standard.removeObject(forKey: "fakePriceFeed")  // first version of the Developer switch
        UserDefaults.standard.removeObject(forKey: "devData.plannerPrefill")  // merged into devData.devPlans
        hasFinnhubKey = KeychainStore.hasFinnhubKey
        hasIbkrCredentials = KeychainStore.hasIbkrCredentials(environment: environment.rawValue)
        hasFlexCredentials = KeychainStore.hasFlexCredentials(environment: environment.rawValue)
        notifier.requestAuthorization()
        launchService()
    }

    func shutdown() {
        quitting = true
        streamTask?.cancel()
        heartbeatTask?.cancel()
        process?.stop(grace: 2)
        process?.waitUntilExit()
    }

    private func launchService() {
        guard AppEnvironment.available.contains(environment) else {
            phase = .failed("\(environment.label) environment is not available yet")
            return
        }
        guard let executable = AppEnvironment.serviceExecutable,
              FileManager.default.isExecutableFile(atPath: executable.path) else {
            phase = .failed("Service not installed — run `uv sync` in terminal/src/service")
            return
        }
        phase = .starting
        status = nil
        let service = ServiceProcess(
            executable: executable,
            arguments: ["--env", environment.rawValue, "--socket", AppEnvironment.socketPath, "--watch-stdin",
                        "--log-level", "warning", "--prices", environment == .fake ? priceFeed : "finnhub"]
                + devDataArguments,
            onExit: { [weak self] code in Task { @MainActor in self?.serviceExited(code: code) } })
        do {
            try service.start()
        } catch {
            phase = .failed("Could not start service: \(error.localizedDescription)")
            return
        }
        process = service
        Task { await waitUntilReady() }
    }

    private func waitUntilReady() async {
        let deadline = Date().addingTimeInterval(20)
        while Date() < deadline, !quitting {
            if let current = try? await api.status() {
                phase = .running
                apply(current)
                slowResponses = false
                finnhubOfflineSimulated = false
                if environment == .fake, let auto = try? await api.autoApprove2FA() { autoApprove2FA = auto }
                startStreams()
                await syncWatchlist()  // a restarted service has forgotten the planner tickers
                if !locked, await sendDataKey() { await refreshAccount() }  // service restarted while unlocked
                await autoLogInIfFake()
                return
            }
            try? await Task.sleep(for: .milliseconds(200))
        }
        if !quitting { phase = .failed("Service did not respond") }
    }

    private func serviceExited(code: Int32) {
        streamTask?.cancel()
        heartbeatTask?.cancel()
        process = nil
        status = nil
        guard !quitting else { return }
        if plannedRestart {
            plannedRestart = false
            launchService()
            return
        }
        logger.error("service exited with code \(code)")
        restarts = restarts.filter { $0 > Date().addingTimeInterval(-60) } + [Date()]
        guard restarts.count <= 3 else {
            phase = .failed("Service keeps stopping (exit code \(code))")
            return
        }
        phase = .failed("Service stopped (exit code \(code)) — restarting…")
        Task {
            try? await Task.sleep(for: .seconds(2))
            if !quitting { launchService() }
        }
    }

    /// Event stream (pushes) + heartbeat (detects a hung service; the stream alone can't).
    private func startStreams() {
        streamTask?.cancel()
        streamTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                do {
                    let socket = try await api.events()
                    defer { socket.close() }
                    for try await message in socket.messages {
                        handle(message)
                    }
                } catch {
                    logger.info("event stream ended: \(error.localizedDescription)")
                }
                try? await Task.sleep(for: .seconds(1))
            }
        }
        heartbeatTask?.cancel()
        heartbeatTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(10))
                guard let self, phase == .running else { continue }
                do { apply(try await api.status()) } catch { lastError = "Service not responding" }
            }
        }
    }

    private func handle(_ message: Data) {
        guard let event = try? ServiceEvent.parse(message) else { return }
        switch event.type {
        case "status":
            guard let update = try? ServiceJSON.decoder.decode(BrokerStatus.self, from: event.data) else { return }
            apply(update)
            scheduleAccountRefresh()
        case "order":
            if let order = try? ServiceJSON.decoder.decode(Order.self, from: event.data) {
                if let notification = OrderNotification.forUpdate(previous: knownOrders[order.orderId], current: order) {
                    notifier.post(notification, for: order)
                }
                knownOrders[order.orderId] = order
                basket.orderUpdated(order)
                sellBasket.orderUpdated(order)
            }
            scheduleAccountRefresh()
        case "feed":
            let object = (try? JSONSerialization.jsonObject(with: event.data)) as? [String: Any]
            pricesOnline = object?["online"] as? Bool ?? true
        case "account", "position", "fill":
            scheduleAccountRefresh()
        case "price":
            if let quote = try? ServiceJSON.decoder.decode(Quote.self, from: event.data), !locked {
                quotes[quote.symbol] = quote
                if let ticket, ticket.contract?.feedSymbol == quote.symbol { ticket.priceUpdated(quote) }
                sellBasket.priceUpdated(quote)
                for row in planRows where volumeWaitingForPrice.contains(row.id) && row.contract.feedSymbol == quote.symbol {
                    applyVolume(row.id)
                }
            }
        default:
            break
        }
    }

    // MARK: - Account data (T3)

    /// Coalesces bursts of events (a fill emits order + fill + position + account) into one reload.
    private func scheduleAccountRefresh() {
        refreshTask?.cancel()
        refreshTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(250))
            guard !Task.isCancelled else { return }
            await self?.refreshAccount()
        }
    }

    /// False while Finnhub can't be reached: an offline icon next to Lock (nothing shown while it works).
    private(set) var pricesOnline = true

    func refreshAccount() async {
        guard !locked, phase == .running else { return }
        if let online = try? await api.pricesOnline() { pricesOnline = online }
        if status?.state == .connected, let live = try? await api.liveSnapshot() {
            account = live
            accountIsLive = true
            if let current = try? await api.prices() {
                quotes = Dictionary(current.map { ($0.symbol, $0) }, uniquingKeysWith: { $1 })
            }
            return
        }
        accountIsLive = false
        if let saved = try? await api.savedSnapshot() { account = saved }
    }

    /// D3.3: after Touch ID, read the data key with the same LAContext (no second prompt) and hand it to
    /// the service over its private stdin pipe. Waits until the service has applied it.
    private func sendDataKey() async -> Bool {
        guard let context = appLock.context, let process else { return false }
        do {
            // Fake data is not sensitive: the fake environment uses a key without Touch ID, so auto log-in works
            // and switching the toggle never strands files. Paper and production always need Touch ID.
            defer { sendFinnhubKey() }
            let key = environment == .fake
                ? try KeychainStore.dataKey(environment: environment.rawValue, context: nil, userPresence: false)
                : try KeychainStore.dataKey(environment: environment.rawValue, context: context)
            try process.send(.unlock(dataKey: key))
            if let flex = try? KeychainStore.flexCredentials(environment: environment.rawValue, context: context) {
                try process.send(.flex(token: flex.token, queryId: flex.queryId))
            }
        } catch {
            lastError = "Could not unlock saved data: \(error.localizedDescription)"
            return false
        }
        for _ in 0..<40 {
            if (try? await api.vaultUnlocked()) == true { return true }
            try? await Task.sleep(for: .milliseconds(50))
        }
        lastError = "Service did not accept the data key"
        return false
    }

    /// T4: the Finnhub key goes to the service over the private pipe, like the data key — whenever the service
    /// uses Finnhub (paper/production, or the fake environment with real prices).
    private var usesFinnhub: Bool { environment != .fake || priceFeed == "finnhub" }

    private func sendFinnhubKey() {
        guard usesFinnhub, let process else { return }
        do {
            if let key = try KeychainStore.finnhubKey() { try process.send(.finnhubKey(key)) }
        } catch {
            lastError = "Could not read the Finnhub key: \(error.localizedDescription)"
        }
    }

    func saveFinnhubKey(_ key: String) throws {
        try KeychainStore.saveFinnhubKey(key)
        hasFinnhubKey = true
        if usesFinnhub { try? process?.send(.finnhubKey(key)) }
    }

    func deleteFinnhubKey() {
        KeychainStore.deleteFinnhubKey()
        hasFinnhubKey = false
    }

    /// Developer menu (fake only): real Finnhub ↔ simulated prices. Restarts the service.
    func setPriceFeed(_ feed: String) {
        guard environment == .fake, feed != priceFeed else { return }
        priceFeed = feed
        AppEnvironment.fakePriceFeed = feed
        quotes = [:]
        plannedRestart = true
        process?.stop(grace: 2)
    }

    // MARK: - Execution (T10, T12)

    /// Positions per ticker with live numbers (T12); loaded while the Execution or History tab is open.
    private(set) var execution: ExecutionReport?

    /// T12: open the sell review for these tickers (nil = every position), all active shares each.
    func openSell(_ conids: Set<Int>?) {
        guard let report = execution, let positions = account?.positions else { return }
        let items: [(Contract, Decimal)] = report.positions.compactMap { row in
            guard conids?.contains(row.conid) ?? true, row.held.value > 0,
                  let contract = positions.first(where: { $0.contract.conid == row.conid })?.contract else { return nil }
            return (contract, row.held.value)
        }
        sellBasket.open(items, quotes: quotes)
        sellSelection = []
    }

    func placeSells() async {
        await sellBasket.place(allowWhenClosed: basket.allowPlacingWhenClosed)
        scheduleAccountRefresh()
        await refreshExecution()
    }

    func refreshExecution() async {
        guard !locked, phase == .running else { return }
        if let report = try? await api.execution() { execution = report }
    }

    /// Execution / History tabs: refresh while open (prices move, orders fill).
    func keepRefreshingExecution() async {
        while !Task.isCancelled {
            await refreshExecution()
            try? await Task.sleep(for: .seconds(3))
        }
    }

    // MARK: - Planner (T8)

    /// Plan rows as shown: once a row's order is filled it is frozen on the execution (fill price and filled
    /// shares), so its price, cost and expected profit stop following the market.
    var effectivePlanRows: [PlanRow] {
        planRows.map { row in
            guard let line = basket.lines.first(where: { $0.id == row.id }), line.isFilled,
                  let order = line.order, let fill = order.avgFillPrice else { return row }
            var frozen = row
            frozen.quantity = order.filled.value
            frozen.fixedPrice = fill.value
            return frozen
        }
    }

    /// The plan as the Planner shows it (nil without account data).
    var planSummary: PlannerSummary? {
        account.map { PlannerSummary.make(account: $0.account, positions: $0.positions, orders: $0.orders,
                                          quotes: quotes, rows: effectivePlanRows) }
    }

    func commitPlan() async {
        guard let plan = planSummary else { return }
        await basket.commit(plan)
    }

    func placeBasket() async {
        guard let plan = planSummary, let account = account?.account else { return }
        if basket.stage == .committed {
            // Record the plan first: it carries the tickers' expected returns (T12). If it can't be saved,
            // nothing is placed.
            let returns = Dictionary(uniqueKeysWithValues: planRows.compactMap { row in
                planReturnPercent(row.id).map { (row.id, $0) }
            })
            do {
                try await api.savePlan(basket.planRecord(expectedReturns: returns))
            } catch {
                lastError = "Could not save the plan — nothing was placed"
                return
            }
        }
        await basket.place(account: account, available: plan.available)
        scheduleAccountRefresh()
    }

    /// Finish: clear the review and the plan, then show the positions in the Execution tab.
    func resetPlan() {
        basket.reset()
        guard !basket.isFrozen else { return }
        mainSection = .execution
        planRows = []
        planQuantityText = [:]
        planVolumeText = [:]
        planReturnText = [:]
        planReturnPrefilled = [:]
        volumeWaitingForPrice = []
        Task { await syncWatchlist() }
    }

    func addPlanRow(_ contract: Contract) {
        guard !basket.isFrozen else { return }
        let row = PlanRow(contract: contract)
        planRows.append(row)
        Task {
            await syncWatchlist()
            await prefillPlanReturn(row.id, conid: contract.conid)
        }
    }

    /// T12: a ticker you hold with an expected return gets it prefilled (only into an empty field).
    private func prefillPlanReturn(_ id: UUID, conid: Int) async {
        if execution == nil { await refreshExecution() }
        guard let position = execution?.positions.first(where: { $0.conid == conid }),
              let percent = position.expectedReturnPct, let from = position.expectedFrom,
              planRows.contains(where: { $0.id == id }), (planReturnText[id] ?? "").isEmpty else { return }
        planReturnText[id] = percent.value.formatted(.number.precision(.fractionLength(0...2)).grouping(.never))
        planReturnPrefilled[id] = from
    }

    func removePlanRow(_ id: UUID) {
        guard !basket.isFrozen else { return }
        planRows.removeAll { $0.id == id }
        planQuantityText[id] = nil
        planVolumeText[id] = nil
        planReturnText[id] = nil
        planReturnPrefilled[id] = nil
        volumeWaitingForPrice.remove(id)
        Task { await syncWatchlist() }
    }

    /// D8.5: "30" = 30 000 in base currency → whole shares at the live price, rounded up, written into the
    /// (still editable) quantity. Computed once per volume entry; later price moves only change the cost.
    func setPlanReturn(_ id: UUID, text: String) {
        guard !basket.isFrozen else { return }
        if planReturnText[id, default: ""] != text { planReturnPrefilled[id] = nil }
        planReturnText[id] = text
    }

    func planReturnPercent(_ id: UUID) -> Decimal? {
        DecimalInput.parse(planReturnText[id] ?? "").flatMap { $0 > 0 ? $0 : nil }
    }

    func setPlanVolume(_ id: UUID, text: String) {
        guard !basket.isFrozen else { return }
        // The field re-submits its unchanged text on focus/commit: only a real edit recalculates the quantity.
        guard planVolumeText[id, default: ""] != text else { return }
        planVolumeText[id] = text
        volumeWaitingForPrice.remove(id)
        applyVolume(id)  // an empty (or 0) volume never touches the quantity
    }

    private func applyVolume(_ id: UUID) {
        guard let row = planRows.first(where: { $0.id == id }),
              let thousands = DecimalInput.parse(planVolumeText[id] ?? ""), thousands > 0 else { return }
        guard let quote = quotes[row.contract.feedSymbol], let account = account?.account,
              let rate = account.rate(row.contract.currency),
              let shares = PlanVolume.quantity(thousands: thousands, price: quote.last.value, fxToBase: rate) else {
            volumeWaitingForPrice.insert(id)  // fill in as soon as a price arrives
            return
        }
        volumeWaitingForPrice.remove(id)
        writeQuantity(id, text: shares.formatted(.number.precision(.fractionLength(0)).grouping(.never)))
    }

    /// Typed by hand: clears the volume (it no longer applies) and wins over a volume still waiting for a price.
    func setPlanQuantity(_ id: UUID, text: String) {
        guard !basket.isFrozen else { return }
        guard planQuantityText[id, default: ""] != text else { return }
        volumeWaitingForPrice.remove(id)
        planVolumeText[id] = nil  // a hand-edited quantity replaces the volume: clear it
        writeQuantity(id, text: text)
    }

    private func writeQuantity(_ id: UUID, text: String) {
        planQuantityText[id] = text
        guard let index = planRows.firstIndex(where: { $0.id == id }) else { return }
        planRows[index].quantity = DecimalInput.parse(text).flatMap { $0 > 0 ? $0 : nil }
    }

    func clearPlan() {
        guard !basket.isFrozen else { return }
        planRows = []
        planQuantityText = [:]
        planVolumeText = [:]
        planReturnText = [:]
        planReturnPrefilled = [:]
        volumeWaitingForPrice = []
        Task { await syncWatchlist() }
    }

    /// Planner tickers get live prices like positions.
    private func syncWatchlist() async {
        guard phase == .running else { return }
        let ticketConid = ticket?.contract.map(\.conid)
        let conids = Array(Set(planRows.map(\.contract.conid) + (ticketConid.map { [$0] } ?? []))).sorted()
        do {
            try await api.setWatchlist(conids)
            if let current = try? await api.prices() {
                for quote in current { quotes[quote.symbol] = quote }
            }
        } catch {
            lastError = "Could not stream planner prices"
        }
    }

    private func didLock() {
        locked = true
        ticket = nil
        account = nil
        quotes = [:]
        execution = nil
        accountIsLive = false
        refreshTask?.cancel()
        try? process?.send(.lock)
    }

    private func apply(_ update: BrokerStatus) {
        let previous = status?.state
        status = update
        if update.state == .connected { Task { await addDevPlannerPreset() } }  // ticker lookup needs a session
        if update.state == .connected, previous != .connected { scheduleAccountRefresh() }  // also without an event
        lastError = nil
        if let notification = StatusNotification.forTransition(from: previous, to: update.state), previous != nil {
            notifier.post(notification)
        }
    }

    // MARK: - User actions

    @discardableResult
    func unlock() async -> Bool {
        let ok: Bool
        if environment == .fake && fakeAutoLogin {
            appLock.unlockWithoutAuthentication()
            ok = true
        } else {
            ok = await appLock.unlock()
        }
        if ok {
            locked = false
            if phase == .running, let current = try? await api.status() { apply(current) }
            if phase == .running, await sendDataKey() { await refreshAccount() }
        }
        return ok
    }

    /// Debug builds, fake environment: unlock without Touch ID and log in to the fake broker (no clicks).
    private func autoLogInIfFake() async {
        guard environment == .fake, fakeAutoLogin else { return }
        if locked { await unlock() }
        if let state = status?.state, [.disconnected, .loginRequired, .loginFailed].contains(state) {
            await logIn()
        }
    }

    /// Debug + fake: the Planner opens with the preset rows (SNDK, MU at 30k) once per launch, if the plan is empty.
    private func addDevPlannerPreset() async {
        guard AppEnvironment.isDebugBuild, environment == .fake, AppEnvironment.DevData.devPlans.isOn,
              planRows.isEmpty, !devPresetAdded,
              status?.state == .connected else { return }
        devPresetAdded = true
        for item in AppEnvironment.devPlannerPreset {
            guard let contract = try? await api.contracts(symbol: item.symbol).first else { continue }
            addPlanRow(contract)
            if let id = planRows.last?.id {
                setPlanVolume(id, text: item.volume)
                setPlanReturn(id, text: item.expectedReturn)
            }
        }
    }

    /// Dev data the fake service starts with (Developer menu); never for paper or production.
    private var devDataArguments: [String] {
        // Debug + paper: quitting keeps IB Gateway logged in; the next start connects to it (no new 2FA).
        if AppEnvironment.isDebugBuild && environment == .paper { return ["--keep-gateway"] }
        guard AppEnvironment.isDebugBuild, environment == .fake else { return [] }
        return (AppEnvironment.DevData.devPlans.isOn ? ["--dev-plan"] : [])
            + (AppEnvironment.DevData.seedTransactions.isOn ? [] : ["--no-seed-transactions"])
    }

    /// Developer menu: switch a piece of dev data. Restarts the service, so the fake account starts fresh with
    /// only what is switched on; nothing from before is shown meanwhile.
    func toggleDevData(_ item: AppEnvironment.DevData) {
        guard environment == .fake else { return }
        item.isOn.toggle()
        if item.isOn { devData.insert(item) } else { devData.remove(item) }
        if item == .devPlans {  // the Planner prefill goes (or comes back on connect) with the dev plans
            basket.replan()
            clearPlan()
            devPresetAdded = false
        }
        account = nil
        accountIsLive = false
        knownOrders = [:]
        execution = nil
        plannedRestart = true
        process?.stop(grace: 2)
    }

    func toggleAllowPlacingWhenClosed() {
        AppEnvironment.devAllowPlacingWhenClosed = !basket.allowPlacingWhenClosed
        basket.allowPlacingWhenClosed = AppEnvironment.devAllowPlacingWhenClosed
    }

    func toggleFakeAutoLogin() {
        fakeAutoLogin.toggle()
        AppEnvironment.fakeAutoLogin = fakeAutoLogin
        if fakeAutoLogin { Task { await autoLogInIfFake() } }
    }

    func logIn() async {
        if locked, !(await unlock()) { return }
        if environment != .fake, !sendIbkrCredentials() { return }
        await perform { try await self.apply(self.api.login()) }
    }

    /// T1: credentials from the Keychain (with the unlock's Touch ID context) to the service, right before Log in.
    private func sendIbkrCredentials() -> Bool {
        guard let process, let context = appLock.context else { return false }
        do {
            guard let login = try KeychainStore.ibkrCredentials(environment: environment.rawValue, context: context)
            else {
                lastError = "Add your IBKR username and password in Settings"
                return false
            }
            try process.send(.ibkrCredentials(username: login.username, password: login.password))
            return true
        } catch {
            lastError = "Could not read the IBKR login: \(error.localizedDescription)"
            return false
        }
    }

    private(set) var hasIbkrCredentials = false
    private(set) var hasFlexCredentials = false

    /// T11: saved and handed to the running service right away (the app is unlocked when Settings saves).
    func saveFlexCredentials(token: String, queryId: String) throws {
        try KeychainStore.saveFlexCredentials(environment: environment.rawValue, token: token, queryId: queryId)
        hasFlexCredentials = true
        try? process?.send(.flex(token: token, queryId: queryId))
    }

    func deleteFlexCredentials() {
        KeychainStore.deleteFlexCredentials(environment: environment.rawValue)
        hasFlexCredentials = false
    }

    func saveIbkrCredentials(username: String, password: String) throws {
        try KeychainStore.saveIbkrCredentials(environment: environment.rawValue, username: username,
                                              password: password)
        hasIbkrCredentials = true
    }

    func deleteIbkrCredentials() {
        KeychainStore.deleteIbkrCredentials(environment: environment.rawValue)
        hasIbkrCredentials = false
    }

    /// Developer menu (Debug): fake ↔ paper. The environment is fixed per run, so the app relaunches.
    func switchEnvironment(_ target: BrokerEnvironment) {
        guard AppEnvironment.isDebugBuild, target != environment, AppEnvironment.available.contains(target) else { return }
        AppEnvironment.select(target)
        let relaunch = Process()
        relaunch.executableURL = URL(fileURLWithPath: "/bin/sh")
        relaunch.arguments = ["-c", "while kill -0 \(ProcessInfo.processInfo.processIdentifier) 2>/dev/null; do sleep 0.2; done; open \"$0\"",
                              Bundle.main.bundlePath]
        try? relaunch.run()
        NSApp.terminate(nil)
    }

    func stop() async {
        await perform { try await self.apply(self.api.logout()) }
    }

    var canTrade: Bool { !locked && phase == .running && status?.state == .connected }

    func openTicket(contract: Contract? = nil, side: OrderSide = .buy) {
        guard canTrade else { return }
        show(OrderTicketModel(api: api, contract: contract, side: side))
    }

    /// Buy helper: side fixed, volume → shares, limit 2 % above the live price.
    func openBuy() {
        guard canTrade else { return }
        show(OrderTicketModel(api: api, helper: .buy, rate: { [weak self] in self?.account?.account.rate($0) }))
    }

    func openQuickSell(_ position: Position) {
        guard canTrade else { return }
        show(OrderTicketModel(api: api, quickSell: position))
    }

    func openModify(_ order: Order) {
        guard canTrade else { return }
        show(OrderTicketModel(api: api, modifying: order))
    }

    /// The ticket's contract is streamed like planner rows (live price, helpers follow it); once sent, the ticket
    /// closes and the Overview shows the order.
    private func show(_ newTicket: OrderTicketModel) {
        newTicket.contractChanged = { [weak self] _ in Task { await self?.syncWatchlist() } }
        newTicket.onSent = { [weak self] order in
            guard let self else { return }
            closeTicket()
            mainSection = .overview
            overviewTab = order.status == "Filled" ? .fills : .orders
        }
        ticket = newTicket
    }

    /// Overview's sub-tab (positions / open orders / fills); set after sending an order.
    var overviewTab: AccountView.Tab = .positions

    func cancel(_ order: Order) async {
        await perform { _ = try await self.api.cancelOrder(order.orderId) }
        scheduleAccountRefresh()
    }

    func cancelAll() async {
        await perform { _ = try await self.api.cancelAllOrders() }
        scheduleAccountRefresh()
    }

    func closeTicket() {
        ticket = nil
        scheduleAccountRefresh()
        Task { await syncWatchlist() }
    }

    func lockNow() {
        appLock.lock()
    }

    /// D2.9: closing the window locks the app like a screen lock; the broker session stays up.
    /// Reopening needs Touch ID only. A full logout is Stop or Quit.
    func windowClosed() {
        appLock.lock()
    }

    func setStartsAtLogin(_ enabled: Bool) {
        do {
            if enabled { try SMAppService.mainApp.register() } else { try SMAppService.mainApp.unregister() }
        } catch {
            lastError = "Start at login: \(error.localizedDescription)"
        }
        startsAtLogin = SMAppService.mainApp.status == .enabled
    }

    // MARK: - Developer menu (Debug builds, fake environment only)

    func dev(_ action: DevAction) async {
        await perform { try await self.api.dev(action) }
    }

    func toggleAutoApprove2FA() async {
        let enable = !autoApprove2FA
        await perform {
            try await self.api.setAutoApprove2FA(enable)
            self.autoApprove2FA = enable
        }
    }

    var canSimulateFinnhubOffline: Bool { AppEnvironment.isDebugBuild && phase == .running && usesFinnhub }

    func toggleFinnhubOfflineSimulated() async {
        let enable = !finnhubOfflineSimulated
        await perform {
            try await self.api.setFinnhubOfflineSimulated(enable)
            self.finnhubOfflineSimulated = enable
        }
    }

    func toggleSlowResponses() async {
        let enable = !slowResponses
        await perform {
            try await self.api.setSlowResponses(seconds: enable ? 2 : 0)
            self.slowResponses = enable
        }
    }

    private func perform(_ work: @escaping @MainActor () async throws -> Void) async {
        do {
            try await work()
            lastError = nil
        } catch ServiceError.http(_, _, let message) {
            lastError = message ?? "Request failed"
        } catch {
            lastError = error.localizedDescription
        }
    }
}

enum MainSection: Hashable { case overview, planner, execution, history }
